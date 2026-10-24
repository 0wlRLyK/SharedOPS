package dev.sharedops.task.domain;

import java.util.Objects;
import java.util.UUID;

public record TaskId(UUID id) {
  public TaskId {
    Objects.requireNonNull(id, "TaskId must not be null");
  }
}
