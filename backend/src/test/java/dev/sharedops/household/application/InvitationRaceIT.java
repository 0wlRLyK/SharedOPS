package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvitationRaceIT {
  private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
  @Autowired InvitationService invitations;
  @Autowired CreateHouseholdService creation;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @MockitoBean Clock clock;
  @MockitoSpyBean InvitationStore store;
  @MockitoSpyBean MembershipStore memberships;
  private final AtomicReference<Instant> now = new AtomicReference<>();
  private final List<UUID> accounts = new ArrayList<>();
  private final List<UUID> households = new ArrayList<>();
  private final String raceName = "race-" + UUID.randomUUID();
  private UUID owner;
  private UUID home;

  @BeforeEach
  void setup() {
    now.set(START);
    when(clock.instant()).thenAnswer(inv -> now.get());
    owner = account();
    home = UUID.randomUUID();
    households.add(home);
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        home);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
        UUID.randomUUID(),
        owner,
        home);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    reset(store, memberships, clock);
    for (UUID account : accounts) {
      for (UUID id :
          jdbc.queryForList(
              "SELECT household_id FROM household_memberships WHERE account_id = ?",
              UUID.class,
              account)) {
        if (!households.contains(id)) {
          households.add(id);
        }
      }
    }
    for (UUID id :
        jdbc.queryForList("SELECT id FROM households WHERE name = ?", UUID.class, raceName)) {
      if (!households.contains(id)) {
        households.add(id);
      }
    }
    for (UUID id : households) {
      jdbc.update("DELETE FROM household_invitations WHERE household_id = ?", id);
      jdbc.update("DELETE FROM household_memberships WHERE household_id = ?", id);
      jdbc.update("DELETE FROM households WHERE id = ?", id);
    }
    for (UUID id : accounts) {
      jdbc.update("DELETE FROM accounts WHERE id = ?", id);
    }
  }

  @Test
  void distinctInvitationsRacingForLastSlotAdmitOnlyOne() throws Exception {
    UUID first = account(), second = account();
    IssuedInvitation a = issue(), b = issue();
    coordinateHouseholdLockAttempts();
    var results = race(() -> accept(first, a.token()), () -> accept(second, b.token()));
    assertThat(results).containsExactlyInAnyOrder("accepted", "rejected");
    assertThat(activeMembers(home)).isEqualTo(2);
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE household_id = ? AND consumed_at IS NOT NULL",
                home))
        .isEqualTo(1);
    UUID loserInvitation = results.get(0).equals("rejected") ? a.id() : b.id();
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL",
                loserInvitation))
        .isEqualTo(1);
  }

  @Test
  void sameInvitationHasAtMostOneSuccessfulConsumer() throws Exception {
    UUID first = account(), second = account();
    IssuedInvitation invitation = issue();
    coordinateHouseholdLockAttempts();
    assertThat(
            race(() -> accept(first, invitation.token()), () -> accept(second, invitation.token())))
        .containsExactlyInAnyOrder("accepted", "rejected");
    assertThat(activeMembers(home)).isEqualTo(2);
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE id = ? AND consumed_at IS NOT NULL AND revoked_at IS NULL",
                invitation.id()))
        .isEqualTo(1);
  }

  @Test
  void revokeAndAcceptSerializeToExactlyOneTerminalState() throws Exception {
    UUID guest = account();
    IssuedInvitation invitation = issue();
    coordinateHouseholdLockAttempts();
    List<String> results =
        race(() -> accept(guest, invitation.token()), () -> revoke(invitation.id()));
    assertThat(results).isIn(List.of("accepted", "rejected"), List.of("rejected", "revoked"));
    boolean accepted = results.get(0).equals("accepted");
    assertThat(activeMembers(home)).isEqualTo(accepted ? 2 : 1);
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE id = ? AND consumed_at IS NOT NULL AND revoked_at IS NULL",
                invitation.id()))
        .isEqualTo(accepted ? 1 : 0);
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE id = ? AND revoked_at IS NOT NULL AND consumed_at IS NULL",
                invitation.id()))
        .isEqualTo(accepted ? 0 : 1);
  }

  @Test
  void creationAndAcceptanceForSameAccountCommitOnlyOneMembershipWithoutOrphan() throws Exception {
    UUID guest = account();
    IssuedInvitation invitation = issue();
    long before = jdbc.queryForObject("SELECT count(*) FROM households", Long.class);
    CountDownLatch reads = new CountDownLatch(2), release = new CountDownLatch(1);
    doAnswer(
            inv -> {
              Object result = inv.callRealMethod();
              if (guest.equals(inv.getArgument(0))) {
                reads.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("membership race timeout");
                }
              }
              return result;
            })
        .when(memberships)
        .findActiveHouseholdId(guest);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Future<String> join = pool.submit(() -> accept(guest, invitation.token()));
      Future<String> create = pool.submit(() -> create(guest));
      try {
        assertThat(reads.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }
      List<String> outcomes =
          List.of(join.get(20, TimeUnit.SECONDS), create.get(20, TimeUnit.SECONDS));
      assertThat(outcomes).isIn(List.of("accepted", "rejected"), List.of("rejected", "created"));
      boolean joined = outcomes.get(0).equals("accepted");
      assertThat(
              count(
                  "SELECT count(*) FROM household_memberships WHERE account_id = ? AND active",
                  guest))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM households", Long.class))
          .isEqualTo(before + (joined ? 0 : 1));
      assertThat(
              count(
                  "SELECT count(*) FROM household_invitations WHERE id = ? AND consumed_at IS NOT NULL",
                  invitation.id()))
          .isEqualTo(joined ? 1 : 0);
    } finally {
      release.countDown();
    }
  }

  @Test
  void expirationWhileWaitingForHouseholdLockRejectsWithoutWrites() throws Exception {
    UUID guest = account();
    IssuedInvitation invitation = issue();
    CountDownLatch attempted = new CountDownLatch(1);
    doAnswer(
            inv -> {
              attempted.countDown();
              return inv.callRealMethod();
            })
        .when(store)
        .lockHousehold(home);
    try (Connection lock = dataSource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      lock.setAutoCommit(false);
      try (var statement =
          lock.prepareStatement("SELECT id FROM households WHERE id = ? FOR UPDATE")) {
        statement.setObject(1, home);
        statement.executeQuery().close();
      }
      Future<String> joining = pool.submit(() -> accept(guest, invitation.token()));
      try {
        assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(joining.isDone()).isFalse();
        now.set(START.plusSeconds(24 * 60 * 60));
      } finally {
        lock.commit();
      }
      assertThat(joining.get(20, TimeUnit.SECONDS)).isEqualTo("rejected");
    }
    assertThat(activeMembers(home)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM household_memberships WHERE account_id = ? AND active",
                guest))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL",
                invitation.id()))
        .isEqualTo(1);
  }

  @Test
  void expirationWhileWaitingForAccountUniquenessRollsBackAcceptance() throws Exception {
    UUID guest = account();
    IssuedInvitation invitation = issue();
    UUID otherHome = UUID.randomUUID();
    households.add(otherHome);
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Other', 'EUR', 'Europe/Berlin')",
        otherHome);

    try (Connection blocker = dataSource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      blocker.setAutoCommit(false);
      int blockerPid;
      try (var statement = blocker.createStatement();
          var result = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertThat(result.next()).isTrue();
        blockerPid = result.getInt(1);
      }
      try (var statement =
          blocker.prepareStatement(
              "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)")) {
        statement.setObject(1, UUID.randomUUID());
        statement.setObject(2, guest);
        statement.setObject(3, otherHome);
        assertThat(statement.executeUpdate()).isEqualTo(1);
      }

      Future<String> joining = pool.submit(() -> accept(guest, invitation.token()));
      try {
        assertThat(awaitMembershipInsertBlockedBy(blockerPid)).isTrue();
        assertThat(joining.isDone()).isFalse();
        now.set(invitation.expiresAt());
      } finally {
        blocker.rollback();
      }
      assertThat(joining.get(20, TimeUnit.SECONDS)).isEqualTo("rejected");
    }
    assertThat(activeMembers(home)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM household_memberships WHERE account_id = ? AND active",
                guest))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM household_invitations WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL",
                invitation.id()))
        .isEqualTo(1);
  }

  private boolean awaitMembershipInsertBlockedBy(int blockerPid) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    do {
      Boolean blocked =
          jdbc.queryForObject(
              "SELECT EXISTS (SELECT 1 FROM pg_stat_activity "
                  + "WHERE ? = ANY(pg_blocking_pids(pid)) AND wait_event_type = 'Lock' "
                  + "AND query LIKE 'INSERT INTO household_memberships%')",
              Boolean.class, blockerPid);
      if (Boolean.TRUE.equals(blocked)) {
        return true;
      }
      Thread.yield();
    } while (System.nanoTime() < deadline);
    return false;
  }

  private void coordinateHouseholdLockAttempts() {
    CountDownLatch entered = new CountDownLatch(2);
    doAnswer(
            inv -> {
              entered.countDown();
              if (!entered.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("lock race timeout");
              }
              return inv.callRealMethod();
            })
        .when(store)
        .lockHousehold(home);
  }

  private UUID account() {
    UUID id = UUID.randomUUID();
    accounts.add(id);
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        id,
        id + "@example.com");
    return id;
  }

  private IssuedInvitation issue() {
    authenticate(owner);
    try {
      return invitations.create(home);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String accept(UUID account, String token) {
    authenticate(account);
    try {
      invitations.accept(token);
      return "accepted";
    } catch (InvitationConflictException
        | InvalidInvitationException
        | ActiveHouseholdExistsException e) {
      return "rejected";
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String create(UUID account) {
    authenticate(account);
    try {
      creation.create(new HouseholdDraft(raceName, null, "EUR", "Europe/Berlin"));
      return "created";
    } catch (InvitationConflictException | ActiveHouseholdExistsException e) {
      return "rejected";
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String revoke(UUID invitation) {
    authenticate(owner);
    try {
      invitations.revoke(home, invitation);
      return "revoked";
    } catch (InvitationConflictException e) {
      return "rejected";
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static void authenticate(UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  private int activeMembers(UUID household) {
    return count(
        "SELECT count(*) FROM household_memberships WHERE household_id = ? AND active", household);
  }

  private int count(String sql, UUID id) {
    return jdbc.queryForObject(sql, Integer.class, id);
  }

  private static List<String> race(
      java.util.concurrent.Callable<String> first, java.util.concurrent.Callable<String> second)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Future<String> a =
          pool.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("start timeout");
                }
                return first.call();
              });
      Future<String> b =
          pool.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("start timeout");
                }
                return second.call();
              });
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }
      return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    } finally {
      start.countDown();
    }
  }
}
