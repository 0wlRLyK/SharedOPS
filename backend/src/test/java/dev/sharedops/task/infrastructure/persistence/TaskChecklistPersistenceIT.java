package dev.sharedops.task.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TaskChecklistPersistenceIT {

  @Autowired TaskJpaRepository taskRepository;

  @Autowired JdbcTemplate jdbcTemplate;

  @PersistenceContext EntityManager entityManager;

  @Test
  void deletingChecklistEntryPreservesTaskAndOtherEntries() {
    UUID householdId = UUID.randomUUID();

    jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
    TaskEntity task = new TaskEntity(householdId);
    ChecklistEntryEntity entryA = new ChecklistEntryEntity(task, "Text A", false, 0);
    ChecklistEntryEntity entryB = new ChecklistEntryEntity(task, "Text B", false, 1);
    task.addChecklistEntry(entryA);
    task.addChecklistEntry(entryB);
    entityManager.persist(task);
    UUID taskId = task.getId();
    UUID checklistEntryA = entryA.getId();
    UUID checklistEntryB = entryB.getId();
    assertThat(taskId).isNotNull();
    assertThat(checklistEntryA).isNotNull();
    assertThat(checklistEntryB).isNotNull().isNotEqualTo(checklistEntryA);
    entityManager.flush();
    entityManager.clear();

    TaskEntity loadedTask = entityManager.find(TaskEntity.class, taskId);
    ChecklistEntryEntity loadedChecklistEntry =
        loadedTask.getChecklistEntries().stream()
            .filter(t -> t.getId().equals(checklistEntryA))
            .findFirst()
            .orElseThrow();
    loadedTask.removeChecklistEntry(loadedChecklistEntry);
    entityManager.flush();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM households WHERE id = ?", Integer.class, householdId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE id = ?", Integer.class, taskId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                Integer.class,
                checklistEntryB))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM checklist_entries WHERE id = ?",
                Integer.class,
                checklistEntryA))
        .isEqualTo(0);
  }
}
