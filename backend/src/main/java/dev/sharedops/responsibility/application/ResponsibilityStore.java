package dev.sharedops.responsibility.application;

import dev.sharedops.responsibility.domain.Responsibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Household-scoped persistence operations. Call writes within the application transaction. */
public interface ResponsibilityStore {
  record TransferFact(
      UUID id,
      UUID householdId,
      UUID responsibilityId,
      UUID actorAccountId,
      Instant occurredAt,
      UUID previousOwnerMembershipId,
      UUID newOwnerMembershipId) {}

  void insert(Responsibility responsibility);

  Optional<Responsibility> find(UUID householdId, UUID responsibilityId);

  /** Locks an accessible responsibility after the household lock has been acquired. */
  Optional<Responsibility> lock(UUID householdId, UUID responsibilityId);

  /** Returns ACTIVE and PAUSED records or ARCHIVED records, ordered by ID. */
  List<Responsibility> list(UUID householdId, boolean archived);

  /**
   * Persists an already changed aggregate only when the stored version matches expectedVersion. A
   * false result denotes an absent, foreign or stale row; callers must roll back and map conflicts.
   */
  boolean update(Responsibility responsibility, long expectedVersion);

  /** Inserts an immutable fact in the caller's transaction; failures propagate. */
  void insertTransferFact(TransferFact fact);
}
