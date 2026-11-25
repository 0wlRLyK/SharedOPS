package dev.sharedops.task.application;

import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.TaskStatus;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Evaluates a task deadline using the application's injected time source. */
@Service
public class DeadlineEvaluator {
  private final Clock clock;

  public DeadlineEvaluator(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public boolean isOverdue(Deadline deadline, TaskStatus status) {
    return Objects.requireNonNull(deadline, "deadline").isOverdue(status, clock.instant());
  }
}
