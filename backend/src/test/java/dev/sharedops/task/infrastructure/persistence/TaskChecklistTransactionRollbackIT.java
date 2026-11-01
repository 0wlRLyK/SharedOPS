package dev.sharedops.task.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskChecklistTransactionRollbackIT {

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired TransactionTemplate transactionTemplate;

  @PersistenceContext EntityManager entityManager;

  @Test
  void rollsBackWhenExplicitFlushFails() {
    UUID householdId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID checklistEntryA = UUID.randomUUID();
    UUID checklistEntryB = UUID.randomUUID();

    try {
      jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        TaskEntity task = new TaskEntity(taskId, householdId);
                        task.addChecklistEntry(
                            new ChecklistEntryEntity(checklistEntryA, task, "Text A", false, 0));
                        task.addChecklistEntry(
                            new ChecklistEntryEntity(checklistEntryB, task, "Text B", false, 0));
                        entityManager.persist(task);
                        entityManager.flush();
                      }))
          .hasRootCauseInstanceOf(PSQLException.class);

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM households WHERE id = ?", Integer.class, householdId))
          .isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, taskId))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  checklistEntryB))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  checklistEntryA))
          .isEqualTo(0);
    } finally {
      jdbcTemplate.update("DELETE FROM households WHERE id = ?", householdId);
    }
  }

  @Test
  void rollsBackWhenImplicitFlushFailsAtCommit() {
    UUID householdId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID checklistEntryA = UUID.randomUUID();
    UUID checklistEntryB = UUID.randomUUID();
    try {
      jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        TaskEntity task = new TaskEntity(taskId, householdId);
                        task.addChecklistEntry(
                            new ChecklistEntryEntity(checklistEntryA, task, "Text A", false, 0));
                        task.addChecklistEntry(
                            new ChecklistEntryEntity(checklistEntryB, task, "Text B", false, 0));
                        entityManager.persist(task);
                      }))
          .hasRootCauseInstanceOf(PSQLException.class);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM households WHERE id = ?", Integer.class, householdId))
          .isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, taskId))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  checklistEntryB))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  checklistEntryA))
          .isEqualTo(0);
    } finally {
      jdbcTemplate.update("DELETE FROM households WHERE id = ?", householdId);
    }
  }
}
