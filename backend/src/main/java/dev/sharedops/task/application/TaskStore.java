package dev.sharedops.task.application;

import dev.sharedops.task.domain.Task;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Transaction-bound persistence port. The caller authorizes the household before use. */
public interface TaskStore {
  void insert(Task task);

  /** Lock and read by both household and task identifiers, without exposing foreign tasks. */
  Optional<Task> lock(UUID householdId, UUID taskId);

  /**
   * Compare-and-set task state and insert its edit fact atomically; false means stale or absent.
   */
  boolean update(Task task, long expectedVersion, EditFact fact);

  enum EditedField {
    TITLE,
    DESCRIPTION,
    DEADLINE,
    PRIORITY,
    ASSIGNMENT
  }

  /** Audit metadata only: no task title, description or other user-provided content. */
  record EditFact(
      UUID id,
      UUID taskId,
      UUID householdId,
      UUID actorAccountId,
      Instant occurredAt,
      long previousVersion,
      long newVersion,
      Set<EditedField> changedFields) {
    public EditFact {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(taskId, "taskId");
      Objects.requireNonNull(householdId, "householdId");
      Objects.requireNonNull(actorAccountId, "actorAccountId");
      Objects.requireNonNull(occurredAt, "occurredAt");
      changedFields = Set.copyOf(Objects.requireNonNull(changedFields, "changedFields"));
      if (previousVersion < 0
          || previousVersion == Long.MAX_VALUE
          || newVersion != previousVersion + 1
          || changedFields.isEmpty()) {
        throw new IllegalArgumentException("Invalid edit fact");
      }
    }
  }
}
