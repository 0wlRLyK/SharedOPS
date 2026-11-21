package dev.sharedops.household.application;

import java.util.Optional;
import java.util.UUID;

public interface MembershipStore {
  Optional<UUID> findActiveHouseholdId(UUID accountId);

  boolean isActiveMember(UUID accountId, UUID householdId);

  void insertActive(UUID accountId, UUID householdId);
}
