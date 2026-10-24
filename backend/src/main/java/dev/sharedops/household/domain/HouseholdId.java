package dev.sharedops.household.domain;

import java.util.Objects;
import java.util.UUID;

public record HouseholdId(UUID id) {
  public HouseholdId {
    Objects.requireNonNull(id, "HouseholdId must not be null");
  }
}
