# Feature Specification: One-off tasks and assignment defaults

**Feature Branch**: `feat/HO-18-one-off-tasks-and-assigments-defaults`

**Created**: 2026-11-25

**Status**: Draft

**Input**: [HO-18](https://linear.app/home-0ps/issue/HO-18/implement-one-off-tasks-and-assignment-defaults); FR-TASK-01–04, FR-TASK-11; accepted decision D01. AC-02 and AC-03 are related M1 criteria, not acceptance of recurrence or shared responsibilities in this M0 slice.

## Clarifications

### Session 2026-11-25

- Q: What priority does a new task have when priority is omitted? → A: `NORMAL`.

## User Scenarios & Testing

### User Story 1 - Create a standalone task (Priority: P1)

An active household member records a one-off task without needing a responsibility or an assignee, or assigns it to an active member of the household.

**Why this priority**: A simple task is the smallest useful coordination unit.

**Independent Test**: Create tasks with only a title, and with valid optional details and each allowed assignment, then retrieve their recorded values.

**Acceptance Scenarios**:

1. **Given** an active household member, **When** they create a standalone task with a valid title as the only task field, **Then** creation succeeds with no responsibility, no assignee, and `NORMAL` priority.
2. **Given** an active household member, **When** they create a standalone task assigned to either active member of their household, **Then** the selected member is recorded as assignee.
3. **Given** an active household member, **When** they supply valid optional description, deadline, priority, or assignment, **Then** the task retains those values and exposes its initial open state and version.
4. **Given** a missing or invalid title, invalid optional field, inactive member, or assignee from another household, **When** creation is requested, **Then** it is rejected without creating a task or disclosing foreign household data.

### User Story 2 - Create a task in a responsibility (Priority: P1)

A household member records a one-off task under an active individual responsibility. Unless an assignment is explicitly chosen, the owner at creation becomes its assignee.

**Why this priority**: The default makes responsibility ownership useful while allowing a deliberate exception.

**Independent Test**: Create linked tasks using the default, either active partner, and an explicit unassigned choice; transfer responsibility ownership afterward and inspect the tasks.

**Acceptance Scenarios**:

1. **Given** an active individual responsibility owned by member A, **When** a member creates a linked task without an assignment choice, **Then** A is recorded as its assignee at creation.
2. **Given** that responsibility, **When** the creator explicitly chooses either active household member or unassigned, **Then** the task retains that explicit choice rather than the owner default.
3. **Given** linked tasks already created, **When** responsibility ownership later changes, **Then** their recorded assignments do not change silently; a subsequent task with no assignment choice uses the new current owner.
4. **Given** a paused, archived, inaccessible, nonexistent, or foreign responsibility, **When** a linked task is created, **Then** creation is rejected without creating a task or revealing foreign resource existence.

### User Story 3 - Edit task details and understand overdue state (Priority: P2)

A household member updates an open task's title, description, deadline, priority, or assignment with version protection and can see whether it is overdue without changing its status.

**Why this priority**: Task details evolve, and a deadline should communicate urgency without changing the task's lifecycle.

**Independent Test**: Edit a task with its current and stale versions, and evaluate deadline boundaries at controlled times for open and terminal tasks.

**Acceptance Scenarios**:

1. **Given** an accessible open task and its current version, **When** either active household member supplies valid edits to its title, description, deadline, priority, or assignment, **Then** the changes and a new version are recorded and other unchanged details remain intact.
2. **Given** an open task with a description, deadline, and assignee, **When** a member explicitly clears the description, selects no deadline, or chooses unassigned, **Then** the corresponding optional value is removed; an omitted field remains unchanged. A title must remain valid, and priority must remain one of the supported values.
3. **Given** an outdated version, **When** an edit is requested, **Then** the edit is rejected as a conflict and the newer recorded task remains unchanged.
4. **Given** an edit containing invalid details or an inactive or foreign assignee, **When** it is requested, **Then** it is rejected without partial changes or disclosure of foreign data.
5. **Given** an open task with a date-only deadline, **When** the next household-local calendar day begins, **Then** overdue is true; immediately before it, overdue is false. For a date-time deadline, overdue becomes true strictly after its instant.
6. **Given** a task with no deadline or a terminal task, **When** overdue is evaluated, **Then** it is false. Evaluating overdue never changes the stored task status.

### Edge Cases

- Explicit unassigned and an omitted assignment choice are distinct for creation in an individual responsibility.
- A client cannot select a responsibility or assignee outside its active household, even if its identifier is known. Rejection leaves committed state unchanged and does not reveal whether a foreign object exists.
- A member who is no longer active cannot be selected as assignee. A concurrent ownership or membership change must not cause a newly created task to use an invalid owner or cross-household assignment.
- Date-only boundaries use the household's next local calendar day, including daylight-saving transitions, and not a fixed 24-hour increment. Explicit local times in gaps are invalid; overlaps require a valid occurrence choice under D01.
- A stale edit, invalid combination of optional fields, or rejection of a responsibility context must not partially update a task. Terminal tasks are read-only under D04.

## Requirements

### Functional Requirements

- **FR-001**: An authenticated active household member MUST be able to create a standalone one-off task with a valid title as the sole required task field. A standalone task defaults to unassigned and may instead explicitly name an active member of the same household. [FR-TASK-01; HO-18 AC 1 and 6]
- **FR-002**: An authenticated active household member MUST be able to create a one-off task linked to an active individual responsibility in that household. An omitted assignment choice defaults to the responsibility's current owner at creation; an explicit active household member or unassigned choice overrides it. [FR-TASK-02; HO-18 AC 2 and 6]
- **FR-003**: Each task MUST retain title, optional description, deadline, priority, assignment, lifecycle status and version. Priority supports `LOW`, `NORMAL`, and `HIGH`; creation defaults to `NORMAL` when priority is omitted. Creation and editing MUST validate supplied values. [FR-TASK-03, FR-TASK-04; HO-18 AC 6; owner decision priority-default]
- **FR-004**: Either active household member MUST be able to edit an open task's title, description, deadline, priority and assignment using the current version. Explicitly clearing the optional description, selecting `NONE` for the deadline, or choosing unassigned removes the corresponding value; an omitted field remains unchanged. The title remains valid and priority remains a supported value. Terminal tasks are read-only. A stale version MUST result in a conflict without overwriting newer changes; a successful edit MUST advance the version. [HO-18; NFR-07; D04]
- **FR-005**: The system MUST derive household access from authenticated identity and active membership, validate responsibility and assignee references within that household, and reject foreign, inaccessible, nonexistent or inactive selections without revealing foreign resource existence or committing partial changes. [HO-18 AC 3; constitution III]
- **FR-006**: Creation MUST reject a paused or archived responsibility. The ownership and assignment decision at creation MUST be consistent with an active individual responsibility and active same-household assignee. Later responsibility owner changes MUST NOT automatically change existing task assignments. [HO-18 AC 2, 5 and 6]
- **FR-007**: Deadline meaning MUST follow accepted D01 and the HO-81 one-off deadline foundation: `NONE`, household-local `DATE`, and validated absolute `DATE_TIME`. For open tasks, a `DATE` becomes overdue at the next local calendar-day start and a `DATE_TIME` strictly after its instant. A task with no deadline or in a terminal state is never overdue. Overdue MUST be derived using controlled current time and MUST NOT be stored as a task status. [FR-TASK-03, FR-TASK-11; HO-18 AC 4; D01]
- **FR-008**: Creation and editing MUST reject invalid date or time values, invalid zones, local times in daylight-saving gaps, and ambiguous overlap times without a valid occurrence choice. Household and host/device timezone differences MUST NOT change the intended deadline. [D01; HO-81]
- **FR-009**: The creation interface MUST accept a title as the only required task field, return the created task's details and version, and distinguish omitted assignment from explicit unassigned in a linked context. [HO-18 AC 6]

### Key Entities

- **Task**: A one-off household item with title, optional description, deadline, priority, status, assignment, version, and optional responsibility link.
- **Responsibility**: An individual household context with an owner and lifecycle state; its owner supplies the default for a new linked task.
- **Household membership**: The active relationship that authorizes a person to act in a household and be selected as assignee.
- **Deadline**: No deadline, a household-local calendar date, or a validated instant, whose interpretation is governed by D01.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Every specified standalone and linked creation scenario records the requested assignment or correct owner default, including an explicit unassigned selection.
- **SC-002**: Every specified invalid, foreign, inactive, paused, archived and stale-version scenario is rejected with no unintended committed task creation or changes and no foreign resource disclosure.
- **SC-003**: Every specified exact deadline boundary, terminal and no-deadline example produces the expected derived overdue result while preserving status.
- **SC-004**: After ownership transfer, all previously created linked tasks retain their assignments, while a new defaulted task records the current owner.

## Assumptions

- HO-12 provides individual responsibility ownership and lifecycle; HO-81 supplies the one-off deadline semantics. Their existing behavior is reused.
- The present slice creates and edits one-off tasks. Checklist items, take/claim, completion and cancellation are owned by their respective issues; browser form acceptance belongs to HO-83.
- M1 shared responsibility defaults, recurrence, scheduler, and Today or All views in AC-02 and AC-03 are outside this slice. Their criteria remain open for their designated M1 work.
- Clearing an optional description is treated as an available edit; the wire representation for clearing versus omission is a plan decision. Selecting `NONE` and unassigned follows D01 and HO-18.
- The exact public representations, validation bounds, and migration compatibility are design matters for the plan, subject to the authoritative OpenAPI contract and owner approval. No unspecified behavior is implied by this draft.
