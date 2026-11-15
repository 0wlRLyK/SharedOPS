package dev.sharedops.household.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class AccountIdTest {
  @Test
  public void whenNullValuePassed() {
    assertThatThrownBy(() -> new AccountId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("AccountId must not be null");
  }

  @Test
  public void whenCorrectValuePassed() {
    UUID value = UUID.randomUUID();
    assertThat(new AccountId(value).id()).isEqualTo(value);
  }

  @Test
  public void equalIdsHaveEqualValuesAndHashes() {
    UUID value = UUID.randomUUID();
    AccountId first = new AccountId(value);
    AccountId second = new AccountId(value);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first).isNotSameAs(second);
  }
}
