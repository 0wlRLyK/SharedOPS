package dev.sharedops.responsibility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ResponsibilityIdTest {
  @Test
  public void whenNullValuePassed() {
    assertThatThrownBy(() -> new ResponsibilityId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ResponsibilityId must not be null");
  }

  @Test
  public void whenCorrectValuePassed() {
    UUID value = UUID.randomUUID();
    assertThat(new ResponsibilityId(value).id()).isEqualTo(value);
  }

  @Test
  public void equalIdsHaveEqualValuesAndHashes() {
    UUID value = UUID.randomUUID();
    ResponsibilityId first = new ResponsibilityId(value);
    ResponsibilityId second = new ResponsibilityId(value);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first).isNotSameAs(second);
  }
}
