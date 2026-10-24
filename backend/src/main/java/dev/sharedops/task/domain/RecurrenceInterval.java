package dev.sharedops.task.domain;

import java.util.Objects;

public record RecurrenceInterval(int interval, RecurrenceUnit unit) {
  public RecurrenceInterval {
    if (interval <= 0) {
      throw new IllegalArgumentException("Interval value must be positive");
    }
    Objects.requireNonNull(unit, "RecurrenceUnit must not be null");
  }
}
