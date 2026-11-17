# Implementation Plan — Cross-household access denial

**Status** Draft, pending owner approval

**Scope** HO-93 validation of HO-10 AC-14 only

**Date** 2026-11-17

**Worktree** Detached HEAD at `a7b09141fdcaef14b2eff064cb21f66b911ea9ed`

**Input** [Specification and issue links](spec.md)

## Summary

Validate one representative B resource read and mutation through the applicable HTTP and real application-use-case boundaries. Compare inaccessible and absent outcomes at HTTP and observe committed state after denied and authorized mutations. The positive B read and mutation guard against a system that denies everyone or a write fixture that cannot produce a committed change.

## Technical Context

- **Stack** Java 25, Spring Boot, Maven, PostgreSQL, production Flyway migrations and the existing Testcontainers test stack. See the [pinned stack](../../docs/stack.md) and [test strategy](../../docs/test-strategy.md).
- **Scope and constraints** Use synthetic fixtures in an isolated backend test environment. Apply the existing documented module boundaries and review the public contract and persistence impact. Separate the fresh committed-state read from the operation transaction and avoid test-managed rollback as proof.
- **Open** D1 selects the concrete resource, read and mutation, identifier inputs and readiness. D2 defines the observable response comparison. D3 identifies the committed-state assertions, including applicable related records and the authorized expected delta. See [research](research.md).
- **Scope controls** No new performance target, schema change, test-only production route or broader HO-10 coverage is proposed. Raise any discovered scope or contract change for owner disposition.

## Constitution Check

| Principle                                    | Draft disposition                                                                                                                                                                                                      |
| -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| I. Traceable Changes                         | HO-93, HO-10 AC-14, NFR-06, ADR-0008 and T30 are linked. Planned checks and future evidence trace to S1–S4 and FR-001–FR-004.                                                                                          |
| II. Module Boundaries and Compatibility      | Use real use cases and documented exports. OpenAPI remains authoritative and describes planned behavior explicitly. Review API and migration impact before execution.                                                  |
| III. Household Isolation and Data Protection | Enforce identity and active membership at the application boundary. Compare foreign and absent denial, assert committed outcomes and use only synthetic data and sanitized evidence.                                   |
| IV. Verification Evidence                    | Exercise relevant HTTP and application paths and real PostgreSQL with production migrations. Record actual commands, revision, environment, results and committed evidence. Missing mandatory evidence prevents merge. |
| V. Bounded Agent Execution                   | The detached worktree is the review context. Scope ends at preparation. The owner decides D1–D3, reviews the local diff and controls publication. Record solo self-review where applicable.                            |

The pre-design and post-design reviews found the draft consistent with these principles. The implementation gate is pending resolution of D1–D3, analysis disposition, and explicit owner approval of the specification and plan. Design artifacts record validation intent. Runtime evidence is pending.

## Project Structure

- **Feature documents** [Research](research.md), [Data model](data-model.md), [Contract notes](contracts/ac-14.md), and [Quickstart](quickstart.md).
- **Existing boundaries** [Module architecture](../../docs/architecture.md), `backend/src/main/java/dev/sharedops/household/` in the current checkout, and the application boundary for the operation selected in D1.
- **Future test location** `backend/src/test/java/dev/sharedops/security/HouseholdIsolationIT.java` is a proposed location based on the current package and HO-10 test name. HO-10 still lists the older com/homeops path, and this file does not yet exist. Its use and operation-specific placement remain subject to D1 and owner review. This preparatory run makes no production changes.
- **Future evidence** The draft [quickstart](quickstart.md) lists the required execution record, including required checks and failure or blockage disposition.

## Phases

1. **Phase 0 — Research.** Reconcile the baselined AC and HTTP contract with the documented runtime readiness and carry through D1–D3.
2. **Phase 1 — Design.** Describe the minimum fixture, contract boundary, committed-state evidence and validation sequence.
3. **After approval — Validate.** Confirm real entry points, cover S1–S4 using the approved operation and evidence selections, run the applicable repository checks, and record their actual results. Record a blocker if an approved operation is unavailable or a mandatory check cannot run.

After implementation approval, validate test sensitivity in an isolated worktree by recording a passing baseline, temporarily removing the membership check, observing the intended authorization assertion fail, restoring the code and recording a passing rerun. Never commit the intentional defect. Run `$speckit-converge` after implementation and verification, resolve remaining work, and record its outcome before owner handoff.
