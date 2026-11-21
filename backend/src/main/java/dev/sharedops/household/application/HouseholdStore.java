package dev.sharedops.household.application;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdStore {
  HouseholdSnapshot insert(HouseholdDraft draft);

  Optional<HouseholdSnapshot> findById(UUID householdId);
}
