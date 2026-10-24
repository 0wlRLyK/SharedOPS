package dev.sharedops.common.api.exception;

public class DomainException extends RuntimeException {
  public DomainException(String message) {
    super(message);
  }
}
