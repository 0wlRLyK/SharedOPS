package dev.sharedops.task.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A one-off deadline whose meaning is independent of the host time zone. */
public sealed interface Deadline permits Deadline.None, Deadline.Date, Deadline.DateTime {
  Instant MIN_PROJECTABLE =
      LocalDate.MIN.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(18 * 3600L);
  Instant MAX_PROJECTABLE =
      LocalDate.MAX.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).minusSeconds(18 * 3600L);

  record None() implements Deadline {}

  record Date(LocalDate dueDate, ZoneId householdZone) implements Deadline {
    public Date {
      Objects.requireNonNull(dueDate, "dueDate");
      validateZone(Objects.requireNonNull(householdZone, "householdZone").getId());
      if (dueDate.equals(LocalDate.MAX)) {
        throw new IllegalArgumentException("Date has no representable next day");
      }
    }
  }

  record DateTime(Instant instant) implements Deadline {
    public DateTime {
      Objects.requireNonNull(instant, "instant");
      if (instant.isBefore(MIN_PROJECTABLE) || instant.isAfter(MAX_PROJECTABLE)) {
        throw new IllegalArgumentException("Instant is outside the projectable range");
      }
    }
  }

  enum Occurrence {
    FIRST,
    SECOND
  }

  static Deadline none() {
    return new None();
  }

  static Deadline date(LocalDate dueDate, String householdZoneId) {
    return new Date(dueDate, validateZone(householdZoneId));
  }

  static Deadline date(String isoDate, String householdZoneId) {
    return date(LocalDate.parse(Objects.requireNonNull(isoDate, "isoDate")), householdZoneId);
  }

  static Deadline dateTime(Instant instant) {
    return new DateTime(instant);
  }

  static Deadline fromLocalDateTime(
      LocalDateTime local, String householdZoneId, Optional<Occurrence> occurrence) {
    Objects.requireNonNull(local, "local");
    Objects.requireNonNull(occurrence, "occurrence");
    ZoneId zone = validateZone(householdZoneId);
    List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
    if (offsets.isEmpty()) {
      throw new IllegalArgumentException("Local time falls in a time zone gap");
    }
    if (offsets.size() > 1 && occurrence.isEmpty()) {
      throw new IllegalArgumentException("An overlap occurrence is required");
    }
    int index =
        offsets.size() == 1 || occurrence.orElse(Occurrence.FIRST) == Occurrence.FIRST ? 0 : 1;
    return new DateTime(local.toInstant(offsets.get(index)));
  }

  default Optional<LocalDate> calendarDate(String householdZoneId) {
    ZoneId zone = validateZone(householdZoneId);
    return switch (this) {
      case None ignored -> Optional.empty();
      case Date date -> Optional.of(date.dueDate());
      case DateTime dateTime -> Optional.of(dateTime.instant().atZone(zone).toLocalDate());
    };
  }

  default boolean isOverdue(TaskStatus status, Instant now) {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(now, "now");
    if (status != TaskStatus.OPEN) {
      return false;
    }
    return switch (this) {
      case None ignored -> false;
      case Date date ->
          !now.isBefore(date.dueDate().plusDays(1).atStartOfDay(date.householdZone()).toInstant());
      case DateTime dateTime -> now.isAfter(dateTime.instant());
    };
  }

  private static ZoneId validateZone(String zoneId) {
    Objects.requireNonNull(zoneId, "householdZoneId");
    if (!ZoneId.getAvailableZoneIds().contains(zoneId)) {
      throw new IllegalArgumentException("Invalid household time zone");
    }
    return ZoneId.of(zoneId);
  }
}
