package dev.sharedops.household.application;

import dev.sharedops.household.infrastructure.persistence.HouseholdRepositoryFixture;
import org.springframework.context.ApplicationContext;

public record AllowedHouseholdPersistenceUsage(ApplicationContext context)
    implements HouseholdRepositoryFixture {}
