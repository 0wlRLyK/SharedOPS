package dev.sharedops.responsibility.domain;

import java.util.Objects;
import java.util.UUID;

public record ResponsibilityId(UUID id) {
  public ResponsibilityId {
    Objects.requireNonNull(id, "ResponsibilityId must not be null");
  }
}
