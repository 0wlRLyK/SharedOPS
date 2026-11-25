# Research: One-off task time and current context

## Time representation

**Decision**: Use separate immutable deadline variants: none; original `LocalDate` plus validated household `ZoneId`; and absolute `Instant`. Use the existing `Clock` JDK abstraction in the application service and pass explicit `Instant` to the domain, as ADR-0007 requires. Reject a date-only `LocalDate.MAX` at construction because the next local calendar date is unrepresentable; all earlier dates remain valid. Compute next date with `plusDays(1).atStartOfDay(zone)`, and compare the clock instant. Use zone rules' valid offsets to reject a gap and select first/second offset for overlap. Require date-time instants to be within the inclusive interval in the [deadline contract](contracts/deadline.md), inset 18 hours from `LocalDate` limits, so calendar projection is defined for every supported zone and invalid extremes fail at construction.

**Rationale**: D01 distinguishes calendar and instant semantics; Java zone rules handle short, long and midnight-transition days. Existing `ClockConfiguration` supplies UTC `Clock`, while a fixed clock proves boundaries. Household creation already validates names against `ZoneId.getAvailableZoneIds()`; use equivalent accepted-zone rule in this independent task domain rather than depending on household application internals.

**Alternatives considered**: UTC-only storage loses date-only meaning; adding 24 hours fails DST; automatic overlap resolution silently changes intent; system/default clock or zone makes behavior environment dependent. All conflict with approved behavior.

## Current-context query

**Decision**: An application port reads a projection with current account email and optionally its household via a single query joining active membership constrained by authenticated account ID. Expose response via existing household auth controller, protected by default security chain. Use a route-specific no-store mechanism that executes for both security rejection and controller outcomes; retain Problem Details.

**Rationale**: Existing `CurrentAccount` returns authenticated UUID; principal email may be stale. One query scopes the projection and avoids a membership check followed by a separate unscoped household read that could observe a changed membership. Database's active-membership uniqueness supports at most one row.

**Alternatives considered**: Read email from login principal (not guaranteed current); accept household ID from client (unnecessary and unsafe); compose separate membership and household reads (membership can change between reads); add cross-module export or new schema (not needed).

## Delivery and verification

**Decision**: Treat time as domain-only and auth/me as additive public API under full workflow, with no migration or new dependency. Use `make verify` on the final candidate and focused domain and PostgreSQL/HTTP coverage. Maintain both source drafts as durable inputs in the feature deliverable.

**Alternatives considered**: Task API or schema in this slice would enlarge HO-81 and require new behavior; unit-only auth tests cannot establish actual production-migration and security behavior. Neither meets the scoped goals.
