package dev.sharedops.household.contract;

import dev.sharedops.household.domain.HouseholdId;
import org.springframework.modulith.NamedInterface;

@NamedInterface(value = "lookup", propagate = true)
public interface HouseholdLookup {
  boolean existsById(HouseholdId id);
}
