package dev.sharedops.persistence;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseConstraintsIT {
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  public void rejectsCrossHouseholdResponsibility() {
    UUID householdA = UUID.randomUUID();
    UUID householdB = UUID.randomUUID();
    UUID responsibilityId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();

    jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdA);
    jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdB);
    jdbcTemplate.update(
        "INSERT INTO responsibilities (id, household_id) VALUES (?, ?)",
        responsibilityId,
        householdA);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO tasks (id, household_id, responsibility_id) VALUES (?, ?, ?)",
                    taskId,
                    householdB,
                    responsibilityId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_tasks_responsibility_household");
  }

  @Test
  public void rejectsDuplicateChecklistPosition() {
    UUID householdId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID checklistEntryA = UUID.randomUUID();
    UUID checklistEntryB = UUID.randomUUID();

    jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
    jdbcTemplate.update("INSERT INTO tasks (id, household_id) VALUES (?, ?)", taskId, householdId);
    jdbcTemplate.update(
        "INSERT INTO checklist_entries (id, task_id, text, is_completed, position) VALUES (?, ?, ?, ?, ?)",
        checklistEntryA,
        taskId,
        "Text A",
        false,
        0);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO checklist_entries (id, task_id, text, is_completed, position) VALUES (?, ?, ?, ?, ?)",
                    checklistEntryB,
                    taskId,
                    "Text B",
                    false,
                    0))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("checklist_entries_task_id_position_key");
  }

  @Test
  public void rejectsNegativeChecklistPosition() {
    UUID householdId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID checklistEntryA = UUID.randomUUID();

    jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);
    jdbcTemplate.update("INSERT INTO tasks (id, household_id) VALUES (?, ?)", taskId, householdId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO checklist_entries (id, task_id, text, is_completed, position) VALUES (?, ?, ?, ?, ?)",
                    checklistEntryA,
                    taskId,
                    "Text A",
                    false,
                    -1))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("non_negative_position");
  }
}
