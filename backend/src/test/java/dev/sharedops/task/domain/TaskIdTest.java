package dev.sharedops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TaskIdTest {
  @Test
  public void whenNullValuePassed() {
    assertThatThrownBy(() -> new TaskId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("TaskId must not be null");
  }

  @Test
  public void whenCorrectValuePassed() {
    UUID value = UUID.randomUUID();
    assertThat(new TaskId(value).id()).isEqualTo(value);
  }

  @Test
  public void equalIdsHaveEqualValuesAndHashes() {
    UUID value = UUID.randomUUID();
    TaskId first = new TaskId(value);
    TaskId second = new TaskId(value);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first).isNotSameAs(second);
  }
}
