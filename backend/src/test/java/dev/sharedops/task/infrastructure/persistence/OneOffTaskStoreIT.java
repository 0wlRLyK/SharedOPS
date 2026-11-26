package dev.sharedops.task.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.task.application.TaskStore;
import dev.sharedops.task.application.TaskStore.EditFact;
import dev.sharedops.task.application.TaskStore.EditedField;
import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.Task;
import dev.sharedops.task.domain.TaskPriority;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OneOffTaskStoreIT {
  @Autowired TaskStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;

  @Test
  void deadlinesRoundTripExactlyAndReadsAreHouseholdScoped() {
    UUID household = household();
    UUID other = household();
    try {
      Task none = task(household, Deadline.none());
      Task date = task(household, Deadline.date("2026-10-25", "Europe/Berlin"));
      Task instant =
          task(household, Deadline.dateTime(Instant.parse("2026-10-25T01:30:00.123456789Z")));
      tx.executeWithoutResult(
          status -> {
            store.insert(none);
            store.insert(date);
            store.insert(instant);
          });
      tx.executeWithoutResult(
          status -> {
            assertThat(store.lock(household, none.id())).contains(none);
            assertThat(store.lock(household, date.id())).contains(date);
            assertThat(store.lock(household, instant.id())).contains(instant);
            assertThat(store.lock(other, instant.id())).isEmpty();
          });
      assertThat(
              jdbc.queryForObject(
                  "SELECT due_nano FROM tasks WHERE id=?", Integer.class, instant.id()))
          .isEqualTo(123456789);
      assertThat(
              jdbc.queryForObject("SELECT due_zone FROM tasks WHERE id=?", String.class, date.id()))
          .isEqualTo("Europe/Berlin");
    } finally {
      cleanup(household);
      cleanup(other);
    }
  }

  @Test
  void compareAndSetWritesFactAtomicallyAndPreservesChecklist() {
    UUID household = household();
    UUID actor = account();
    Task original = task(household, Deadline.none());
    UUID entry = UUID.randomUUID();
    try {
      tx.executeWithoutResult(status -> store.insert(original));
      jdbc.update(
          "INSERT INTO checklist_entries (id, task_id, text, is_completed, position) VALUES (?, ?, 'Keep', false, 0)",
          entry,
          original.id());
      Task edited =
          original.edit(
              "Updated",
              null,
              TaskPriority.HIGH,
              Deadline.date("2026-11-26", "Europe/Berlin"),
              null);
      EditFact fact = fact(edited, actor, 0);
      assertThat(Boolean.TRUE.equals(tx.execute(status -> store.update(edited, 0, fact)))).isTrue();
      assertThat(
              Boolean.FALSE.equals(
                  tx.execute(status -> store.update(edited, 0, fact(edited, actor, 0)))))
          .isTrue();
      assertThat(
              tx.<java.util.Optional<Task>>execute(status -> store.lock(household, original.id())))
          .contains(edited);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM task_edit_facts WHERE task_id=?",
                  Integer.class,
                  original.id()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM checklist_entries WHERE id=? AND task_id=?",
                  Integer.class,
                  entry,
                  original.id()))
          .isEqualTo(1);
      Task next = edited.edit("Must roll back", null, TaskPriority.LOW, Deadline.none(), null);
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      status -> store.update(next, 1, fact(next, UUID.randomUUID(), 1))))
          .isInstanceOf(RuntimeException.class);
      assertThat(
              tx.<java.util.Optional<Task>>execute(status -> store.lock(household, original.id())))
          .contains(edited);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM task_edit_facts WHERE task_id=?",
                  Integer.class,
                  original.id()))
          .isEqualTo(1);
    } finally {
      cleanup(household);
      jdbc.update("DELETE FROM accounts WHERE id=?", actor);
    }
  }

  @Test
  void invalidPersistedDeadlineFailsClosedAndOperationsRequireTransaction() {
    UUID household = household();
    Task task = task(household, Deadline.none());
    try {
      assertThatThrownBy(() -> store.insert(task)).isInstanceOf(RuntimeException.class);
      tx.executeWithoutResult(status -> store.insert(task));
      jdbc.update(
          "UPDATE tasks SET deadline_kind='DATE', due_date_iso='not-a-date', due_zone='Europe/Berlin' WHERE id=?",
          task.id());
      assertThatThrownBy(() -> tx.execute(status -> store.lock(household, task.id())))
          .isInstanceOf(RuntimeException.class);
    } finally {
      cleanup(household);
    }
  }

  private UUID household() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        id);
    return id;
  }

  private UUID account() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        id,
        id + "@example.com");
    return id;
  }

  private Task task(UUID household, Deadline deadline) {
    return Task.create(
        UUID.randomUUID(),
        household,
        null,
        "Title",
        "Description",
        TaskPriority.NORMAL,
        deadline,
        null);
  }

  private EditFact fact(Task task, UUID actor, long previous) {
    return new EditFact(
        UUID.randomUUID(),
        task.id(),
        task.householdId(),
        actor,
        Instant.parse("2026-11-26T10:00:00Z"),
        previous,
        task.version(),
        Set.of(EditedField.TITLE));
  }

  private void cleanup(UUID household) {
    jdbc.update("DELETE FROM task_edit_facts WHERE household_id=?", household);
    jdbc.update(
        "DELETE FROM checklist_entries WHERE task_id IN (SELECT id FROM tasks WHERE household_id=?)",
        household);
    jdbc.update("DELETE FROM tasks WHERE household_id=?", household);
    jdbc.update("DELETE FROM households WHERE id=?", household);
  }
}
