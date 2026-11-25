package dev.sharedops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class DeadlineTest {
  private static final String BERLIN = "Europe/Berlin";
  private static final Instant MIN =
      LocalDate.MIN.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(18 * 3600L);
  private static final Instant MAX =
      LocalDate.MAX.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).minusSeconds(18 * 3600L);

  @Test
  void dateBoundaryUsesNextCalendarStartAcrossShortAndLongDays() {
    for (LocalDate date : new LocalDate[] {LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25)}) {
      Deadline deadline = Deadline.date(date, BERLIN);
      Instant boundary = date.plusDays(1).atStartOfDay(ZoneId.of(BERLIN)).toInstant();
      assertThat(deadline.isOverdue(TaskStatus.OPEN, boundary.minusNanos(1))).isFalse();
      assertThat(deadline.isOverdue(TaskStatus.OPEN, boundary)).isTrue();
      assertThat(deadline.isOverdue(TaskStatus.OPEN, boundary.plusNanos(1))).isTrue();
    }
    assertThat(
            java.time.Duration.between(
                    LocalDate.of(2026, 3, 29).atStartOfDay(ZoneId.of(BERLIN)).toInstant(),
                    LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of(BERLIN)).toInstant())
                .toHours())
        .isEqualTo(23);
    assertThat(
            java.time.Duration.between(
                    LocalDate.of(2026, 10, 25).atStartOfDay(ZoneId.of(BERLIN)).toInstant(),
                    LocalDate.of(2026, 10, 26).atStartOfDay(ZoneId.of(BERLIN)).toInstant())
                .toHours())
        .isEqualTo(25);
  }

  @Test
  void midnightGapUsesFirstValidLocalTime() {
    Deadline deadline = Deadline.date("2018-11-03", "America/Sao_Paulo");
    Instant boundary = Instant.parse("2018-11-04T03:00:00Z");
    assertThat(deadline.isOverdue(TaskStatus.OPEN, boundary.minusNanos(1))).isFalse();
    assertThat(deadline.isOverdue(TaskStatus.OPEN, boundary)).isTrue();
  }

  @Test
  void noneAndTerminalStatesNeverBecomeOverdueAndInstantIsStrict() {
    Instant instant = Instant.parse("2026-01-01T12:00:00Z");
    Deadline dateTime = Deadline.dateTime(instant);
    assertThat(dateTime.isOverdue(TaskStatus.OPEN, instant.minusNanos(1))).isFalse();
    assertThat(dateTime.isOverdue(TaskStatus.OPEN, instant)).isFalse();
    assertThat(dateTime.isOverdue(TaskStatus.OPEN, instant.plusNanos(1))).isTrue();
    for (TaskStatus status : TaskStatus.values()) {
      assertThat(Deadline.none().isOverdue(status, instant)).isFalse();
      if (status != TaskStatus.OPEN) {
        assertThat(dateTime.isOverdue(status, instant.plusSeconds(1))).isFalse();
        assertThat(Deadline.date("2025-12-31", BERLIN).isOverdue(status, instant)).isFalse();
      }
    }
    assertThatThrownBy(() -> dateTime.isOverdue(null, instant))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> dateTime.isOverdue(TaskStatus.OPEN, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resolvesGapAndOverlapOnlyWhenUnambiguous() {
    LocalDateTime gap = LocalDateTime.of(2026, 3, 29, 2, 30);
    LocalDateTime overlap = LocalDateTime.of(2026, 10, 25, 2, 30);
    assertThatThrownBy(() -> Deadline.fromLocalDateTime(gap, BERLIN, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Deadline.fromLocalDateTime(overlap, BERLIN, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Deadline.fromLocalDateTime(overlap, BERLIN, Optional.of(Deadline.Occurrence.FIRST)))
        .isEqualTo(Deadline.dateTime(Instant.parse("2026-10-25T00:30:00Z")));
    assertThat(Deadline.fromLocalDateTime(overlap, BERLIN, Optional.of(Deadline.Occurrence.SECOND)))
        .isEqualTo(Deadline.dateTime(Instant.parse("2026-10-25T01:30:00Z")));
    LocalDateTime ordinary = LocalDateTime.of(2026, 1, 1, 12, 0);
    assertThat(
            Deadline.fromLocalDateTime(ordinary, BERLIN, Optional.of(Deadline.Occurrence.SECOND)))
        .isEqualTo(Deadline.fromLocalDateTime(ordinary, BERLIN, Optional.empty()));
    assertThatThrownBy(() -> Deadline.fromLocalDateTime(ordinary, BERLIN, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void calendarDateUsesOriginalDateOrHouseholdProjection() {
    assertThat(Deadline.none().calendarDate(BERLIN)).isEmpty();
    assertThat(Deadline.date("2026-01-01", BERLIN).calendarDate("America/Los_Angeles"))
        .contains(LocalDate.of(2026, 1, 1));
    Deadline deadline = Deadline.dateTime(Instant.parse("2026-01-01T00:30:00Z"));
    assertThat(deadline.calendarDate(BERLIN)).contains(LocalDate.of(2026, 1, 1));
    assertThat(deadline.calendarDate("America/Los_Angeles")).contains(LocalDate.of(2025, 12, 31));
    for (Deadline value :
        new Deadline[] {Deadline.none(), Deadline.date("2026-01-01", BERLIN), deadline}) {
      assertThatThrownBy(() -> value.calendarDate("+01:00"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsMalformedInputsAndConstructorsCannotBypassValidation() {
    assertThatThrownBy(() -> Deadline.date("2026-02-30", BERLIN))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> Deadline.date("2026-1-01", BERLIN))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> Deadline.date("2026-01-01", "Mars/Olympus"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Deadline.Date(LocalDate.now(), ZoneOffset.UTC))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Deadline.date((LocalDate) null, BERLIN))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Deadline.date("2026-01-01", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Deadline.dateTime(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Deadline.fromLocalDateTime(null, BERLIN, Optional.empty()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Deadline.fromLocalDateTime(LocalDateTime.MIN, "bad", Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dateUpperBoundaryIsValidatedByEveryCreationPath() {
    assertThatThrownBy(() -> Deadline.date(LocalDate.MAX, BERLIN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Deadline.date(LocalDate.MAX.toString(), BERLIN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Deadline.Date(LocalDate.MAX, ZoneId.of(BERLIN)))
        .isInstanceOf(IllegalArgumentException.class);
    Deadline last = Deadline.date(LocalDate.MAX.minusDays(1), BERLIN);
    Instant boundary = LocalDate.MAX.atStartOfDay(ZoneId.of(BERLIN)).toInstant();
    assertThat(last.isOverdue(TaskStatus.OPEN, boundary.minusNanos(1))).isFalse();
    assertThat(last.isOverdue(TaskStatus.OPEN, boundary)).isTrue();
  }

  @Test
  void instantBoundsProtectCalendarProjectionAcrossAllOffsets() {
    for (Instant bound : new Instant[] {MIN, MAX}) {
      assertThat(Deadline.dateTime(bound)).isEqualTo(new Deadline.DateTime(bound));
      assertThat(Deadline.dateTime(bound).calendarDate("Pacific/Kiritimati")).isPresent();
      assertThat(Deadline.dateTime(bound).calendarDate("Etc/GMT+12")).isPresent();
      assertThat(bound.atOffset(ZoneOffset.ofHours(18)).toLocalDate()).isNotNull();
      assertThat(bound.atOffset(ZoneOffset.ofHours(-18)).toLocalDate()).isNotNull();
    }
    for (Instant outside :
        new Instant[] {MIN.minusNanos(1), MAX.plusNanos(1), Instant.MIN, Instant.MAX}) {
      assertThatThrownBy(() -> Deadline.dateTime(outside))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new Deadline.DateTime(outside))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(
            () -> Deadline.fromLocalDateTime(LocalDateTime.MIN, "Etc/UTC", Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Deadline.fromLocalDateTime(LocalDateTime.MAX, "Etc/UTC", Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @ResourceLock("java.util.TimeZone.default")
  void hostDefaultZoneDoesNotChangeMeaning() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
      Deadline first = Deadline.date("2026-03-29", BERLIN);
      Instant boundary = Instant.parse("2026-03-29T22:00:00Z");
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
      Deadline second = Deadline.date("2026-03-29", BERLIN);
      assertThat(first).isEqualTo(second);
      assertThat(first.isOverdue(TaskStatus.OPEN, boundary)).isTrue();
      assertThat(second.isOverdue(TaskStatus.OPEN, boundary)).isTrue();
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
