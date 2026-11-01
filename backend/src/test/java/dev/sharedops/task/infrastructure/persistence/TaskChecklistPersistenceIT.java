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
    UUID taskId = UUID.randomUUID();
    UUID checklistEntryA = UUID.randomUUID();
    UUID checklistEntryB = UUID.randomUUID();

    jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
    TaskEntity task = new TaskEntity(taskId, householdId);
    task.addChecklistEntry(new ChecklistEntryEntity(checklistEntryA, task, "Text A", false, 0));
    task.addChecklistEntry(new ChecklistEntryEntity(checklistEntryB, task, "Text B", false, 1));
    entityManager.persist(task);
    entityManager.flush();
    entityManager.clear();

    TaskEntity loadedTask = entityManager.find(TaskEntity.class, taskId);
    ChecklistEntryEntity loadedChecklistEntry =
        loadedTask.getChecklistEntries().stream()
            .filter(t -> t.getId().equals(checklistEntryA))
            .findFirst()
            .orElseThrow();
    ;
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
