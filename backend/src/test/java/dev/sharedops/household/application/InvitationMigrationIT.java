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
class InvitationMigrationIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @Test
  void upgradesExistingMembershipsAndEnforcesSlotsAndInvitationConstraints() {
    String schema = newSchema();
    try {
      migrateToSeven(schema);
      UUID first = household(schema);
      UUID second = household(schema);
      UUID a = account(schema);
      UUID b = account(schema);
      UUID c = account(schema);
      UUID inactive = account(schema);
      UUID m1 = UUID.randomUUID();
      UUID m2 = UUID.randomUUID();
      UUID m3 = UUID.randomUUID();
      UUID m4 = UUID.randomUUID();
      membership(schema, m1, a, first, true);
      membership(schema, m2, b, first, true);
      membership(schema, m3, c, second, true);
      membership(schema, m4, inactive, second, false);
      migrateLatest(schema);

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM "
                      + schema
                      + ".household_memberships WHERE active AND slot IN (1,2)",
                  Integer.class))
          .isEqualTo(3);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(DISTINCT slot) FROM "
                      + schema
                      + ".household_memberships WHERE household_id = ? AND active",
                  Integer.class,
                  first))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForList(
                  "SELECT slot FROM "
                      + schema
                      + ".household_memberships WHERE household_id = ? ORDER BY id",
                  Integer.class,
                  first))
          .containsExactly(1, 2);
      assertThat(
              jdbc.queryForObject(
                  "SELECT slot FROM " + schema + ".household_memberships WHERE id = ?",
                  Integer.class,
                  m4))
          .isNull();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM "
                      + schema
                      + ".household_memberships WHERE id IN (?, ?, ?, ?)",
                  Integer.class,
                  m1,
                  m2,
                  m3,
                  m4))
          .isEqualTo(4);

      UUID extra = account(schema);
      assertThatThrownBy(() -> insertSlot(schema, extra, second, null, true))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertSlot(schema, extra, second, 3, true))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertSlot(schema, extra, second, 1, true))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertSlot(schema, extra, second, 2, false))
          .isInstanceOf(DataIntegrityViolationException.class);
      insertSlot(schema, extra, second, 2, true);
      assertThatThrownBy(() -> insertSlot(schema, extra, first, 1, true))
          .isInstanceOf(DataIntegrityViolationException.class);

      UUID invitation = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO "
              + schema
              + ".household_invitations (id, household_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, now(), now() + interval '24 hours')",
          invitation,
          first,
          new byte[32]);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + schema
                          + ".household_invitations (id, household_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, now(), now() + interval '24 hours')",
                      UUID.randomUUID(),
                      first,
                      new byte[32]))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE "
                          + schema
                          + ".household_invitations SET consumed_at = now(), revoked_at = now() WHERE id = ?",
                      invitation))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + schema
                          + ".household_invitations (id, household_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, now(), now())",
                      UUID.randomUUID(),
                      first,
                      new byte[31]))
          .isInstanceOf(DataIntegrityViolationException.class);
      byte[] distinctHash = new byte[32];
      distinctHash[0] = 1;
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + schema
                          + ".household_invitations (id, household_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, now(), now())",
                      UUID.randomUUID(),
                      first,
                      distinctHash))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  @Test
  void overCapacityUpgradeFailsWithoutChangingLegacyRowsOrSchema() {
    String schema = newSchema();
    try {
      migrateToSeven(schema);
      UUID household = household(schema);
      for (int i = 0; i < 3; i++) {
        membership(schema, UUID.randomUUID(), account(schema), household, true);
      }
      assertThatThrownBy(() -> migrateLatest(schema))
          .hasMessageContaining("household exceeds two active members");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM "
                      + schema
                      + ".household_memberships WHERE household_id = ? AND active",
                  Integer.class,
                  household))
          .isEqualTo(3);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'household_memberships' AND column_name = 'slot'",
                  Integer.class,
                  schema))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'household_invitations'",
                  Integer.class,
                  schema))
          .isZero();
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  private String newSchema() {
    String schema = "ho11_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    return schema;
  }

  private void migrateToSeven(String schema) {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .target("7")
        .load()
        .migrate();
  }

  private void migrateLatest(String schema) {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
  }

  private UUID household(String schema) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO "
            + schema
            + ".households (id, name, currency, timezone) VALUES (?, 'Fixture', 'EUR', 'Europe/Berlin')",
        id);
    return id;
  }

  private UUID account(String schema) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO " + schema + ".accounts (id, email, password_hash) VALUES (?, ?, 'hash')",
        id,
        id + "@example.com");
    return id;
  }

  private void membership(String schema, UUID id, UUID account, UUID household, boolean active) {
    jdbc.update(
        "INSERT INTO "
            + schema
            + ".household_memberships (id, account_id, household_id, active) VALUES (?, ?, ?, ?)",
        id,
        account,
        household,
        active);
  }

  private void insertSlot(
      String schema, UUID account, UUID household, Integer slot, boolean active) {
    jdbc.update(
        "INSERT INTO "
            + schema
            + ".household_memberships (id, account_id, household_id, active, slot) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        account,
        household,
        active,
        slot);
  }
}
