# Tasks — Cross-household access denial

**Status** Draft, all tasks pending

**Scope** HO-93 validation of HO-10 AC-14 only

**Input** [Specification](spec.md) · [Plan](plan.md) · [Research](research.md) · [Contract](contracts/ac-14.md) · [Data model](data-model.md) · [Quickstart](quickstart.md)

The following tasks describe a future validation run. The representative operations and exact assertions depend on D1–D3. Resolve analysis findings and obtain explicit owner approval of the spec and plan before editing test or implementation files. The proposed integration path uses the current checkout package and HO-10 test name. The file does not yet exist and its location is subject to D1. Record and escalate unavailable operations or behavior gaps for owner disposition within the approved scope.

## Phase 1 — Setup

- [ ] T001 Record the owner-reviewed D1–D3 dispositions in `specs/ho-10-household-isolation/research.md` and reconcile the selected resource, entry points, denial comparator, committed-state footprint and expected B mutation delta in `specs/ho-10-household-isolation/spec.md`, `plan.md`, `contracts/ac-14.md`, `data-model.md` and `quickstart.md`.

## Phase 2 — Foundational

- [ ] T002 Confirm operation availability and the API and migration impact against `docs/api/openapi.yml` and `specs/ho-10-household-isolation/quickstart.md`. Record analysis disposition, checklist review and explicit owner approval of the spec and plan before proceeding. Record and escalate any blocker.
- [ ] T003 Establish synthetic fixtures for the approved operations in `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java` if D1 confirms this location. Use two distinct households, distinct authenticated active members, a committed B resource and a valid absent identifier of the same form. Use PostgreSQL with production Flyway migrations and isolate each mutation's fixture state.

## Phase 3 — User Story 1, protect B's resource and preserve authorized B access (P1)

**Independent validation** In an isolated fixture, run both actor controls using the selected operations and compare the inaccessible and absent cases. Observe committed data after each mutation.

- [ ] T004 [US1] Cover S1, FR-001 and SC-001 in `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java`. Exercise the selected foreign read through HTTP and its real application use case. Assert denial and zero B data disclosure.
- [ ] T005 [US1] Cover S2, FR-001, FR-003, SC-001 and SC-003 in `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java`. Exercise the selected otherwise valid foreign mutation through HTTP and its real application use case. For each invocation, assert denial and zero B data disclosure, and compare the committed target and applicable related data to the baseline using a fresh persistence context or connection after transaction completion.
- [ ] T006 [US1] Cover S3, FR-002, FR-003, SC-002 and SC-003 in `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java`. For the read and mutation, compare the HTTP denial for the existing B identifier and the same-form absent identifier using the approved D2 assertions and equivalent valid inputs. For the absent-ID mutation, assert that all data and side effects identified in D3 remain unchanged after transaction completion.
- [ ] T007 [US1] Cover S4, FR-004 and SC-004 in `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java`. As the permitted active B actor, assert the approved successful read returns the expected resource and the valid mutation produces the expected change in separately observed committed state.

## Phase 4 — Verification and handoff

- [ ] T008 Run the focused integration test and applicable repository checks documented in `specs/ho-10-household-isolation/quickstart.md`. Record the commands, tested revision and results, with any failures, skips, blockers or unrun mandatory checks.
- [ ] T009 Record the AC-14/NFR-06 and T30 trace, environment and sanitized expected-versus-actual observations for each scenario in the execution record associated with `specs/ho-10-household-isolation/quickstart.md`. Include the committed observations, API and migration impact review and local diff review. Record solo self-review when applicable and leave publication to the owner.
- [ ] T010 In an isolated worktree, establish a passing baseline for the relevant authorization test in `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java`. Temporarily remove the membership check and confirm the intended authorization assertion fails. Restore the code, rerun the test and record all three outcomes. Keep the deliberate defect out of commits.
- [ ] T011 If an operation lacks the required authorization boundary, record the blocker in `specs/ho-10-household-isolation/research.md`. Implementation requires explicit owner approval of the complete HO-10 specification and plan covering that boundary.
- [ ] T012 After implementation and verification, run `$speckit-converge` against `specs/ho-10-household-isolation/spec.md`, `plan.md` and `tasks.md`. Reconcile the diff and evidence against approved AC-14 scenarios, address remaining tasks and record the outcome before owner handoff.

## Dependencies & Execution Order

T001 → T002 → T003 → T004 and T005 → T006. Apply T011 if needed and obtain approval of the complete HO-10 scope before implementing the boundary. T007 relies on T003 and the approved expected change. T010 follows T004 and T005. T008 follows all scenario tasks and T010. T009 follows T008, and T012 precedes owner handoff.

**Parallel opportunities** The story tasks share one candidate integration file. Sequence edits to that file. Read-only review of the documented HTTP contract and the committed-state evidence can proceed concurrently after D1 identifies the candidate operations.

## Implementation Strategy

The minimum slice contains all four scenarios in US1, including the authorized B control. Validate the narrow approved pair through the real boundaries. Document any behavior that needs a separately scoped change before the slice can produce successful verification evidence.
