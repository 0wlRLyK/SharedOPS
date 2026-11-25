# ADR-0007: Time Source and Domain Time Handling

Date: 24.10.2026
Status: Accepted
**Scope:** SharedOPS backend

## Context

Time-dependent operations are required for task deadlines, overdue calculations, completion timestamps, and future scheduling functionality.
Direct access to the system clock from domain logic would make behavior dependent on execution time and complicate deterministic testing.
A consistent approach to obtaining and supplying the current time is therefore required.

## Decision

The standard Java `java.time.Clock` abstraction is selected as the source of current time in SharedOPS.

- Current time is obtained by application services through an injected `Clock`.
- Time-dependent domain operations receive explicit temporal values, such as `Instant`, rather than obtaining the current time independently.
- `Clock.systemUTC()` is provided through Spring configuration in production.
- `Clock.fixed(...)` is used where deterministic time-dependent tests are required.
- A custom `ClockPort` interface is not introduced unless additional domain-specific time-source requirements justify it.
- Household-local date interpretation remains governed by D01 and the household's IANA time zone.

## Alternatives Considered

**Direct use of Instant.now()**
Rejected because system time would become an implicit dependency of domain operations, making deterministic testing more difficult.

**Custom ClockPort abstraction**
Deferred because the existing JDK abstraction provides the currently required functionality without an additional interface and adapter.

## Consequences

**Positive**

- Time-dependent behavior can be tested deterministically.
- Domain logic remains independent of Spring and system-clock configuration.
- Production and test environments can use different time sources without changing business logic.

**Trade-offs**

- Clock dependencies must be supplied explicitly to application services.
- Spring configuration must ensure an unambiguous `Clock` dependency.
- A custom abstraction may become necessary if requirements evolve.
- This ADR establishes a time-source contract; each time-dependent use case needs its own behavior and verification.

## Delivery Scope and Verification

### HO-003 — Time-handling contract decision

The project adopts `java.time.Clock` as its framework-independent time-source contract. A custom clock port is not introduced.

Application services will obtain the current time from an injected `Clock`. Domain operations that require time will receive explicit temporal values, such as `Instant`, as method arguments.
At that stage, domain primitives contained no time-dependent behavior. HO-003 recorded the contract choice and usage rules; it did not introduce a clock consumer, adapter, or Spring configuration.
This decision refines the "clock port" requirement in HO-003: the existing JDK abstraction is adopted instead of a project-defined interface. The issue description must reflect this scope clarification.

### HO-004 — Runtime integration

Provide the production `Clock` bean using `Clock.systemUTC()` and inject it through application-service constructors.
Verify that a test-specific `Clock` can replace the production clock without missing or ambiguous dependencies.

**Status:** Planned; implementation and verification are tracked in HO-004.

### HO-081 — Time-dependent behavior

HO-81 adds an internal, framework-independent one-off `Deadline` domain contract and a `DeadlineEvaluator` application service. The evaluator obtains an `Instant` from its injected `Clock` and passes it explicitly to the domain. The domain does not read the system clock or the host's default time zone.

- `NONE` has no calendar date and is never overdue. `DATE` retains the original calendar date and the household's validated named IANA zone. An open task is overdue at or after the start of the following local calendar day, using zone rules rather than adding 24 hours. `DATE_TIME` stores a validated absolute instant; an open task is overdue strictly after that instant. Terminal tasks are never overdue, and evaluation does not change status.
- Calendar projection preserves the original `DATE` and derives a `DATE_TIME` local date in the supplied household zone. Explicit local times in DST gaps are rejected; overlaps require the first or second occurrence. Invalid dates and zones are rejected.
- A date-only `LocalDate.MAX` is rejected because its next day is not representable. Date-time instants are restricted to an inclusive range inset by 18 hours from the `LocalDate` limits at UTC, so projection remains representable for every valid zone offset. These limits apply to deadlines, not the clock's current instant.

`DeadlineTest` and `DeadlineEvaluatorTest` cover the deadline variants, controlled-clock boundaries, DST transitions, zone independence and representability limits. Their execution and final verification are recorded with HO-81's delivery evidence. This internal domain contract does not itself deliver task persistence, a task API, recurrence endpoints or templates, or a background generator; remaining task and recurrence scope stays with its owning issues.

## Revisit Trigger

The decision is reconsidered if additional clock behavior, independently controlled time sources, or new architectural constraints cannot be adequately represented by `java.time.Clock`.
