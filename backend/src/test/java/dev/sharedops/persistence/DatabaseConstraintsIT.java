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
    UUID accountId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();
    UUID responsibilityId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'Fixture', 'EUR', 'Europe/Berlin')",
        householdA);
    jdbcTemplate.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'Fixture', 'EUR', 'Europe/Berlin')",
        householdB);
    jdbcTemplate.update(
        "INSERT INTO accounts (id, email, password_hash) VALUES (?, ?, 'hash')",
        accountId,
        accountId + "@example.com");
    jdbcTemplate.update(
        "INSERT INTO household_memberships (id, account_id, household_id, active, slot) VALUES (?, ?, ?, true, 1)",
        membershipId,
        accountId,
        householdA);
    jdbcTemplate.update(
        "INSERT INTO responsibilities (id, household_id, title, mode, status, owner_membership_id, version) VALUES (?, ?, 'Fixture', 'INDIVIDUAL', 'ACTIVE', ?, 0)",
        responsibilityId,
        householdA,
        membershipId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO tasks (id,household_id,responsibility_id,title,priority,status,version,deadline_kind) VALUES (?,?,?,'Task','NORMAL','OPEN',0,'NONE')",
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

    jdbcTemplate.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'Fixture', 'EUR', 'Europe/Berlin')",
        householdId);
    jdbcTemplate.update(
        "INSERT INTO tasks (id,household_id,title,priority,status,version,deadline_kind) VALUES (?,?,'Task','NORMAL','OPEN',0,'NONE')",
        taskId,
        householdId);
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

    jdbcTemplate.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'Fixture', 'EUR', 'Europe/Berlin')",
        householdId);
    jdbcTemplate.update(
        "INSERT INTO tasks (id,household_id,title,priority,status,version,deadline_kind) VALUES (?,?,'Task','NORMAL','OPEN',0,'NONE')",
        taskId,
        householdId);
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
