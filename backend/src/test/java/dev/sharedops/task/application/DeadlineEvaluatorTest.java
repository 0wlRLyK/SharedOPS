package dev.sharedops.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeadlineEvaluatorTest {
  private static final Instant DATE_BOUNDARY = Instant.parse("2026-03-29T22:00:00Z");
  private static final Instant DUE_INSTANT = Instant.parse("2026-10-25T01:30:00Z");

  @Test
  void dateBecomesOverdueExactlyAtTheNextHouseholdDay() {
    Deadline deadline = Deadline.date(LocalDate.of(2026, 3, 29), "Europe/Berlin");

    assertThat(
            evaluator(DATE_BOUNDARY.minusNanos(1), ZoneOffset.UTC)
                .isOverdue(deadline, TaskStatus.OPEN))
        .isFalse();
    assertThat(evaluator(DATE_BOUNDARY, ZoneOffset.UTC).isOverdue(deadline, TaskStatus.OPEN))
        .isTrue();
    assertThat(
            evaluator(DATE_BOUNDARY.plusNanos(1), ZoneOffset.UTC)
                .isOverdue(deadline, TaskStatus.OPEN))
        .isTrue();
  }

  @Test
  void dateTimeBecomesOverdueStrictlyAfterItsInstant() {
    Deadline deadline = Deadline.dateTime(DUE_INSTANT);

    assertThat(
            evaluator(DUE_INSTANT.minusNanos(1), ZoneOffset.UTC)
                .isOverdue(deadline, TaskStatus.OPEN))
        .isFalse();
    assertThat(evaluator(DUE_INSTANT, ZoneOffset.UTC).isOverdue(deadline, TaskStatus.OPEN))
        .isFalse();
    assertThat(
            evaluator(DUE_INSTANT.plusNanos(1), ZoneOffset.UTC)
                .isOverdue(deadline, TaskStatus.OPEN))
        .isTrue();
  }

  @Test
  void noneAndEveryTerminalStatusRemainNotOverdue() {
    DeadlineEvaluator evaluator = evaluator(DUE_INSTANT.plusSeconds(60), ZoneOffset.UTC);
    Deadline timedDeadline = Deadline.dateTime(DUE_INSTANT);
    Deadline dateDeadline = Deadline.date(LocalDate.of(2026, 10, 23), "Europe/Berlin");

    for (TaskStatus status : TaskStatus.values()) {
      assertThat(evaluator.isOverdue(Deadline.none(), status)).isFalse();
      if (status != TaskStatus.OPEN) {
        assertThat(evaluator.isOverdue(timedDeadline, status)).isFalse();
        assertThat(evaluator.isOverdue(dateDeadline, status)).isFalse();
      }
    }
  }

  @Test
  void clockZoneDoesNotChangeTheEvaluatedInstant() {
    Deadline deadline = Deadline.date(LocalDate.of(2026, 3, 29), "Europe/Berlin");

    for (Instant instant : new Instant[] {DATE_BOUNDARY.minusNanos(1), DATE_BOUNDARY}) {
      boolean utc = evaluator(instant, ZoneOffset.UTC).isOverdue(deadline, TaskStatus.OPEN);
      assertThat(
              evaluator(instant, ZoneId.of("Pacific/Kiritimati"))
                  .isOverdue(deadline, TaskStatus.OPEN))
          .isEqualTo(utc);
      assertThat(
              evaluator(instant, ZoneId.of("America/Los_Angeles"))
                  .isOverdue(deadline, TaskStatus.OPEN))
          .isEqualTo(utc);
    }
  }

  @Test
  void rejectsMissingClockDeadlineOrStatus() {
    assertThatNullPointerException().isThrownBy(() -> new DeadlineEvaluator(null));
    DeadlineEvaluator evaluator = evaluator(DUE_INSTANT, ZoneOffset.UTC);
    assertThatNullPointerException().isThrownBy(() -> evaluator.isOverdue(null, TaskStatus.OPEN));
    assertThatNullPointerException().isThrownBy(() -> evaluator.isOverdue(Deadline.none(), null));
  }

  private static DeadlineEvaluator evaluator(Instant instant, ZoneId zone) {
    return new DeadlineEvaluator(Clock.fixed(instant, zone));
  }
}
