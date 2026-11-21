package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HouseholdMigrationIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired HouseholdStore households;
  @Autowired MembershipStore memberships;

  @Test
  void activeMembershipIsUniqueAndReferencesExistingRows() {
    UUID actor = UUID.randomUUID();
    UUID household = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        actor,
        actor + "@example.com");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'A','EUR','Europe/Berlin'), (?, 'B','EUR','Europe/Berlin')",
        household,
        other);
    try {
      jdbc.update(
          "INSERT INTO household_memberships VALUES (?, ?, ?, true)",
          UUID.randomUUID(),
          actor,
          household);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO household_memberships VALUES (?, ?, ?, true)",
                      UUID.randomUUID(),
                      actor,
                      other))
          .isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update(
          "INSERT INTO household_memberships VALUES (?, ?, ?, false)",
          UUID.randomUUID(),
          actor,
          other);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO household_memberships VALUES (?, ?, ?, true)",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      household))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO household_memberships VALUES (?, ?, ?, false)",
                      UUID.randomUUID(),
                      actor,
                      UUID.randomUUID()))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM household_memberships WHERE account_id = ? AND active",
                  Integer.class,
                  actor))
          .isEqualTo(1);
    } finally {
      jdbc.update("DELETE FROM household_memberships WHERE account_id = ?", actor);
      jdbc.update("DELETE FROM households WHERE id IN (?, ?)", household, other);
      jdbc.update("DELETE FROM accounts WHERE id = ?", actor);
    }
  }

  @Test
  void productionPortsPersistAndResolveCommittedHouseholdAndMembership() {
    UUID actor = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        actor,
        actor + "@example.com");
    var draft = new HouseholdDraft("Fixture", "icon", "EUR", "Europe/Berlin");
    var household = households.insert(draft);
    try {
      assertThat(households.findById(household.id())).contains(household);
      assertThat(memberships.findActiveHouseholdId(actor)).isEmpty();
      memberships.insertActive(actor, household.id());
      assertThat(memberships.findActiveHouseholdId(actor)).contains(household.id());
      assertThat(memberships.isActiveMember(actor, household.id())).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM household_memberships WHERE account_id = ? AND household_id = ? AND active",
                  Integer.class,
                  actor,
                  household.id()))
          .isEqualTo(1);
    } finally {
      jdbc.update("DELETE FROM household_memberships WHERE account_id = ?", actor);
      jdbc.update("DELETE FROM households WHERE id = ?", household.id());
      jdbc.update("DELETE FROM accounts WHERE id = ?", actor);
    }
  }

  @Test
  void populatedLegacyMigrationFailsWithoutChangingRowsOrSchema() {
    String schema = "ho10_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    try {
      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .target("6")
          .load()
          .migrate();
      UUID household = UUID.randomUUID();
      UUID responsibility = UUID.randomUUID();
      jdbc.update("INSERT INTO " + schema + ".households (id) VALUES (?)", household);
      jdbc.update(
          "INSERT INTO " + schema + ".responsibilities (id, household_id) VALUES (?, ?)",
          responsibility,
          household);
      assertThatThrownBy(
              () ->
                  Flyway.configure()
                      .dataSource(dataSource)
                      .schemas(schema)
                      .defaultSchema(schema)
                      .load()
                      .migrate())
          .hasMessageContaining("Existing households require an approved migration");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + schema + ".households WHERE id = ?",
                  Integer.class,
                  household))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM "
                      + schema
                      + ".responsibilities WHERE id = ? AND household_id = ?",
                  Integer.class,
                  responsibility,
                  household))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'households' AND column_name = 'name'",
                  Integer.class,
                  schema))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'household_memberships'",
                  Integer.class,
                  schema))
          .isZero();
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }
}
