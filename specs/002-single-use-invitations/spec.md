# Feature Specification: Single-use household invitations

**Feature Branch**: `feat/HO-11-workflow`

**Created**: 2026-11-22

**Status**: Draft

**Input**: [HO-11 — Implement single-use invitations with race protection](https://linear.app/home-0ps/issue/HO-11/implement-single-use-invitations-with-race); requirements FR-HH-02–05, NFR-08 and AC-01; decision D08.

## Clarifications

### Session 2026-11-22

- Q: How long should a newly created household invitation remain valid? → A: 24 hours after creation.

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Invite and join a household (Priority: P1)

A household member creates an invitation and shares its code or link with a second person. That person accepts it and becomes an active member, while the invitation can no longer be used.

**Why this priority**: This delivers the core household invitation journey and protects the two-person limit.

**Independent Test**: Create an invitation for a household with one active member, accept it as an eligible account, and inspect both the resulting memberships and invitation state.

**Acceptance Scenarios**:

1. **Given** a household with one active member and a valid invitation, **When** an authenticated account without an active household accepts it, **Then** that account becomes an active member of the same household and the invitation is consumed. (AC-01)
2. **Given** a consumed invitation, **When** anyone tries to accept it again, **Then** acceptance is rejected and no membership or invitation state changes.
3. **Given** an account already actively belonging to a household and an invitation to another household, **When** the account attempts to accept, **Then** acceptance is rejected and its existing membership and the invitation remain unchanged.
4. **Given** an acceptance operation fails before completion, **When** its outcome is observed, **Then** neither a new membership nor consumption of the invitation is committed.

### User Story 2 - Control an unused invitation (Priority: P1)

A household member can revoke an unused invitation. An invitation is usable only until its expiry, allowing access to be withdrawn or to lapse.

**Why this priority**: A shared invitation grants access and needs a bounded lifetime and a way to withdraw it.

**Independent Test**: Create separate invitations, revoke one and let another expire, then try accepting each and inspect the resulting state.

**Acceptance Scenarios**:

1. **Given** an unused invitation, **When** an authorized household member revokes it, **Then** any later acceptance is rejected and creates no membership.
2. **Given** an invitation at or after 24 hours from its creation, **When** an account attempts acceptance, **Then** acceptance is rejected without consuming the invitation or creating membership.
3. **Given** an invitation that does not exist or a code that is invalid, **When** an account attempts acceptance, **Then** acceptance is rejected and no household or invitation state changes.
4. **Given** a person who is not an active member of the invitation's household, **When** they try to create or revoke an invitation for that household, **Then** the operation is rejected without changing that household or its invitations.

### User Story 3 - Preserve limits during simultaneous operations (Priority: P1)

When operations happen at the same time, participants can rely on a household having at most two active members and an account having at most one active household.

**Why this priority**: Simultaneous requests must not bypass the same rules that apply to a single request.

**Independent Test**: Coordinate competing acceptance attempts for a final slot, and separately coordinate creation and invitation acceptance by one account; inspect committed outcomes after both operations finish.

**Acceptance Scenarios**:

1. **Given** one remaining active-member slot and competing eligible accounts, **When** they attempt acceptance concurrently, **Then** at most one joins and the household has at most two active members; a losing attempt creates no membership and does not consume its invitation.
2. **Given** an account without an active membership and a valid invitation, **When** that account concurrently creates a household and accepts the invitation, **Then** at most one active membership is committed; the losing path leaves no orphan household and does not consume the invitation.
3. **Given** two concurrent attempts to accept the same invitation, **When** both complete, **Then** at most one succeeds and the invitation cannot be reused.
4. **Given** a household already has two active members, **When** an invitation is presented for acceptance, **Then** it is rejected without creating a membership or consuming the invitation.

### Edge Cases

- An invitation at its exact expiry time is expired and cannot be accepted.
- Revocation or expiry racing with acceptance must have a consistent outcome: an invitation cannot be accepted after revocation takes effect or after expiry, and a losing acceptance has no partial side effects.
- Acceptance cannot create duplicate active membership, including when the requester is already a member of the target household.
- A full household cannot become overfull through multiple distinct valid invitations or concurrent requests.
- Rejected requests must not reveal another household's private details or change its state.

## Requirements _(mandatory)_

### Functional Requirements

- **FR-001**: The system MUST allow an active household member to create an invitation for that household and obtain a shareable code or link. (FR-HH-02)
- **FR-002**: Every invitation MUST expire 24 hours after its creation, have a cryptographically random secret, and a stored hash rather than the plaintext secret. An invitation is expired at or after its expiry. (HO-11)
- **FR-003**: The system MUST allow an authenticated account with no active household membership to accept a valid, unused, unrevoked and unexpired invitation when its household has an available active-member slot. (FR-HH-03, AC-01)
- **FR-004**: A successful acceptance MUST create the active membership and consume the invitation as one indivisible outcome; a failed acceptance MUST commit neither effect. (NFR-08)
- **FR-005**: The system MUST reject invalid, expired, revoked and consumed invitations, and MUST leave membership and invitation state unchanged on rejection. (HO-11)
- **FR-006**: The system MUST allow an active member to revoke an unused invitation belonging to that member's household; after revocation takes effect the invitation MUST NOT be accepted. (FR-HH-05)
- **FR-007**: The system MUST reject invitation creation or revocation for a household when the requesting account is not an active member of it, with no side effects or disclosure of private household data. (NFR-06)
- **FR-008**: A household MUST have no more than two active members, including under simultaneous acceptance attempts. An acceptance rejected for lack of capacity MUST create no membership and MUST NOT consume an otherwise unused invitation as a side effect; a concurrent successful acceptance may independently consume that invitation. (FR-HH-04, NFR-08)
- **FR-009**: An account MUST have no more than one active household membership, including when household creation and invitation acceptance race. The losing path MUST leave no orphan household and MUST NOT consume the invitation. (HO-11; FR-HH-01)
- **FR-010**: The system MUST exclude invitation secrets and complete invitation URLs from logs for creation, acceptance, revocation, and failures. (HO-11)
- **FR-011**: The system MUST enforce invitation validity, membership and capacity rules at the application operation boundary, including callers outside the HTTP interface. (Constitution III)
- **FR-012**: The system MUST preserve committed-state evidence for acceptance, rejection, and concurrency outcomes using the project's production database migrations and actual application operations. (HO-11; Constitution IV)

### Key Entities _(include if feature involves data)_

- **Invitation**: A household-scoped permission to join, with a secret, expiry and lifecycle that distinguishes usable, consumed and revoked invitations.
- **Household**: The group to which an invitation grants membership; at most two active members are permitted.
- **Membership**: An account's active association with a household. An account can have at most one active association.

## Success Criteria _(mandatory)_

### Measurable Outcomes

- **SC-001**: In every successful acceptance scenario, exactly one eligible account joins the invited household and the invitation is unusable afterward.
- **SC-002**: In every tested invalid, expired, revoked, consumed, full-household and already-member scenario, no new membership is committed and the relevant invitation is unchanged by the rejected acceptance.
- **SC-003**: In each coordinated final-slot race, no more than one new active member joins and the household ends with at most two active members.
- **SC-004**: In each coordinated creation-versus-acceptance race for one account, at most one active membership exists afterward, with no orphan household or consumed invitation on the losing path.
- **SC-005**: In all tested invitation operations and failure paths, logs contain neither invitation secrets nor complete invitation URLs.

## Assumptions

- Existing authentication and household creation from HO-10 are available. Invitation holders must authenticate before joining.
- User-facing delivery of a code or link is a design detail to be specified in planning. Sharing occurs outside this feature.
- HO-11 covers explicit revocation; the real account-deletion-to-revocation and replacement journey belongs to HO-85 and is not represented as implemented here. D08 remains applicable to that later journey.
- Financial stage changes on membership composition are M2 work (FR-FIN-13, HO-92), outside this M0 invitation scope.
- The current public HTTP contract remains authoritative; any change to it needs compatibility assessment during planning.
