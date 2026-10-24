package dev.sharedops.household.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class UserIdTest {
  @Test
  public void whenNullValuePassed() {
    assertThatThrownBy(() -> new UserId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("UserId must not be null");
  }

  @Test
  public void whenCorrectValuePassed() {
    UUID value = UUID.randomUUID();
    assertThat(new UserId(value).id()).isEqualTo(value);
  }

  @Test
  public void equalIdsHaveEqualValuesAndHashes() {
    UUID value = UUID.randomUUID();
    UserId first = new UserId(value);
    UserId second = new UserId(value);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first).isNotSameAs(second);
  }
}
