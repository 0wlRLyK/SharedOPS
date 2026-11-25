package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CurrentContextIT {
  @Autowired CurrentContextStore store;
  @Autowired JdbcTemplate jdbc;

  final UUID a = UUID.randomUUID();
  final UUID b = UUID.randomUUID();
  final UUID inactive = UUID.randomUUID();
  final UUID absent = UUID.randomUUID();
  final UUID homeA = UUID.randomUUID();
  final UUID homeB = UUID.randomUUID();

  @BeforeEach
  void fixtures() {
    for (UUID id : List.of(a, b, inactive, absent)) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          id,
          id + "@example.test");
    }
    jdbc.update(
        "INSERT INTO households (id,name,icon,currency,timezone) VALUES (?, 'Home A', NULL, 'EUR', 'Europe/Berlin'), (?, 'Home B', 'star', 'USD', 'America/New_York')",
        homeA,
        homeB);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 1), (?, ?, ?, true, 1), (?, ?, ?, false, NULL), (?, ?, ?, false, NULL)",
        UUID.randomUUID(),
        a,
        homeA,
        UUID.randomUUID(),
        b,
        homeB,
        UUID.randomUUID(),
        a,
        homeB,
        UUID.randomUUID(),
        inactive,
        homeB);
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM household_memberships WHERE account_id IN (?, ?, ?, ?)",
        a,
        b,
        inactive,
        absent);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", homeA, homeB);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?, ?)", a, b, inactive, absent);
  }

  @Test
  void readsOnlyTheActorsActiveHouseholdAndCurrentStoredValuesWithoutWrites() {
    jdbc.update("UPDATE accounts SET email = ? WHERE id = ?", "updated-" + a + "@example.test", a);
    jdbc.update("UPDATE households SET name = 'Updated Home' WHERE id = ?", homeA);
    var before = snapshot();

    assertThat(store.findForAccount(a))
        .contains(
            new CurrentContext(
                a,
                "updated-" + a + "@example.test",
                new HouseholdSnapshot(homeA, "Updated Home", null, "EUR", "Europe/Berlin")));
    assertThat(store.findForAccount(b))
        .contains(
            new CurrentContext(
                b,
                b + "@example.test",
                new HouseholdSnapshot(homeB, "Home B", "star", "USD", "America/New_York")));
    assertThat(store.findForAccount(inactive))
        .contains(new CurrentContext(inactive, inactive + "@example.test", null));
    assertThat(store.findForAccount(absent))
        .contains(new CurrentContext(absent, absent + "@example.test", null));
    assertThat(store.findForAccount(UUID.randomUUID())).isEmpty();
    assertThat(snapshot()).isEqualTo(before);
  }

  private record Snapshot(
      List<Map<String, Object>> accounts,
      List<Map<String, Object>> households,
      List<Map<String, Object>> memberships) {}

  private Snapshot snapshot() {
    return new Snapshot(
        jdbc.queryForList(
            "SELECT id, email FROM accounts WHERE id IN (?, ?, ?, ?) ORDER BY id",
            a,
            b,
            inactive,
            absent),
        jdbc.queryForList(
            "SELECT id, name, icon, currency, timezone FROM households WHERE id IN (?, ?) ORDER BY id",
            homeA,
            homeB),
        jdbc.queryForList(
            "SELECT id, account_id, household_id, active, slot FROM household_memberships WHERE account_id IN (?, ?, ?, ?) ORDER BY id",
            a,
            b,
            inactive,
            absent));
  }
}
