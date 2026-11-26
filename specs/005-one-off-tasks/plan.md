# Implementation Plan: One-off tasks and assignment defaults

**Branch**: `feat/HO-18-one-off-tasks-and-assigments-defaults` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

## Summary

Add household-scoped one-off create, read and partial-edit operations, using the existing deadline value and injected clock. Serialize authorization, membership validation and responsibility snapshot with household-first locking, persist the resolved assignee, and use version-checked updates. The exact HTTP and cross-module contract, data migration and evidence are defined in the accompanying artifacts. HO-19 terminal actions, checklist, recurrence, shared responsibilities and browser flows remain excluded.

## Technical Context

**Language/Version**: Java 25. **Dependencies**: existing Spring Boot 4.1.1, Spring Modulith 2.1.1, JDBC/Flyway; no new dependency. **Storage**: PostgreSQL. **Testing**: JUnit, MockMvc and real PostgreSQL with production migrations/Testcontainers. **Platform**: existing backend service. No new throughput target or scale claim. Preserve module boundaries, authenticated household scope, explicit time and committed state. `make verify` is required; Docker access needs the approved elevated environment.

## Constitution Check

Before and after design: PASS contingent on implementation and tests. Traceability: spec maps FR-TASK-01–04/11, D01/D04 and HO-18. Modules: task uses explicit exported interfaces, never responsibility or household persistence; domain remains framework-independent. Isolation: application transaction obtains authorized locked household before references or writes. Verification: PostgreSQL constraints, transactions and races; exact deadline boundaries and status; non-disclosing HTTP failures; architecture tests. Compatibility: additive API; V10 preflight blocks unrepresentable legacy rows instead of fabricating data. Owner approval of this exact plan and its linked artifacts is required before code changes.

## Project Structure

```text
specs/005-one-off-tasks/{spec.md,plan.md,research.md,data-model.md,quickstart.md,contracts/}
backend/src/main/java/dev/sharedops/task/{domain,application,api,infrastructure/persistence}/
backend/src/main/java/dev/sharedops/household/{contract,application,infrastructure/persistence}/
backend/src/main/java/dev/sharedops/responsibility/{contract,application,infrastructure/persistence}/
backend/src/main/resources/db/migration/V10__one_off_tasks.sql
backend/src/test/java/dev/sharedops/{task,architecture}/
docs/api/openapi.yml
```

Use narrow exported contracts and the established JDBC transaction patterns; update existing checklist persistence fixtures and TaskEntity mappings for mandatory fields while preserving checklist cascade and orphan removal behavior. Shared household and responsibility contract implementations, migrations, and OpenAPI are serially owned shared resources. Implement behavior plus its regression tests in isolated batch tasks; defer execution to final verification as required by the workflow.

## Design and sequencing

1. Establish household task access and responsibility snapshot contracts, preserving household → responsibility → task row lock order. Expose active membership resolution and household zone only under the authorized, transaction-scoped locked view. The responsibility export locks and returns only same-household active individual context, including owner membership; it does not authorize independently or expose persistence. Document task's permitted `household::task` dependency in module declaration and architecture docs, preserving existing contracts. Exercise races before integration.
2. Evolve task storage with guarded V10 and transaction-safe domain/application handling; use explicit deadline variant fields and membership snapshots. Create and edit use the same validation in domain/application, not only HTTP. Household locking coordinates with responsibility lifecycle and membership mutations; versioned task update is checked atomically. Reject context state and foreign references uniformly. Add atomic edit facts under D04.
3. Add the HTTP contract in [contracts/task-api.md](contracts/task-api.md), implement strict parsing preserving absent versus explicit null, and update authoritative OpenAPI and contract tests. Read returns current persisted values and derived overdue. Establish error mappings and test unauthorized, foreign and missing resources without disclosing existence. No list, claim, cancellation or completion endpoint is added.
4. Verify positive and negative paths and record per-criterion evidence. Task design will map exact file ownership, interfaces, dependencies and tests; final integration is the sole place for task checkbox updates, hooks, formatting, full tests and independent review.

## Verification and AC mapping

| Requirement                    | Evidence to implement and execute                                                                                                                                                                                           |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-001, 003, 009 / HO-18 AC1,6 | HTTP and PostgreSQL: title-only -> OPEN, NONE, NORMAL, unassigned, version 0; both active partners; invalid and null option combinations produce no row.                                                                    |
| FR-002, 005, 006 / AC2,3,5,6   | PostgreSQL HTTP/application tests: default current owner, explicit partner/unassigned, lifecycle rejections; indistinguishable foreign/missing, inactive member; transfer retains prior snapshots. Inspect committed state. |
| FR-004 / edit and NFR-07       | partial edit/clear, stale version and terminal rejection, two concurrent updates exactly one succeeds, no partial state, audit fact atomicity; test both actor memberships and direct application authorization.            |
| FR-007,008 / AC4               | existing DeadlineTest and DeadlineEvaluatorTest plus persisted round-trip/API tests for NONE, DATE and DATE_TIME at exact boundaries, DST gaps/overlaps, zone independence and terminal states.                             |
| Concurrency and invariants     | coordinated PostgreSQL races: create vs ownership transfer/pause and membership deactivation; only a serialized valid result commits; same-household FKs, migration guards and rollback asserted after commit.              |
| Public contract / architecture | OpenAPI schema tests, validation/error parity, existing clients unchanged, Modulith/ArchUnit; `make verify` on final exact tree, per-class reports and scope-selected receipts.                                             |

## Complexity Tracking

No constitution exception. The two narrow cross-module contracts and audit record are needed to enforce lock and D04 invariants without direct repository access; a separate replicated responsibility snapshot or host-time conversion would introduce divergence.
