package dev.sharedops.household.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class HouseholdIdTest {
  @Test
  public void whenNullValuePassed() {
    assertThatThrownBy(() -> new HouseholdId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("HouseholdId must not be null");
  }

  @Test
  public void whenCorrectValuePassed() {
    UUID value = UUID.randomUUID();
    assertThat(new HouseholdId(value).id()).isEqualTo(value);
  }

  @Test
  public void equalIdsHaveEqualValuesAndHashes() {
    UUID value = UUID.randomUUID();
    HouseholdId first = new HouseholdId(value);
    HouseholdId second = new HouseholdId(value);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first).isNotSameAs(second);
  }
}
