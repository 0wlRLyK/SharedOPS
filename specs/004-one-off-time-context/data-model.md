# Data Model

## Deadline

`Deadline` has exactly one of `None`, `Date(LocalDate dueDate, ZoneId householdZone)` and `DateTime(Instant instant)`. Dates and instants are non-null and date zones must be named IDs accepted by `ZoneId.getAvailableZoneIds()`, matching household creation. Local input parses strictly; invalid dates and zones are rejected. Date-only `LocalDate.MAX` is outside the supported range because its next calendar day cannot be represented; constructors reject it, while `LocalDate.MAX.minusDays(1)` remains valid. A `DateTime` accepts only the inclusive projection-safe instant interval defined in the [deadline contract](contracts/deadline.md); constructors and both creation paths enforce it, while the clock instant remains unrestricted. This ensures projection to every accepted household zone is representable. DateTime factory from `LocalDateTime` and a validated household zone inspects valid offsets: zero means gap/reject; one means unambiguous; two require `FIRST` or `SECOND` selection, each mapping to its respective instant. An invalid selection is rejected. A directly supplied `Instant` is already an absolute instant.

No persisted task deadline is introduced. On a `Date`, the next local calendar day start (including zone-rule adjustment for a nonexistent midnight) determines overdue. For `DateTime`, now must be strictly later than the instant. Open is the existing `TaskStatus.OPEN`; `COMPLETED`, `CANCELED`, and `SKIPPED` are terminal and never overdue. A calculation does not mutate task state. Calendar date is the original date for `Date`, or the instant projected into household zone for `DateTime`; `None` has no date.

## Current context projection

Account: UUID and current stored email. Household: UUID, name, nullable icon, currency and timezone. A server-authenticated account has zero or one active membership; inactive memberships do not contribute. A scoped left join yields the account with null household when no active membership exists, otherwise the matching household. No client-selectable account/household ID enters the use case. Account absence in the store must not return another account or a fabricated response; treat it through the existing authentication failure mechanism.

This slice does not change account, membership, household or task schema, lifecycle or write semantics. Existing uniqueness and foreign keys remain authoritative.
