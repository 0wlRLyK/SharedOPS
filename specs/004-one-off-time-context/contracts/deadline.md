# Internal Deadline Contract

Package `dev.sharedops.task.domain`; this is not a public HTTP or persistence contract. Proposed exact API for task design and review:

```java
public sealed interface Deadline permits Deadline.None, Deadline.Date, Deadline.DateTime {
  record None() implements Deadline {}
  record Date(LocalDate dueDate, ZoneId householdZone) implements Deadline {}
  record DateTime(Instant instant) implements Deadline {}
  enum Occurrence { FIRST, SECOND }
  static Deadline none();
  static Deadline date(LocalDate dueDate, String householdZoneId);
  static Deadline date(String isoDate, String householdZoneId);
  static Deadline dateTime(Instant instant);
  static Deadline fromLocalDateTime(LocalDateTime local, String householdZoneId, Optional<Occurrence> occurrence);
  Optional<LocalDate> calendarDate(String householdZoneId);
  boolean isOverdue(TaskStatus status, Instant now);
}
```

Constructors validate non-null invariants and date zone as a named IANA ID accepted by `ZoneId.getAvailableZoneIds()`; public constructors cannot bypass validation. A date-only deadline of `LocalDate.MAX` is rejected at construction because its next calendar day cannot be represented; `LocalDate.MAX.minusDays(1)` remains valid. Both date factories and the public `Date` constructor enforce this same rule, so overdue calculation never fails later from date overflow. A `DateTime` instant must lie in the inclusive projection-safe range `[LocalDate.MIN.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(18 * 3600), LocalDate.MAX.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).minusSeconds(18 * 3600)]`. Both `dateTime(Instant)`, `fromLocalDateTime` after offset resolution, and the public `DateTime` constructor enforce that same range; reject `Instant.MIN`, `Instant.MAX`, or any instant outside it at construction. Java zone offsets are bounded by ±18 hours, so every instant in this range can be projected to a `LocalDate` in every accepted household IANA zone without overflow. This applies to date-time deadlines, not the clock's `now` supplied to `isOverdue`.

`date` and `fromLocalDateTime` reject invalid zone IDs. `fromLocalDateTime` rejects gaps and ambiguous overlaps without a valid explicit occurrence; `FIRST` maps to the earlier offset in the rules' valid-offset order and `SECOND` to the later one. For an unambiguous local time use its sole valid offset regardless of whether a valid occurrence was supplied. `calendarDate` validates the supplied household zone for every variant; `Date` returns original date regardless of that zone, `DateTime` projects its instant there, and `None` is empty. `isOverdue` requires non-null status/instant, returns false for none or any terminal status; for OPEN, date is overdue at or after `dueDate.plusDays(1).atStartOfDay(householdZone).toInstant()`, and date-time strictly after its instant. Neither uses a clock zone or host default. A `DeadlineEvaluator` application service receives an injected `Clock` and calls `deadline.isOverdue(status, clock.instant())`, preserving ADR-0007’s explicit-time domain boundary. The string factory uses strict ISO date parsing and rejects malformed or invalid calendar dates; it does not expose a new endpoint.

Verify each inclusive safe-instant endpoint and the adjacent ±1 nanosecond outside it through both the factory and public constructor; test `fromLocalDateTime` also enforces the range. Project both endpoints using accepted named zones `Pacific/Kiritimati` and `Etc/GMT+12`, and verify offset-bound arithmetic for ±18 hours. Verify `Instant.MIN`/`Instant.MAX` are rejected. Verify `LocalDate.MAX` is rejected through both factories and the public constructor, and `LocalDate.MAX.minusDays(1)` is accepted and its overdue calculation completes without overflow. Verify exact boundary instants, DST gap/overlap, both clock zone/default zone variations, NONE and each terminal state, date-time local-date rollover, and zones with midnight gaps. No recurrence generation, endpoints or schema changes.
