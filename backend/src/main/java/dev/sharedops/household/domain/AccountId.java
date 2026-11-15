package dev.sharedops.household.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID id) {
  public AccountId {
    Objects.requireNonNull(id, "AccountId must not be null");
  }
}
