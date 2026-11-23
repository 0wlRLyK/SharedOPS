package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.contract.HouseholdLookup;
import dev.sharedops.household.domain.HouseholdId;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HouseholdLookupIT {
  @Autowired HouseholdLookup lookup;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean MembershipStore memberships;
  UUID a;
  UUID b;
  UUID homeA;
  UUID homeB;

  @BeforeEach
  void fixtures() {
    a = UUID.randomUUID();
    b = UUID.randomUUID();
    homeA = UUID.randomUUID();
    homeB = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, ?), (?, ?, ?)",
        a,
        a + "@example.com",
        "hash",
        b,
        b + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin'), (?, 'Other', 'EUR', 'Europe/Berlin')",
        homeA,
        homeB);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 1), (?, ?, ?, true, 1)",
        UUID.randomUUID(),
        a,
        homeA,
        UUID.randomUUID(),
        b,
        homeB);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM household_memberships WHERE account_id IN (?, ?)", a, b);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", homeA, homeB);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?)", a, b);
  }

  private void authenticate(UUID actor) {
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  @Test
  void actualExportDeniesForeignAbsentInactiveAndUntrustedIdentities() {
    var before = jdbc.queryForObject("SELECT count(*) FROM household_memberships", Long.class);
    authenticate(a);
    assertThat(lookup.isAccessible(new HouseholdId(homeA))).isTrue();
    assertDeniedUnchanged(new HouseholdId(homeB));
    assertDeniedUnchanged(new HouseholdId(UUID.randomUUID()));
    assertNoPersistenceAndUnchanged(null);
    jdbc.update(
        "UPDATE household_memberships SET active = false, slot = NULL WHERE account_id = ?", a);
    assertDeniedUnchanged(new HouseholdId(homeA));
    SecurityContextHolder.clearContext();
    assertNoPersistenceAndUnchanged(new HouseholdId(homeB));
    var unauthenticated = new AccountPrincipal(a, a + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.unauthenticated(unauthenticated, null));
    assertNoPersistenceAndUnchanged(new HouseholdId(homeB));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key",
                "anonymous",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertNoPersistenceAndUnchanged(new HouseholdId(homeB));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "unexpected", null, java.util.List.of()));
    assertNoPersistenceAndUnchanged(new HouseholdId(homeB));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM household_memberships", Long.class))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE account_id = ?", Boolean.class, b))
        .isTrue();
  }

  private record DatabaseSnapshot(
      List<Map<String, Object>> households, List<Map<String, Object>> memberships) {}

  private DatabaseSnapshot snapshot() {
    return new DatabaseSnapshot(
        jdbc.queryForList(
            "SELECT id, name, icon, currency, timezone FROM households WHERE id IN (?, ?) ORDER BY id",
            homeA,
            homeB),
        jdbc.queryForList(
            "SELECT id, account_id, household_id, active FROM household_memberships WHERE account_id IN (?, ?) ORDER BY id",
            a,
            b));
  }

  private void assertDeniedUnchanged(HouseholdId target) {
    var before = snapshot();
    assertThat(lookup.isAccessible(target)).isFalse();
    assertThat(snapshot()).isEqualTo(before);
  }

  private void assertNoPersistenceAndUnchanged(HouseholdId target) {
    var before = snapshot();
    clearInvocations(memberships);
    assertThat(lookup.isAccessible(target)).isFalse();
    verifyNoInteractions(memberships);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void concurrentContextsNeverCrossHouseholds() throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> checkActor(a, homeA, homeB, ready, start));
      var second = pool.submit(() -> checkActor(b, homeB, homeA, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      start.countDown();
    }
  }

  private boolean checkActor(
      UUID actor, UUID own, UUID foreign, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    try {
      authenticate(actor);
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        return false;
      }
      for (int i = 0; i < 20; i++) {
        if (!lookup.isAccessible(new HouseholdId(own))) {
          return false;
        }
        var before = snapshot();
        if (lookup.isAccessible(new HouseholdId(foreign)) || !snapshot().equals(before)) {
          return false;
        }
      }
      return true;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
