# Requirements Quality Checklist: One-off time and current context

**Purpose**: Review completeness, clarity, consistency and acceptance quality for deadline semantics and current-context security.
**Created**: 2026-11-25
**Feature**: [spec.md](../spec.md)

**Review Ownership**: This is a reviewer-owned requirements-quality artifact; only a reviewer may mark an item satisfied.
**Marker Semantics**: `[x]` means the requirements-quality criterion has been reviewed and satisfied; it does not mean implementation is complete.

## Requirement Completeness

- [x] CHK001 Are all deadline variants and their required data explicitly distinguished? [Completeness, Spec FR-001]
- [x] CHK002 Are calendar-filter meanings specified for both dated variants and the undated variant? [Completeness, Spec FR-005, Deadline contract]
- [x] CHK003 Are authenticated, unauthenticated, active, inactive and absent-membership outcomes specified? [Completeness, Spec US3, FR-006–008]
- [x] CHK004 Are response fields, nullability, serialization and source of current values specified? [Completeness, Spec US3, Auth/me contract]
- [x] CHK005 Are the supported boundary inputs and rejection outcomes defined for representability limits? [Completeness, Deadline contract]

## Clarity and Measurability

- [x] CHK006 Are date and instant overdue thresholds distinguishable at equality and measurable against controlled time? [Clarity, Spec FR-003, US1]
- [x] CHK007 Are overlap occurrence selection and gap rejection specified without relying on defaults? [Clarity, Spec FR-002, Deadline contract]
- [x] CHK008 Are date-only and instant calendar dates specified independently of host timezone? [Clarity, Spec FR-004–005]
- [x] CHK009 Is the current account and household selection authority unambiguous and independent of client-supplied identifiers? [Clarity, Spec FR-006–008, Auth/me contract]
- [x] CHK010 Are error status, error contract, confidentiality and cache requirements objectively stated? [Measurability, Spec FR-007]

## Consistency and Dependencies

- [x] CHK011 Do exact boundary semantics align with D01 and the explicit-time domain boundary in ADR-0007? [Consistency, Spec FR-003–004, Deadline contract]
- [x] CHK012 Are the date and instant representability rules consistent across every declared construction path? [Consistency, Deadline contract]
- [x] CHK013 Are the current-context projection and HTTP response requirements consistent with membership and household assumptions? [Consistency, Spec US3, Auth/me contract]
- [x] CHK014 Are existing timezone and active-membership foundations, compatibility expectations and migration boundaries documented? [Dependency, Spec Assumptions, Plan]
- [x] CHK015 Are the requested auth/me source requirements and the HO-81 scope both traceable without implying completion of broader task features? [Traceability, Spec Input, Assumptions]

## Scenario and Edge Coverage

- [x] CHK016 Are no deadline, open and terminal task scenarios and status preservation specified? [Coverage, Spec FR-003, US1]
- [x] CHK017 Are short/long DST days, midnight gaps, invalid dates and zones, and both overlap occurrences addressed? [Edge Case, Spec US1–2, Deadline contract]
- [x] CHK018 Are local-date rollover and maximum/minimum projection boundaries addressed? [Edge Case, Spec FR-005, Deadline contract]
- [x] CHK019 Are inactive or absent membership, foreign household, nullable icon and missing account paths addressed? [Exception Flow, Spec US3, Auth/me contract]
- [x] CHK020 Are success and authentication-failure response confidentiality and cache behavior addressed consistently? [Security, Spec FR-007–008]

## Scope and Acceptance

- [x] CHK021 Are recurrence, task creation APIs, membership mutation and background generation explicitly excluded? [Scope, Spec FR-010, Assumptions]
- [x] CHK022 Can every functional requirement be evaluated through a stated acceptance scenario or documented contract? [Acceptance Criteria, Spec FR-001–010]
- [x] CHK023 Are security and data protection requirements scoped to authenticated identity and active membership? [Security, Spec FR-006–008]
- [x] CHK024 Are performance targets and recovery or rollback requirements intentionally scoped rather than implied for this read-only and domain-only change? [Assumption, Spec Assumptions, Plan Technical Context]

## Notes

- Independent review: CHK001–024 are satisfied by the cited approved spec, plan and contracts; checked markers assess requirements quality only. The deadline contract specifies NONE as an empty calendar result and the missing-account path is an authentication failure. Read-only/domain-only scope requires no mutation rollback; no numeric performance target is set.
- `$speckit-implement` reads checklist state but does not modify markers.
- `checklists/requirements.md` follows the separate built-in specification-quality lifecycle.
