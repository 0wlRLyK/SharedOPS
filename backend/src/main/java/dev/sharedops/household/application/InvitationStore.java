package dev.sharedops.household.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations used inside an invitation transaction. */
public interface InvitationStore {
  record Locator(UUID invitationId, UUID householdId) {}

  record State(
      UUID id, UUID householdId, Instant expiresAt, Instant consumedAt, Instant revokedAt) {}

  Optional<Locator> findByDigest(byte[] digest);

  /** Locks the household row until the transaction ends; false if absent. */
  boolean lockHousehold(UUID householdId);

  /** Must be called after locking the corresponding household. */
  Optional<State> lockInvitation(UUID householdId, UUID invitationId);

  int activeMemberCount(UUID householdId);

  void insert(UUID id, UUID householdId, byte[] digest, Instant createdAt, Instant expiresAt);

  boolean consume(UUID invitationId, Instant consumedAt);

  boolean revoke(UUID invitationId, Instant revokedAt);
}
