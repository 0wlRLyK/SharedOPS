package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.contract.HouseholdLookup;
import dev.sharedops.household.domain.HouseholdId;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class HouseholdPersistenceAdapter implements HouseholdLookup {
  private final HouseholdJpaRepository repository;

  public HouseholdPersistenceAdapter(HouseholdJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean existsById(HouseholdId id) {
    UUID householdId = id.id();
    return repository.existsById(householdId);
  }
}
