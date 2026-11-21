package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CreateHouseholdIT {
  @Autowired ApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreateHouseholdService service;
  @MockitoSpyBean MembershipStore memberships;
  @MockitoSpyBean HouseholdStore households;
  UUID actor;

  @BeforeEach
  void setup() {
    actor = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, ?)",
        actor,
        actor + "@example.com",
        "hash");
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    reset(memberships, households);
    var ids =
        jdbc.queryForList(
            "SELECT household_id FROM household_memberships WHERE account_id = ?",
            UUID.class,
            actor);
    jdbc.update("DELETE FROM household_memberships WHERE account_id = ?", actor);
    for (UUID id : ids) {
      jdbc.update("DELETE FROM households WHERE id = ?", id);
    }
    jdbc.update("DELETE FROM accounts WHERE id = ?", actor);
  }

  @Test
  void createsHouseholdAndMembershipAsOneCommittedOperation() {
    assertThatCode(
            () -> {
              Class<?> type =
                  Class.forName("dev.sharedops.household.application.CreateHouseholdService");
              Object service = context.getBean(type);
              var result =
                  (HouseholdSnapshot)
                      type.getMethod("create", HouseholdDraft.class)
                          .invoke(
                              service, new HouseholdDraft("Home", null, "EUR", "Europe/Berlin"));
              assertThat(
                      jdbc.queryForObject(
                          "SELECT count(*) FROM households WHERE id = ?",
                          Integer.class,
                          result.id()))
                  .isEqualTo(1);
              assertThat(
                      jdbc.queryForObject(
                          "SELECT count(*) FROM household_memberships WHERE household_id = ? AND account_id = ? AND active",
                          Integer.class,
                          result.id(),
                          actor))
                  .isEqualTo(1);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void duplicateAndMissingIdentityAreRejectedWithoutWrites() {
    service.create(new HouseholdDraft("One", null, "EUR", "Europe/Berlin"));
    var before = jdbc.queryForObject("SELECT count(*) FROM households", Long.class);
    assertThatThrownBy(
            () -> service.create(new HouseholdDraft("Two", null, "EUR", "Europe/Berlin")))
        .isInstanceOf(ActiveHouseholdExistsException.class);
    SecurityContextHolder.clearContext();
    clearInvocations(memberships, households);
    assertThatThrownBy(
            () -> service.create(new HouseholdDraft("Three", null, "EUR", "Europe/Berlin")))
        .isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    verifyNoInteractions(memberships, households);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM households", Long.class))
        .isEqualTo(before);
  }

  @Test
  void invalidCommandLeavesNoCommittedHouseholdOrMembership() {
    var beforeHomes = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    var beforeMemberships = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    assertThatThrownBy(
            () -> service.create(new HouseholdDraft("   ", null, "EUR", "Europe/Berlin")))
        .isInstanceOf(InvalidHouseholdException.class);
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id")).isEqualTo(beforeHomes);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(beforeMemberships);
  }

  @Test
  void unrelatedIntegrityViolationIsNotReportedAsMembershipConflict() {
    var beforeHomes = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    var beforeMemberships = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    doThrow(new DataIntegrityViolationException("unrelated constraint"))
        .when(memberships)
        .insertActive(any(), any());
    assertThatThrownBy(
            () -> service.create(new HouseholdDraft("Home", null, "EUR", "Europe/Berlin")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id")).isEqualTo(beforeHomes);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(beforeMemberships);
  }

  @Test
  void realProxyRollsBackAfterHouseholdWriteAndBypassWouldLeaveOrphan() throws Exception {
    var before = jdbc.queryForList("SELECT id FROM households ORDER BY id", UUID.class);
    var beforeMemberships = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    doThrow(new IllegalStateException("genuine membership failure"))
        .when(memberships)
        .insertActive(any(), any());
    assertThatThrownBy(
            () -> service.create(new HouseholdDraft("Rollback", null, "EUR", "Europe/Berlin")))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("genuine membership failure");
    assertThat(jdbc.queryForList("SELECT id FROM households ORDER BY id", UUID.class))
        .isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(beforeMemberships);
    CreateHouseholdService target = AopTestUtils.getUltimateTargetObject(service);
    assertThatThrownBy(
            () -> target.create(new HouseholdDraft("Bypass", null, "EUR", "Europe/Berlin")))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("genuine membership failure");
    var afterBypass = jdbc.queryForList("SELECT id FROM households ORDER BY id", UUID.class);
    assertThat(afterBypass).hasSize(before.size() + 1);
    afterBypass.removeAll(before);
    jdbc.update("DELETE FROM households WHERE id = ?", afterBypass.getFirst());
  }

  @Test
  void concurrentCreatesYieldOneWinnerAndOneConflictWithoutOrphan() throws Exception {
    var ready = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    doAnswer(
            inv -> {
              Object result = inv.callRealMethod();
              ready.countDown();
              if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("race timeout");
              }
              return result;
            })
        .when(memberships)
        .findActiveHouseholdId(actor);
    long before = jdbc.queryForObject("SELECT count(*) FROM households", Long.class);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(this::raceCreate);
      var b = pool.submit(this::raceCreate);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      assertThat(java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("created", "conflict");
    } finally {
      release.countDown();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id = ? AND active",
                Long.class,
                actor))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM households", Long.class))
        .isEqualTo(before + 1);
  }

  private String raceCreate() {
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    try {
      service.create(new HouseholdDraft("Race", null, "EUR", "Europe/Berlin"));
      return "created";
    } catch (ActiveHouseholdExistsException e) {
      return "conflict";
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
