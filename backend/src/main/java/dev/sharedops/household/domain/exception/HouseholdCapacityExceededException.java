package dev.sharedops.household.domain.exception;

import dev.sharedops.common.exception.DomainException;

public class HouseholdCapacityExceededException extends DomainException {
  public HouseholdCapacityExceededException(String message) {
    super(message);
  }
}
