package dev.sharedops.household.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.common.exception.DomainException;
import org.junit.jupiter.api.Test;

public class HouseholdCapacityExceededExceptionTest {

  @Test
  public void householdCapacityExceededExceptionPreservesTypeAndMessage() {
    String message = "Household member limit reached.";

    HouseholdCapacityExceededException exception = new HouseholdCapacityExceededException(message);

    assertThat(exception).isInstanceOf(DomainException.class).hasMessage(message);
  }
}
