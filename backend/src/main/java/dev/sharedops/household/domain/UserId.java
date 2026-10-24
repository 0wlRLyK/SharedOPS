package dev.sharedops.household.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID id) {
  public UserId {
    Objects.requireNonNull(id, "UserId must not be null");
  }
}
