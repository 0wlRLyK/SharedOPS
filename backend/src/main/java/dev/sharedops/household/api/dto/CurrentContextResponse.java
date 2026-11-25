package dev.sharedops.household.api.dto;

import java.util.UUID;

public record CurrentContextResponse(AccountResponse account, Household household) {
  public record Household(UUID id, String name, String icon, String currency, String timezone) {}
}
