package dev.sharedops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class RecurrenceIntervalTest {
  @Test
  public void rejectsNullUnit() {
    assertThatThrownBy(() -> new RecurrenceInterval(1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("RecurrenceUnit must not be null");
  }

  @Test
  public void rejectsNegativeInterval() {
    assertThatThrownBy(() -> new RecurrenceInterval(-1, RecurrenceUnit.DAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Interval value must be positive");
  }

  @Test
  public void rejectsZeroInterval() {
    assertThatThrownBy(() -> new RecurrenceInterval(0, RecurrenceUnit.DAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Interval value must be positive");
  }

  @Test
  public void validIntervalsHaveValueEquality() {
    RecurrenceInterval first = new RecurrenceInterval(1, RecurrenceUnit.DAY);
    RecurrenceInterval second = new RecurrenceInterval(1, RecurrenceUnit.DAY);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }
}
