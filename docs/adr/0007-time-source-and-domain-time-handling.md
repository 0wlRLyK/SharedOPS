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
- No runtime time-handling behavior is delivered by this ADR alone. Its correctness will be verified when clock integration and time-dependent use cases are implemented.

## Delivery Scope and Verification

### HO-003 — Time-handling contract decision

The project adopts `java.time.Clock` as its framework-independent time-source contract. A custom clock port is not introduced.

Application services will obtain the current time from an injected `Clock`. Domain operations that require time will receive explicit temporal values, such as `Instant`, as method arguments.
The current domain primitives contain no time-dependent behavior. Therefore, HO-003 records the contract choice and usage rules;it does not introduce a clock consumer, adapter, or Spring configuration.
This decision refines the "clock port" requirement in HO-003: the existing JDK abstraction is adopted instead of a project-defined interface. The issue description must reflect this scope clarification.

### HO-004 — Runtime integration

Provide the production `Clock` bean using `Clock.systemUTC()`and inject it through application-service constructors.
Verify that a test-specific `Clock` can replace the productionclock without missing or ambiguous dependencies.

**Status:** Planned; implementation and verification are tracked in HO-004.

### HO-081 — Time-dependent behavior

Verify deadline and overdue behavior using controlled time sources,including household time-zone semantics.

**Status:** Planned; behavioral verification is tracked in HO-081.

## Revisit Trigger

The decision is reconsidered if additional clock behavior, independently controlled time sources, or new architectural constraints cannot be adequately represented by `java.time.Clock`.
