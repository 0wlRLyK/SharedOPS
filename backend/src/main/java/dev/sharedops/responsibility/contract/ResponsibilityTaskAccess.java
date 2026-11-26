package dev.sharedops.responsibility.contract;

import java.util.Optional;
import java.util.UUID;

/** Provides a responsibility snapshot to task creation without exposing persistence. */
public interface ResponsibilityTaskAccess {
  enum Status {
    ACTIVE,
    PAUSED,
    ARCHIVED
  }

  enum Mode {
    INDIVIDUAL
  }

  record Snapshot(UUID ownerMembershipId, Status status, Mode mode) {}

  /**
   * Locks the row until the current transaction ends and returns a same-household snapshot.
   *
   * <p>The caller must already have authorized the actor and acquired the household lock in the
   * current transaction. This operation does not authorize access; the required lock order is
   * household, responsibility, then task. Foreign and missing references return empty.
   */
  Optional<Snapshot> lockForTask(UUID householdId, UUID responsibilityId);
}
