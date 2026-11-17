# Security Requirements Checklist — Cross-household access denial

**Status** Draft

**Scope** HO-93 validation of HO-10 AC-14 only

**Purpose** Review the wording and evidence requirements before approval of the spec and plan.

**Created** 2026-11-17

**Feature** [Specification](../spec.md) · [Plan](../plan.md)

**Review ownership** The reviewer marks `[x]` after determining the requirements-quality criterion is satisfied. Each item begins unchecked.

## Completeness and clarity

- [ ] CHK001 Are the actor identities, active memberships, resource ownership and permissions distinct and explicit? [Clarity, Spec §Key Entities]
- [ ] CHK002 Are the denial requirements explicit for both reads and otherwise valid mutations at the authorization boundary? [Completeness, Spec §FR-001]
- [ ] CHK003 Is the valid absent comparator defined clearly enough to exclude denials unrelated to resource existence? [Clarity, Spec §Edge Cases and §FR-002]
- [ ] CHK004 Can a reviewer identify the concrete operations, applicable identifier inputs and availability evidence required to approve the slice? [Dependency, Gap, Spec §Clarifications D1]

## Consistency and measurability

- [ ] CHK005 Are the observable denial requirements consistent with the planned OpenAPI outcome and ADR-0008? [Consistency, Spec §FR-002, Contract §S1–S3]
- [ ] CHK006 Are the response elements and any permitted incidental differences defined precisely enough to assess existence concealment? [Measurability, Gap, Spec §SC-002 and §Clarifications D2]
- [ ] CHK007 Is the boundary for observing committed outcomes defined for each denied write, with the target and any affected related data in scope? [Measurability, Spec §FR-003 and §SC-003, Data Model §State transitions]
- [ ] CHK008 Are the exact data to compare and the expected positive change identified for the selected mutation? [Measurability, Gap, Spec §Clarifications D3]

## Scenario coverage and review gates

- [ ] CHK009 Do the acceptance scenarios distinguish the negative read, rejected write, absent comparator and authorized B control? [Coverage, Spec §Acceptance Scenarios]
- [ ] CHK010 Is the authorized B read and mutation described with independent, observable outcomes? [Coverage, Spec §FR-004 and §SC-004]
- [ ] CHK011 Is the scope limited to AC-14, with pending decisions and owner approval distinguished from draft traceability and future verification evidence? [Coverage, Spec §Clarifications, Plan §Constitution Check]

## Notes

- Leave a marker unchecked where reviewer evaluation, correction or clarification is pending.
- The implementation skill reads checklist state as a gate. Markers belong to the reviewer.
- The [built-in specification checklist](requirements.md) follows its own lifecycle.
