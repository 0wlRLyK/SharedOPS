package dev.sharedops.household.application;

import java.util.Optional;
import java.util.UUID;

/** Internal membership lookup after locking the relevant household. */
public interface ResponsibilityMembershipStore {
  Optional<UUID> findActiveMembershipId(UUID accountId, UUID householdId);

  Optional<UUID> findAccountId(UUID membershipId, UUID householdId);

  boolean isActiveMembership(UUID membershipId, UUID householdId);
}
