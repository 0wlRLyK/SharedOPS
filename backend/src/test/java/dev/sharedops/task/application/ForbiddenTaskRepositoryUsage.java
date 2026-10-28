package dev.sharedops.task.application;

import dev.sharedops.household.infrastructure.persistence.HouseholdRepositoryFixture;
import org.springframework.context.ApplicationContext;

public record ForbiddenTaskRepositoryUsage(ApplicationContext context)
    implements HouseholdRepositoryFixture {}
