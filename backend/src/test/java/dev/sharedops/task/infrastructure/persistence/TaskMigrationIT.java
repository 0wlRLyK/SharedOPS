package dev.sharedops.task.infrastructure.persistence;

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
class TaskMigrationIT {
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
    String schema = "task_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    return schema;
  }

  private void household(String s, UUID id) {
    jdbc.update(
        "INSERT INTO "
            + s
            + ".households (id,name,currency,timezone) VALUES (?, 'Home','EUR','Europe/Berlin')",
        id);
  }

  @Test
  void populatedUpgradeFailsWithoutChangingExistingRows() {
    String s = schema();
    try {
      flyway(s, "9").migrate();
      UUID household = UUID.randomUUID(), task = UUID.randomUUID();
      household(s, household);
      jdbc.update("INSERT INTO " + s + ".tasks (id,household_id) VALUES (?,?)", task, household);
      assertThatThrownBy(() -> flyway(s, "10").migrate())
          .hasMessageContaining(
              "Existing tasks require an approved data-preserving migration before V10");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + s + ".tasks WHERE id=?", Integer.class, task))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='tasks' AND column_name='title'",
                  Integer.class,
                  s))
          .isZero();
    } finally {
      jdbc.execute("DROP SCHEMA " + s + " CASCADE");
    }
  }

  @Test
  void emptyUpgradeEnforcesVariantsReferencesAndImmutableFacts() {
    String s = schema();
    try {
      flyway(s, "9").migrate();
      flyway(s, "10").migrate();
      UUID household = UUID.randomUUID(), other = UUID.randomUUID(), task = UUID.randomUUID();
      UUID actor = UUID.randomUUID(), member = UUID.randomUUID(), foreign = UUID.randomUUID();
      household(s, household);
      household(s, other);
      jdbc.update(
          "INSERT INTO " + s + ".accounts (id,email,password_hash) VALUES (?,?,'hash')",
          actor,
          actor + "@example.com");
      UUID foreignAccount = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO " + s + ".accounts (id,email,password_hash) VALUES (?,?,'hash')",
          foreignAccount,
          foreignAccount + "@example.com");
      jdbc.update(
          "INSERT INTO "
              + s
              + ".household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
          member,
          actor,
          household);
      jdbc.update(
          "INSERT INTO "
              + s
              + ".household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
          foreign,
          foreignAccount,
          other);
      String insert =
          "INSERT INTO "
              + s
              + ".tasks (id,household_id,title,priority,status,version,deadline_kind,assignee_membership_id) VALUES (?,?,?,'NORMAL','OPEN',0,'NONE',?)";
      jdbc.update(insert, task, household, "Task", member);
      assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), household, " \t", member))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), household, "Task", foreign))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () -> jdbc.update("UPDATE " + s + ".tasks SET due_nano=1 WHERE id=?", task))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE "
                          + s
                          + ".tasks SET deadline_kind='DATE_TIME', due_epoch_second=1, due_nano=1000000000 WHERE id=?",
                      task))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE "
                          + s
                          + ".tasks SET deadline_kind='DATE',due_date_iso='2026-01-01' WHERE id=?",
                      task))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () -> jdbc.update("UPDATE " + s + ".tasks SET status='OVERDUE' WHERE id=?", task))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () -> jdbc.update("UPDATE " + s + ".tasks SET priority='URGENT' WHERE id=?", task))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () -> jdbc.update("UPDATE " + s + ".tasks SET version=-1 WHERE id=?", task))
          .isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update(
          "UPDATE "
              + s
              + ".tasks SET deadline_kind='DATE', due_date_iso='2026-01-01', due_zone='Europe/Berlin' WHERE id=?",
          task);
      jdbc.update(
          "UPDATE "
              + s
              + ".tasks SET deadline_kind='NONE', due_date_iso=NULL, due_zone=NULL WHERE id=?",
          task);
      jdbc.update(
          "UPDATE "
              + s
              + ".tasks SET deadline_kind='DATE_TIME', due_epoch_second=1, due_nano=999999999 WHERE id=?",
          task);
      UUID fact = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO "
              + s
              + ".task_edit_facts (id,task_id,household_id,actor_account_id,occurred_at,previous_version,new_version,changed_fields) VALUES (?,?,?,?,now(),0,1,ARRAY['TITLE'])",
          fact,
          task,
          household,
          actor);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE " + s + ".task_edit_facts SET new_version=2 WHERE id=?", fact))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + s
                          + ".task_edit_facts (id,task_id,household_id,actor_account_id,occurred_at,previous_version,new_version,changed_fields) VALUES (?,?,?,?,now(),0,1,ARRAY['INVALID'])",
                      UUID.randomUUID(),
                      task,
                      household,
                      actor))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + s
                          + ".task_edit_facts (id,task_id,household_id,actor_account_id,occurred_at,previous_version,new_version,changed_fields) VALUES (?,?,?,?,now(),0,2,ARRAY['TITLE'])",
                      UUID.randomUUID(),
                      task,
                      household,
                      actor))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO "
                          + s
                          + ".task_edit_facts (id,task_id,household_id,actor_account_id,occurred_at,previous_version,new_version,changed_fields) VALUES (?,?,?,?,now(),0,1,ARRAY['TITLE'])",
                      UUID.randomUUID(),
                      task,
                      other,
                      actor))
          .isInstanceOf(DataIntegrityViolationException.class);

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + s + ".task_edit_facts WHERE task_id=?",
                  Integer.class,
                  task))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT due_nano FROM " + s + ".tasks WHERE id=?", Integer.class, task))
          .isEqualTo(999999999);
    } finally {
      jdbc.execute("DROP SCHEMA " + s + " CASCADE");
    }
  }
}
