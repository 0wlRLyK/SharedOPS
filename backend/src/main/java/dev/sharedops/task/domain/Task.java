package dev.sharedops.task.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable one-off task; membership and version checks belong to the application boundary. */
public record Task(
    UUID id,
    UUID householdId,
    UUID responsibilityId,
    String title,
    String description,
    TaskPriority priority,
    TaskStatus status,
    Deadline deadline,
    UUID assigneeMembershipId,
    long version) {

  public Task {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(householdId, "householdId");
    requireTitle(title);
    requireDescription(description);
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(deadline, "deadline");
    if (version < 0) {
      throw new IllegalArgumentException("Version must be nonnegative");
    }
  }

  public static Task create(
      UUID id,
      UUID householdId,
      UUID responsibilityId,
      String title,
      String description,
      TaskPriority priority,
      Deadline deadline,
      UUID assigneeMembershipId) {
    return new Task(
        id,
        householdId,
        responsibilityId,
        title,
        description,
        priority,
        TaskStatus.OPEN,
        deadline,
        assigneeMembershipId,
        0);
  }

  /** The application resolves omitted fields to current values before calling this method. */
  public Task edit(
      String nextTitle,
      String nextDescription,
      TaskPriority nextPriority,
      Deadline nextDeadline,
      UUID nextAssigneeMembershipId) {
    if (status != TaskStatus.OPEN) {
      throw new IllegalStateException("Terminal tasks cannot be edited");
    }
    return new Task(
        id,
        householdId,
        responsibilityId,
        nextTitle,
        nextDescription,
        nextPriority,
        status,
        nextDeadline,
        nextAssigneeMembershipId,
        Math.incrementExact(version));
  }

  public boolean isOverdue(Instant now) {
    return deadline.isOverdue(status, now);
  }

  private static void requireTitle(String value) {
    if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Title must not be blank");
    }
  }

  private static void requireDescription(String value) {
    if (value != null && value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Description contains an unsupported character");
    }
  }
}
