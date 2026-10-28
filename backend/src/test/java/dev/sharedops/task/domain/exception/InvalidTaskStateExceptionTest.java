package dev.sharedops.task.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.common.exception.DomainException;
import org.junit.jupiter.api.Test;

public class InvalidTaskStateExceptionTest {

  @Test
  public void invalidTaskStateExceptionPreservesTypeAndMessage() {
    String message = "Cannot complete a completed task";

    InvalidTaskStateException exception = new InvalidTaskStateException(message);

    assertThat(exception).isInstanceOf(DomainException.class).hasMessage(message);
  }
}
