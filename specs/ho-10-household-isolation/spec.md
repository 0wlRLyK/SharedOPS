# Feature Specification — Cross-household access denial

**Status** Draft, pending owner review

**Created** 2026-11-17

**Scope** HO-93 validation of exactly one criterion, HO-10 AC-14

**Worktree** Detached HEAD at `a7b09141fdcaef14b2eff064cb21f66b911ea9ed`

**Sources** [HO-93](https://linear.app/home-0ps/issue/HO-93/establish-agentic-development-and-spec-driven-delivery-foundation), [HO-10](https://linear.app/home-0ps/issue/HO-10/implement-household-creation-and-data-isolation), [AC-14 and NFR-06](../../docs/requirements.md), [ADR-0008](../../docs/adr/0008-rest-api-contract-and-problem-details.md)

An authenticated actor has active membership in household A and lacks access to household B. The actor attempts to read or mutate a resource owned by B. The slice validates denial, concealment of existence and preservation of committed data. A permitted B actor provides the positive control. Other HO-10 acceptance criteria, other product criteria and new product behavior are outside this draft's scope.

## Clarifications

### Session 2026-11-17

The preparatory run preserves these open decisions for owner review. No questions were asked or answered in this run.

- **D1 — Representative operations.** Select the B resource and one read and one mutation that exercise real application boundaries. Confirm the applicable resource identifier inputs and how operation readiness affects validation.
- **D2 — Existence comparison.** Apply the same public denial to inaccessible and absent resources. The operation-specific response fields, any permitted request-specific differences, and the comparison rules remain open.
- **D3 — Committed-state evidence.** Define the target fields and any related committed effects to compare for the selected mutation, including the expected committed change for the authorized B control.

## User Scenarios & Testing

### User Story 1 — Protect B's resource and preserve authorized B access (P1)

As a member of household B, I can use my household's resource through operations I am permitted to perform, while an actor from household A cannot see or change it.

**Why this priority** Household isolation protects B's data and its continued use by authorized members.

**Independent Test** Use two synthetic households, two authenticated actors with distinct active memberships, an existing B resource and a valid identifier known to be absent. Exercise each selected operation with independent fixture state so a successful B mutation cannot affect a denial assertion.

**Acceptance Scenarios**

1. **Read denial (S1).** Given a committed B resource, when the A actor requests a read of its identifier, the system denies access and returns no B data.
2. **Mutation denial (S2).** Given a committed B resource, when the A actor submits an otherwise valid mutation targeting it, the system denies access. After the request finishes, a fresh committed-state observation shows no change to the target or any committed data the mutation could affect.
3. **Existence concealment (S3).** Given the same actor and otherwise equivalent inputs, when each selected read or mutation is attempted against the existing B resource and a valid absent identifier, the observable denial gives the caller no way to distinguish the outcomes by resource existence.
4. **Authorized B access (S4).** Given an active B actor permitted to perform the selected operations, when that actor reads the fixture, the expected resource is returned. When the actor submits a valid selected mutation, that mutation succeeds and the expected change is visible in committed state.

### Edge Cases

- The absent identifier has the same valid form as the existing identifier. Invalid identifiers, malformed inputs and unauthenticated requests are outside this comparison.
- The denied mutation uses an otherwise valid input that would produce a visible change if performed by the permitted B actor.

## Requirements

### Functional Requirements

- **FR-001** — The system MUST derive household access from authenticated identity and active membership at the application boundary and deny an A actor's read or mutation of a B resource before side effects. A denied read MUST return no B data.
- **FR-002** — For each selected operation, the system MUST present equivalent externally observable denials for an existing inaccessible resource and a valid absent resource without revealing B data or existence.
- **FR-003** — Each rejected A mutation MUST leave the target and any committed data the operation could affect unchanged.
- **FR-004** — The permitted active B actor MUST be able to read the expected resource and perform the selected valid mutation, with its intended effect visible in committed state.

### Key Entities

- **Households A and B** are distinct tenants.
- **A actor and B actor** have distinct authenticated identities and active memberships. The A actor has no access to B. The B actor has permission for the selected operations.
- **B resource** is owned by B and identifiable for the selected operations. The absent comparator is a valid identifier with no matching resource.

## Success Criteria

### Measurable Outcomes

- **SC-001** — Every selected cross-household read and mutation is denied and discloses zero B resource data.
- **SC-002** — Every existing-inaccessible versus absent pair has zero observable differences attributable to resource existence.
- **SC-003** — Each denied mutation produces zero differences in the committed data it could affect.
- **SC-004** — All selected authorized B controls succeed. The read returns the expected resource and the mutation produces the expected committed change.

## Assumptions

- Use synthetic, isolated fixtures with stable active memberships and no unrelated writes during committed-state comparison.
- Selection of the representative resource, read and mutation, and the operation-specific response and committed-data assertions are deferred pending review. This draft establishes the outcomes those selections must prove.
