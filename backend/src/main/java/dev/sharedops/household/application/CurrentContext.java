package dev.sharedops.household.application;

import java.util.UUID;

public record CurrentContext(UUID accountId, String email, HouseholdSnapshot household) {}
