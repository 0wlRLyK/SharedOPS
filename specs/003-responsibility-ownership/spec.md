# Feature Specification: Individual Responsibility Ownership and Lifecycle

**Feature Branch**: `feat/HO-12-responsibility-ownership`

**Created**: 2026-11-23

**Status**: Draft

**Input**: [HO-12 — Implement responsibility ownership and lifecycle](https://linear.app/home-0ps/issue/HO-12/implement-responsibility-ownership-and-lifecycle)

## Clarifications

### Session 2026-11-23

- Q: How should a repeated lifecycle request be handled when the responsibility is already in the requested state? → A: Reject it when the supplied version is current; return `409 Conflict` for a stale version. Owner decision: `repeat-lifecycle`.

## User Scenarios & Testing

### User Story 1 - Create and find individual responsibilities (Priority: P1)

An active household member creates an individual responsibility with a title, optional description and an active owner in their household, and finds it among their household's current responsibilities.

**Why this priority**: A clearly owned responsibility is the foundation for managing household work.

**Independent Test**: Create an individual responsibility with a valid owner; retrieve it and compare its details and owner, then attempt creation or access with an invalid owner or another household.

**Acceptance Scenarios**:

1. **Given** an active household member and an active member of that household, **When** the user creates an individual responsibility with a valid title and the selected owner, **Then** it is active with initial version `0`, has exactly that owner, and appears in the household's current responsibilities.
2. **Given** a missing, empty or whitespace-only title, **When** a user creates or edits a responsibility, **Then** the request is rejected without changing stored data. A description may be absent or empty.
3. **Given** an absent, inactive or foreign-household proposed owner, **When** a user creates a responsibility with that owner, **Then** the request is rejected and no responsibility is created.
4. **Given** a responsibility in another household or an unknown responsibility, **When** a user attempts to read or modify it, **Then** the request is denied without disclosing which case applies and no other household's data changes.
5. **Given** an unauthenticated user or a user lacking an active household membership, **When** they request household responsibilities, **Then** access is denied and no data is disclosed or modified.

### User Story 2 - Maintain details and transfer ownership (Priority: P1)

A household member updates a responsibility's title or description and explicitly transfers its ownership when household arrangements change.

**Why this priority**: Keeping responsibility details and ownership current makes the assignment useful over time.

**Independent Test**: Edit the details and transfer ownership with the current version; inspect the new details, owner, transfer facts, and any pre-existing task assignments. Repeat with stale versions and invalid owners.

**Acceptance Scenarios**:

1. **Given** a current responsibility and its latest version, **When** an authorized member changes its title or description, **Then** the new details and a newer version are available to the household.
2. **Given** a current individual responsibility and another active member of the same household, **When** an authorized member explicitly transfers ownership using the current version, **Then** the new owner takes effect without recipient acceptance and existing tasks keep their prior assignments.
3. **Given** a successful explicit transfer to a different owner, **When** its outcome is committed, **Then** the actor, time and owner change facts are committed with the transfer; if recording those facts fails, neither the transfer nor its facts are committed.
4. **Given** an invalid or inactive proposed owner, **When** ownership is transferred, **Then** the request is rejected and the responsibility, its transfer facts and existing task assignments remain unchanged.
5. **Given** the current version and unchanged details or the current owner as the transfer target, **When** an authorized member submits the request, **Then** it succeeds as a no-op without changing the version or producing transfer facts. A stale request still returns `409 Conflict`.
6. **Given** two requests based on the same version, **When** one successfully changes the responsibility and the other attempts an update, **Then** the latter receives a `409 Conflict`, preserves the winning changes, and causes no task reassignment or transfer facts.

### User Story 3 - Pause, archive and restore work (Priority: P2)

A household member can pause and resume a responsibility, archive it when no longer current, and restore it when it becomes relevant again.

**Why this priority**: Household work changes without becoming a completed object or losing history.

**Independent Test**: Pause and resume an active responsibility, archive it, inspect the separate lists, and restore it with valid ownership, including a historical inactive-owner case.

**Acceptance Scenarios**:

1. **Given** an active responsibility, **When** an authorized member pauses and later resumes it using current versions, **Then** its status changes to paused and back to active while it remains in the current, non-archived view.
2. **Given** an active or paused responsibility, **When** it is archived with the current version, **Then** it leaves the current view and appears in the archived view, retaining its historical owner and existing linked work.
3. **Given** an archived responsibility whose historical owner remains active in the same household, **When** an authorized member restores it with the current version and omits the owner input, **Then** it becomes active with that historical owner.
4. **Given** an archived responsibility with an inactive historical owner, **When** an authorized member restores it with a selected active same-household owner and the current version, **Then** it becomes active with that owner. An absent, inactive or foreign replacement is rejected and it remains archived.
5. **Given** a stale version, **When** an authorized member attempts pause, resume, archive or restore, **Then** the request returns `409 Conflict` and leaves the newer state, owner and existing task assignments unchanged; a stale restore does not reopen it.
6. **Given** any responsibility, **When** a user requests a completed state, **Then** the request is rejected and the responsibility remains unchanged.
7. **Given** an archived responsibility, **When** a user tries to edit its details or transfer its owner without restoring it, **Then** the request is rejected and the archived record remains unchanged.
8. **Given** a responsibility already in the requested state, **When** a user repeats that lifecycle request using its current version, **Then** it is rejected without mutation; a stale version still returns `409 Conflict`.

### Edge Cases

- An archived responsibility may retain an inactive historical owner; an active or paused responsibility may not have an inactive or foreign owner. Restore validates ownership at the time of transition, including concurrent membership changes. Archived details and ownership cannot be edited; restore is the only permitted mutation.
- Creation, edits, transfers and lifecycle transitions must not accept SHARED mode or switch between modes in M0.
- Invalid, missing or stale versions for mutations of existing responsibilities and transitions outside the supported lifecycle must not overwrite newer state or create a transfer record. Stale requests use the required conflict response.
- When ownership transfer and another mutation race on the same version, at most one state-changing mutation succeeds; a valid no-op may also succeed without advancing the version. Only a successful state-changing transfer produces its corresponding committed change facts.
- Archiving or restoring does not reassign tasks or reopen completed tasks. Full recurrence and linked-list behavior belongs to later work.

## Requirements

### Functional Requirements

- **FR-001**: An authenticated active household member MUST be able to create an `INDIVIDUAL` responsibility within their household with a nonblank title, an optional description and one selected owner. Null, empty and whitespace-only titles MUST be rejected; descriptions MAY be absent or empty. The initial state is `ACTIVE`. [FR-RESP-01, FR-RESP-02]
- **FR-002**: Every `ACTIVE` or `PAUSED` individual responsibility MUST have exactly one owner who is an active member of the same household. Creation and explicit transfer MUST reject a missing, inactive or foreign proposed owner. Restore MUST reject a missing, inactive or foreign effective owner: when the owner input is omitted it uses the historical owner if still active in the same household; otherwise a valid replacement is required. An archived responsibility MAY retain its historical owner even when that owner is no longer active. [FR-RESP-02, FR-RESP-06, FR-RESP-10, NFR-08]
- **FR-003**: An active household member MUST be able to view a responsibility within their household and update the title and description of an `ACTIVE` or `PAUSED` responsibility. The same title and description rules apply to edits. Unchanged details submitted with the current version succeed without changing the version. An `ARCHIVED` responsibility is read-only except for an authorized restore. [FR-RESP-04, FR-RESP-07, FR-RESP-10]
- **FR-004**: An active household member MUST be able to explicitly transfer an active or paused individual responsibility to a valid owner without recipient acceptance. A request to transfer to the existing owner with the current version succeeds without changing the version or recording transfer facts. The transfer MUST NOT reassign existing tasks. For a transfer to a different owner, the actor, time, previous owner and new owner MUST be recorded atomically with the version-checked change; a failure MUST leave both ownership and transfer facts unchanged. [FR-RESP-06, NFR-07, NFR-08]
- **FR-005**: An authorized household member MUST be able to pause an active responsibility, resume a paused responsibility, and archive an active or paused responsibility. A responsibility MUST NOT transition to a completed state. Transitions outside those listed and restore, including repeated same-state requests, MUST be rejected without mutation. [FR-RESP-07]
- **FR-006**: An authorized member MUST be able to restore an archived responsibility to `ACTIVE`, subject to a valid active same-household owner. If its historical owner is inactive, the member MUST select a valid replacement. A failed restore MUST NOT reopen it or reassign tasks. [FR-RESP-10]
- **FR-007**: The system MUST offer separate household-scoped current and archived responsibility queries. The current view includes `ACTIVE` and `PAUSED` responsibilities and excludes `ARCHIVED`; the archived view includes only `ARCHIVED` responsibilities. [FR-RESP-08]
- **FR-008**: Creation MUST initialize the responsibility version to `0` without requiring a caller-supplied version. Each mutation of an existing responsibility, including requests that would make no change, MUST check the version supplied by the caller against the current version atomically with its changes. A stale version takes precedence over no-op or lifecycle-state handling. A stale mutation MUST return `409 Conflict` and MUST NOT overwrite newer changes or produce side effects, including transfer facts or task reassignment. [NFR-07, AC-15]
- **FR-009**: Household access MUST derive from authenticated identity and active membership and be checked before reads or writes. Foreign and nonexistent responsibilities MUST be indistinguishable to an unauthorized caller; neither may expose data or change it. [NFR-06, AC-14]
- **FR-010**: M0 commands MUST reject creation of `SHARED` responsibilities and requests to switch modes, preserving all existing state. Shared responsibilities and mode switching belong to M1 HO-92. [FR-RESP-03, FR-RESP-05]
- **FR-011**: Archiving or restoring a responsibility MUST preserve existing linked work and MUST NOT reassign or reopen tasks. Account deletion and its complete deletion-to-restore workflow are owned by HO-85 and decision D08. [FR-RESP-07, FR-RESP-10]

### Key Entities

- **Responsibility**: A household-scoped ongoing area of work with a title, description, individual mode, lifecycle status, one owner and a version used to detect concurrent edits.
- **Household member**: A user whose active membership provides access to the household and eligibility to own current responsibilities.
- **Ownership transfer facts**: The actor, time, and previous and new owners for an explicitly transferred responsibility.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In acceptance scenarios, every successful creation, transfer and restoration of a current responsibility has exactly one active owner in the same household; every invalid-owner attempt leaves the stored responsibility unchanged.
- **SC-002**: In all exercised stale mutation scenarios, the user receives `409 Conflict`, the winning version remains intact, and no rejected transfer, restoration or task reassignment is recorded.
- **SC-003**: All exercised household-isolation attempts disclose no foreign responsibility data and commit no changes to it.
- **SC-004**: All exercised lifecycle scenarios place active and paused responsibilities in the current view and archived responsibilities only in the archived view; none creates a completed responsibility.
- **SC-005**: Every exercised successful explicit transfer to a different owner records the actor, time and owner change together, and every injected failure to record those facts leaves ownership unchanged. A successful same-owner no-op records no transfer facts.

## Assumptions

- Existing authentication and household membership capabilities establish actor identity and active same-household membership. Any active member may manage household responsibilities; no owner-only restriction is stated in the issue.
- The title is required and nonblank; description is optional. No product-specific numeric length limits are stated in the issue or repository requirements. Those storage and transport constraints, if needed, are design details to document in the plan and public contract rather than additional product rules. No-op edits and same-owner transfers succeed without a new version or audit fact; repeated lifecycle transitions are rejected. These defaults retain the required stale-version check and may be reconsidered during clarification.
- FR-RESP-03 and FR-RESP-05 are traced as M1 exclusions, and FR-RESP-09 linked-task/list/expense aggregation is excluded from this M0 slice. Recurring generation and its pause/archive behavior are M1 work (D02 and HO-29).
- D08 and HO-85 own deletion, historical identity protection and the real deletion-to-restore journey. HO-12 must accept the supported historical-owner condition and enforce the restore invariant, without implementing deletion.
- Existing task assignment is preserved; this feature does not implement task creation, reassignment, or recurrence.
