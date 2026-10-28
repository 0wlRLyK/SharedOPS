package dev.sharedops.household.api;

import dev.sharedops.household.infrastructure.persistence.HouseholdRepositoryFixture;

public record ForbiddenApiToPersistence(HouseholdRepositoryFixture fixture) {}
