# Requirements Quality Checklist: Single-use household invitations

**Purpose**: Assess completeness, clarity, consistency and verifiability of the invitation lifecycle and its security and concurrency boundaries.
**Created**: 2026-11-22
**Feature**: [Specification](../spec.md) | [Plan](../plan.md) | [Contract](../contracts/invitations.md)

**Review Ownership**: This custom checklist is a reviewer-owned requirements-quality artifact. Only the reviewer marks an item `[x]` after finding it satisfied; a checked item does not claim implementation is complete.

## Lifecycle and boundaries

- [x] CHK001 Are authorized invitation creation and eligible acceptance requirements defined with observable outcomes? [Completeness, Spec §FR-001, §FR-003]
- [x] CHK002 Are expiry duration, starting instant and exact deadline boundary unambiguously stated? [Clarity, Spec §FR-002, User Story 2]
- [x] CHK003 Are invalid, expired, revoked and consumed invitation outcomes and their state effects specified consistently? [Coverage, Spec §FR-005, User Stories 1–2]
- [x] CHK004 Are the explicit revocation authority, terminal outcome and account-deletion workflow boundary stated? [Completeness, Spec §FR-006–007, Assumptions]
- [x] CHK005 Is the successful acceptance outcome and failure atomicity objectively defined for both membership and invitation state? [Measurability, Spec §FR-004, User Story 1]
- [x] CHK006 Are full-household, already-member and invalid-input cases covered with observable rejected-state requirements? [Coverage, Spec §FR-005, §FR-008–009, User Stories 1–3]

## Concurrency and state invariants

- [x] CHK007 Are per-household capacity and per-account membership limits quantified for concurrent operations? [Clarity, Spec §FR-008–009]
- [x] CHK008 Are final-slot, same-invitation and creation-versus-acceptance races specified with outcomes for winners and losers? [Coverage, Spec User Story 3, Plan §Verification and AC Trace]
- [x] CHK009 Are capacity rejection and concurrent successful consumption requirements mutually consistent? [Consistency, Spec §FR-008, User Story 3]
- [x] CHK010 Are revocation and expiry races defined with a determinable ordering and no partial effects? [Clarity, Spec §Edge Cases, Contract §Invitation interface contract]
- [x] CHK011 Does the verification design cover expiry occurring while acceptance waits, including the exact boundary and committed state? [Coverage, Plan §Verification and AC Trace, Data model §Concurrency states]
- [x] CHK012 Are failed transactions, uniqueness conflicts and resulting persisted-state requirements specified for each competing path? [Completeness, Spec §FR-004, §FR-009, Plan §Design and Integration]

## Security and interface

- [x] CHK013 Are identity, active-membership authorization and enforcement at the application boundary specified for protected operations? [Completeness, Spec §FR-007, §FR-011, Contract]
- [x] CHK014 Are inaccessible-versus-absent resource and invalid-invitation disclosure requirements consistent across the plan and contract? [Consistency, Spec §FR-007, Plan §Design and Integration, Contract]
- [x] CHK015 Are secret generation, storage, one-time disclosure and prohibited logging or URL exposure requirements specified? [Completeness, Spec §FR-002, §FR-010, Plan §Design and Integration]
- [x] CHK016 Are the request, success, error, authentication and CSRF requirements defined for each proposed operation? [Completeness, Contract §Invitation interface contract]
- [x] CHK017 Is the proposed HTTP contract's relationship to authoritative OpenAPI and existing clients explicitly stated? [Consistency, Spec §Assumptions, Plan §Compatibility and Migration, Contract]

## Migration, evidence and scope

- [x] CHK018 Are existing-data preconditions, deterministic backfill and invalid-data failure behavior documented without silent data repair? [Completeness, Plan §Compatibility and Migration, Data model §Membership and household]
- [x] CHK019 Are active and inactive slot constraints and the two-member invariant stated unambiguously, including the null case? [Clarity, Data model §Membership and household]
- [x] CHK020 Are mixed-version compatibility, coordinated deployment and rollback requirements explicit? [Completeness, Plan §Compatibility and Migration]
- [x] CHK021 Are PostgreSQL production-migration, real-operation, controlled concurrency and committed-state evidence requirements traceable to the acceptance criteria? [Traceability, Spec §FR-012, Plan §Verification and AC Trace]
- [x] CHK022 Are implementation exclusions and external dependencies identified, including delivery, account deletion and financial stages? [Scope, Spec §Assumptions, Plan §Technical Context]

## Notes

- `$speckit-implement` reads checklist state but must not modify its markers.
- Evaluate each criterion against the linked source artifacts; unresolved requirement defects remain unchecked and return to the relevant owner.
- `checklists/requirements.md` is the separate built-in specification-quality checklist with its own lifecycle.
