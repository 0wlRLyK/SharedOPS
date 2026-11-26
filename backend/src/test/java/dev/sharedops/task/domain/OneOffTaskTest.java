package dev.sharedops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OneOffTaskTest {
  private final UUID id = UUID.randomUUID();
  private final UUID household = UUID.randomUUID();
  private final UUID responsibility = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();

  @Test
  void createsOpenVersionZeroAndRetainsValues() {
    Task task =
        Task.create(
            id,
            household,
            responsibility,
            "Buy milk",
            "Whole",
            TaskPriority.HIGH,
            Deadline.dateTime(Instant.parse("2026-11-25T12:00:00Z")),
            member);
    assertThat(task.status()).isEqualTo(TaskStatus.OPEN);
    assertThat(task.version()).isZero();
    assertThat(task.responsibilityId()).isEqualTo(responsibility);
    assertThat(task.assigneeMembershipId()).isEqualTo(member);
    assertThat(task.description()).isEqualTo("Whole");
    assertThat(task.priority()).isEqualTo(TaskPriority.HIGH);
  }

  @Test
  void rejectsBlankOrUnsupportedTextAndInvalidRequiredValues() {
    assertThatThrownBy(
            () ->
                Task.create(
                    id, household, null, " \t", null, TaskPriority.NORMAL, Deadline.none(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Task.create(
                    id,
                    household,
                    null,
                    "valid\0title",
                    null,
                    TaskPriority.NORMAL,
                    Deadline.none(),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Task.create(
                    id,
                    household,
                    null,
                    "valid",
                    "bad\0text",
                    TaskPriority.NORMAL,
                    Deadline.none(),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Task.create(id, household, null, "valid", null, null, Deadline.none(), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new Task(
                    id,
                    household,
                    null,
                    "valid",
                    null,
                    TaskPriority.NORMAL,
                    TaskStatus.OPEN,
                    Deadline.none(),
                    null,
                    -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editClearsOptionalValuesAndIncrementsEvenForEqualValues() {
    Task original =
        Task.create(
            id,
            household,
            responsibility,
            "Task",
            "Description",
            TaskPriority.NORMAL,
            Deadline.dateTime(Instant.parse("2026-11-25T12:00:00Z")),
            member);
    Task cleared = original.edit("Task", null, TaskPriority.NORMAL, Deadline.none(), null);
    assertThat(cleared.description()).isNull();
    assertThat(cleared.assigneeMembershipId()).isNull();
    assertThat(cleared.deadline()).isEqualTo(Deadline.none());
    assertThat(cleared.responsibilityId()).isEqualTo(responsibility);
    assertThat(cleared.version()).isEqualTo(1);
    assertThat(cleared.edit("Task", null, TaskPriority.NORMAL, Deadline.none(), null).version())
        .isEqualTo(2);
    assertThat(original.version()).isZero();
  }

  @Test
  void terminalTasksAreReadOnlyAndNeverOverdue() {
    Task task =
        new Task(
            id,
            household,
            null,
            "Done",
            null,
            TaskPriority.LOW,
            TaskStatus.COMPLETED,
            Deadline.dateTime(Instant.parse("2026-11-25T12:00:00Z")),
            null,
            7);
    assertThatThrownBy(() -> task.edit("Changed", null, TaskPriority.LOW, Deadline.none(), null))
        .isInstanceOf(IllegalStateException.class);
    assertThat(task.isOverdue(Instant.parse("2026-11-26T12:00:00Z"))).isFalse();
  }

  @Test
  void overdueIsDerivedWithoutChangingTaskState() {
    Instant due = Instant.parse("2026-11-25T12:00:00Z");
    Task task =
        Task.create(
            id, household, null, "Task", null, TaskPriority.NORMAL, Deadline.dateTime(due), null);
    assertThat(task.isOverdue(due)).isFalse();
    assertThat(task.isOverdue(due.plusNanos(1))).isTrue();
    assertThat(task.status()).isEqualTo(TaskStatus.OPEN);
    assertThat(task.version()).isZero();
  }
}
