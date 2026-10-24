package dev.sharedops.task.domain;

import java.util.List;
import java.util.Objects;

public record TaskChecklist(List<String> items) {
  public TaskChecklist {
    Objects.requireNonNull(items, "TaskChecklist must not be null");
    items = List.copyOf(items);
  }
}
