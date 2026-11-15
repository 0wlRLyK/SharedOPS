package dev.sharedops.household.domain.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
  public EmailAlreadyRegisteredException(String message) {
    super(message);
  }
}
