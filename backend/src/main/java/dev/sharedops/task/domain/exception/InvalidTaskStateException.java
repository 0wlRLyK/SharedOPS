package dev.sharedops.task.domain.exception;

import dev.sharedops.common.api.exception.DomainException;

public class InvalidTaskStateException extends DomainException {
  public InvalidTaskStateException(String message) {
    super(message);
  }
}
