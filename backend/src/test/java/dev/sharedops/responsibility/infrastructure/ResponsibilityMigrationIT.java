package dev.sharedops.responsibility.infrastructure;

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
class ResponsibilityMigrationIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  private Flyway flyway(String schema, String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .target(target)
        .load();
  }

  private String schema() {
    String schema = "resp_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    return schema;
  }

  private void insertHousehold(String s, UUID household) {
    jdbc.update(
        "INSERT INTO "
            + s
            + ".households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        household);
  }

  private void insertMember(
      String s, UUID membership, UUID account, UUID household, boolean active) {
    jdbc.update(
        "INSERT INTO " + s + ".accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        account,
        account + "@example.com");
    jdbc.update(
        "INSERT INTO "
            + s
            + ".household_memberships (id, account_id, household_id, active, slot) VALUES (?, ?, ?, ?, ?)",
        membership,
        account,
        household,
        active,
        active ? 1 : null);
  }

  private void insertResponsibility(String s, UUID id, UUID household, UUID owner, String status) {
    jdbc.update(
        "INSERT INTO "
            + s
            + ".responsibilities (id,household_id,title,description,mode,status,owner_membership_id,version) VALUES (?, ?, 'Title', NULL, 'INDIVIDUAL', ?, ?, 0)",
        id,
        household,
        status,
        owner);
  }

  @Test
  void productionTitleConstraintMatchesJavaBlankForUnicodeWhitespace() {
    String s = schema();
    try {
      flyway(s, "9").migrate();
      UUID household = UUID.randomUUID();
      UUID member = UUID.randomUUID();
      insertHousehold(s, household);
      insertMember(s, member, UUID.randomUUID(), household, true);
      // Include Java whitespace, non-breaking spaces, and mixed content. The stored value
      // is compared without trimming so that valid titles preserve their original bytes.
      for (String title :
          new String[] {
            "",
            " ",
            "\t\n",
            "\u001c",
            "\u1680",
            "\u2003",
            "\u2028",
            "\u3000",
            "\u00a0",
            "\u2007",
            "\u202f",
            "\u2003x\u2003"
          }) {
        UUID id = UUID.randomUUID();
        if (title.isBlank()) {
          assertThatThrownBy(
                  () ->
                      jdbc.update(
                          "INSERT INTO "
                              + s
                              + ".responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,?,'INDIVIDUAL','ACTIVE',?,0)",
                          id,
                          household,
                          title,
                          member))
              .isInstanceOf(DataIntegrityViolationException.class);
        } else {
          jdbc.update(
              "INSERT INTO "
                  + s
                  + ".responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,?,'INDIVIDUAL','ACTIVE',?,0)",
              id,
              household,
              title,
              member);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT title FROM " + s + ".responsibilities WHERE id=?", String.class, id))
              .isEqualTo(title);
        }
      }
    } finally {
      jdbc.execute("DROP SCHEMA " + s + " CASCADE");
    }
  }

  @Test
  void populatedV8UpgradeFailsTransactionallyAndPreservesLinkedTask() {
    String s = schema();
    try {
      flyway(s, "8").migrate();
      UUID household = UUID.randomUUID();
      UUID responsibility = UUID.randomUUID();
      UUID task = UUID.randomUUID();
      insertHousehold(s, household);
      jdbc.update(
          "INSERT INTO " + s + ".responsibilities (id, household_id) VALUES (?, ?)",
          responsibility,
          household);
      jdbc.update(
          "INSERT INTO " + s + ".tasks (id, household_id, responsibility_id) VALUES (?, ?, ?)",
          task,
          household,
          responsibility);
      assertThatThrownBy(() -> flyway(s, "9").migrate())
          .hasMessageContaining(
              "Existing responsibilities require an approved data-preserving migration");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + s + ".responsibilities WHERE id = ?",
                  Integer.class,
                  responsibility))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + s + ".tasks WHERE id = ? AND responsibility_id = ?",
                  Integer.class,
                  task,
                  responsibility))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'responsibilities' AND column_name = 'owner_membership_id'",
                  Integer.class,
                  s))
          .isZero();
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + s
                          + ".tasks (id, household_id, responsibility_id) VALUES (?, ?, ?)",
                      UUID.randomUUID(),
                      household,
                      UUID.randomUUID()))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("DROP SCHEMA " + s + " CASCADE");
    }
  }

  @Test
  void emptyUpgradeEnforcesOwnerStatusAndHistoricalReferences() {
    String s = schema();
    try {
      flyway(s, "9").migrate();
      UUID h = UUID.randomUUID(),
          other = UUID.randomUUID(),
          member = UUID.randomUUID(),
          foreign = UUID.randomUUID(),
          inactive = UUID.randomUUID();
      UUID a = UUID.randomUUID(),
          b = UUID.randomUUID(),
          c = UUID.randomUUID(),
          r = UUID.randomUUID();
      insertHousehold(s, h);
      insertHousehold(s, other);
      insertMember(s, member, a, h, true);
      insertMember(s, foreign, b, other, true);
      insertMember(s, inactive, c, h, false);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO " + s + ".responsibilities (id, household_id) VALUES (?, ?)",
                      UUID.randomUUID(),
                      h))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertResponsibility(s, UUID.randomUUID(), h, foreign, "ACTIVE"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertResponsibility(s, UUID.randomUUID(), h, inactive, "ACTIVE"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertResponsibility(s, UUID.randomUUID(), h, member, "COMPLETED"))
          .isInstanceOf(DataIntegrityViolationException.class);
      insertResponsibility(s, r, h, member, "ACTIVE");
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE " + s + ".responsibilities SET title = '   ' WHERE id = ?", r))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE " + s + ".responsibilities SET mode = 'SHARED' WHERE id = ?", r))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update("UPDATE " + s + ".responsibilities SET version = -1 WHERE id = ?", r))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE "
                          + s
                          + ".household_memberships SET active = false, slot = NULL WHERE id = ?",
                      member))
          .isInstanceOf(DataIntegrityViolationException.class);
      UUID archived = UUID.randomUUID();
      insertResponsibility(s, archived, h, inactive, "ARCHIVED");
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE " + s + ".responsibilities SET status = 'ACTIVE' WHERE id = ?",
                      archived))
          .isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update("UPDATE " + s + ".responsibilities SET status = 'ARCHIVED' WHERE id = ?", r);
      jdbc.update(
          "UPDATE " + s + ".household_memberships SET active = false, slot = NULL WHERE id = ?",
          member);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + s + ".responsibilities WHERE status = 'ARCHIVED'",
                  Integer.class))
          .isEqualTo(2);
      jdbc.update("DELETE FROM " + s + ".responsibilities WHERE household_id = ?", h);
      jdbc.update("DELETE FROM " + s + ".household_memberships WHERE household_id = ?", h);
      jdbc.update("DELETE FROM " + s + ".households WHERE id = ?", h);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + s + ".households WHERE id = ?", Integer.class, h))
          .isZero();
    } finally {
      jdbc.execute("DROP SCHEMA " + s + " CASCADE");
    }
  }
}
