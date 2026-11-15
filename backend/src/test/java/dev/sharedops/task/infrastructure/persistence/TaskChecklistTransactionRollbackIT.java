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
    TaskEntity task = new TaskEntity(householdId);
    ChecklistEntryEntity entryA = new ChecklistEntryEntity(task, "Text A", false, 0);
    ChecklistEntryEntity entryB = new ChecklistEntryEntity(task, "Text B", false, 0);
    task.addChecklistEntry(entryA);
    task.addChecklistEntry(entryB);

    try {
      jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        entityManager.persist(task);
                        entityManager.flush();
                      }))
          .hasRootCauseInstanceOf(PSQLException.class);

      assertThat(task.getId()).isNotNull();
      assertThat(entryA.getId()).isNotNull();
      assertThat(entryB.getId()).isNotNull().isNotEqualTo(entryA.getId());
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM households WHERE id = ?", Integer.class, householdId))
          .isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, task.getId()))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  entryB.getId()))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  entryA.getId()))
          .isEqualTo(0);
    } finally {
      jdbcTemplate.update("DELETE FROM households WHERE id = ?", householdId);
    }
  }

  @Test
  void rollsBackWhenImplicitFlushFailsAtCommit() {
    UUID householdId = UUID.randomUUID();
    TaskEntity task = new TaskEntity(householdId);
    ChecklistEntryEntity entryA = new ChecklistEntryEntity(task, "Text A", false, 0);
    ChecklistEntryEntity entryB = new ChecklistEntryEntity(task, "Text B", false, 0);
    task.addChecklistEntry(entryA);
    task.addChecklistEntry(entryB);
    try {
      jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        entityManager.persist(task);
                      }))
          .hasRootCauseInstanceOf(PSQLException.class);
      assertThat(task.getId()).isNotNull();
      assertThat(entryA.getId()).isNotNull();
      assertThat(entryB.getId()).isNotNull().isNotEqualTo(entryA.getId());
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM households WHERE id = ?", Integer.class, householdId))
          .isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, task.getId()))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  entryB.getId()))
          .isEqualTo(0);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                  Integer.class,
                  entryA.getId()))
          .isEqualTo(0);
    } finally {
      jdbcTemplate.update("DELETE FROM households WHERE id = ?", householdId);
    }
  }
}
