package dev.sharedops.household.application;

import java.util.UUID;

public record HouseholdSnapshot(
    UUID id, String name, String icon, String currency, String timezone) {}
