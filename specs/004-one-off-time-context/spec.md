# Feature Specification: One-off task time and current context

**Feature Branch**: `feat/HO-81-one-off-task-date-and-timezone-semantics`

**Created**: 2026-11-25

**Status**: Draft, pending owner review

**Input**: [HO-81](https://linear.app/home-0ps/issue/HO-81/implement-one-off-task-date-and-timezone-semantics), [auth/me specification](../auth-me/spec.md), and [pending T001](../auth-me/tasks.md). Related baselines: FR-HH-07, FR-TASK-03, FR-TASK-11, NFR-03 and accepted decision D01.

## User Scenarios & Testing

### User Story 1 - Understand a one-off deadline (Priority: P1)

A household member can interpret a task deadline consistently regardless of their device or server timezone, including on daylight-saving transitions.

**Why this priority**: Incorrect time interpretation makes a task appear overdue at the wrong time.

**Independent Test**: Evaluate deadlines around their boundaries with controlled current time and different host timezone settings.

**Acceptance Scenarios**:

1. **Given** a task with no deadline, **When** its overdue state is evaluated, **Then** it is not overdue.
2. **Given** an open task with a date-only deadline, **When** current time reaches the start of the next calendar day in the household's IANA timezone, **Then** it is overdue; immediately before that boundary it is not.
3. **Given** an open task with a date-time deadline, **When** current time equals its validated instant, **Then** it is not overdue; **When** current time is strictly later, **Then** it is overdue.
4. **Given** a terminal task and any deadline, **When** overdue is evaluated, **Then** it is never overdue and its status does not change.
5. **Given** a daylight-saving transition, **When** the next local calendar-day boundary is evaluated, **Then** it uses that calendar boundary rather than adding 24 hours.
6. **Given** the same deadline and household timezone, **When** device or host timezone changes, **Then** deadline meaning and overdue result remain unchanged.

### User Story 2 - Set and locate an unambiguous deadline (Priority: P1)

A household member can distinguish no deadline, a calendar date, and an absolute date-time; calendar views preserve the intended household-local date.

**Why this priority**: Unambiguous input and filtering keep tasks on the intended day.

**Independent Test**: Validate deadline variants, invalid input and DST transitions, then evaluate their calendar dates in a household timezone.

**Acceptance Scenarios**:

1. **Given** a deadline, **When** represented for a task, **Then** it explicitly distinguishes `NONE`, `DATE` with its original calendar date and household zone, and `DATE_TIME` with a validated instant.
2. **Given** an invalid timezone or calendar date, **When** a deadline is constructed or interpreted, **Then** it is rejected.
3. **Given** an explicit local date-time in a DST gap, **When** it is interpreted in the household zone, **Then** it is rejected.
4. **Given** an explicit local date-time in a DST overlap, **When** it is interpreted, **Then** the first or second occurrence must be explicitly selected; a missing or invalid choice is rejected.
5. **Given** a calendar filter, **When** it considers a `DATE` deadline, **Then** it uses the original due date; for `DATE_TIME`, it uses the local date of its instant in the household zone.

### User Story 3 - Retrieve current account context (Priority: P1)

An authenticated client retrieves its current account and active household context, including when the account has no active household.

**Why this priority**: A client needs its own context without exposing another household.

**Independent Test**: Request current context for distinct accounts with active, inactive and absent memberships and without authentication.

**Acceptance Scenarios**:

1. **Given** an authenticated account with an active membership, **When** it requests `GET /api/v1/auth/me`, **Then** it receives `200 OK` with its current account and active household values in the contract below.
2. **Given** an authenticated account without an active membership, including one with only an inactive membership, **When** it requests current context, **Then** it receives `200 OK` with its account and `household: null`.
3. **Given** an unauthenticated request, **When** current context is requested, **Then** it receives `401 UNAUTHORIZED` under the existing error contract and no account or household data.
4. **Given** any response from this endpoint, **When** it is returned, **Then** it includes `Cache-Control: no-store`.
5. **Given** distinct accounts and households, **When** an account requests current context, **Then** the household is derived only from that account's active membership and no other account's household data is exposed.

The successful response shape is:

```json
{
  "account": { "id": "uuid", "email": "user@example.test" },
  "household": {
    "id": "uuid",
    "name": "Our home",
    "icon": null,
    "currency": "EUR",
    "timezone": "Europe/Berlin"
  }
}
```

IDs are UUID strings, `icon` may be null, and all fields contain current stored values rather than the examples.

### Edge Cases

- Date-only boundaries include short and long DST days, midnight transitions, and calendar rollover; no fixed 24-hour duration substitutes for the next local day.
- Invalid calendar dates, invalid IANA zones, gaps and ambiguous overlaps do not silently normalize to another meaning.
- A date-time may map to a different calendar date in the household zone than in a device or host zone.
- Inactive membership must not supply household data; no membership and unauthenticated access have distinct outcomes.
- Null household icon remains present as null; success and unauthorized responses both carry the cache directive.

## Requirements

### Functional Requirements

- **FR-001**: Represent one-off deadlines as mutually distinguishable `NONE`, `DATE` (original calendar date and household IANA zone), or `DATE_TIME` (validated absolute instant).
- **FR-002**: Reject invalid dates and zones; reject explicit local times in DST gaps and require a valid first/second occurrence selection for overlaps.
- **FR-003**: Derive overdue for open tasks using an injectable clock without changing task status. `DATE` is overdue at or after the next calendar-day start in the household zone; `DATE_TIME` is overdue strictly after its instant. `NONE` and terminal tasks are never overdue.
- **FR-004**: Deadline meaning and overdue results MUST be independent of device and host timezone, and local-day boundaries MUST be calendar-based, including DST changes.
- **FR-005**: Calendar filtering MUST use the original `DATE` due date and the household-zone local date derived from a `DATE_TIME` instant.
- **FR-006**: Provide read-only `GET /api/v1/auth/me` to return the authenticated account and household from its active membership, or null household without active membership, using the response shape above and current stored values.
- **FR-007**: Unauthenticated requests MUST return `401 UNAUTHORIZED` using the existing API error contract without account or household data. All endpoint responses MUST include `Cache-Control: no-store`.
- **FR-008**: The endpoint MUST prevent access to another account's household and MUST ignore inactive membership when determining current household.
- **FR-009**: Document the endpoint's response and error contract in the authoritative OpenAPI document and assess existing-client compatibility.
- **FR-010**: This slice MUST expose no recurrence endpoints, templates, or background generator.

### Key Entities

- **Deadline**: One of no deadline, original household-local calendar date, or validated absolute instant.
- **Household**: Context with a valid IANA timezone and current identifying and display fields.
- **Account and membership**: Authenticated identity and its active or inactive association with a household.
- **Task**: A one-off item whose open or terminal state governs derived overdue status.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All specified boundary examples, including both short and long DST days and exact date-time equality, produce the stated overdue result under controlled time.
- **SC-002**: All specified invalid zone, date, DST gap and unselected or invalid overlap cases are rejected, and each valid overlap occurrence resolves distinctly.
- **SC-003**: All date-only and date-time calendar examples retain their specified household-local date across differing host timezones.
- **SC-004**: All specified current-context scenarios return the correct account and household or null, prevent cross-account disclosure, and supply the specified cache behavior.

## Assumptions

- HO-10 supplies household timezone and active membership foundation; HO-12 is complete. Household timezone is read-only in M0 and defaults to Europe/Berlin at creation as established by D01 and FR-HH-07.
- This implements an early one-off deadline subset of HO-016 and HO-18; their remaining scope stays open and this work is reused rather than counted twice.
- Authentication, account creation, membership mutation, task creation APIs, recurrence and background generation are outside this slice. The current-context endpoint reuses existing authentication.
- Full Spec Kit workflow and owner approval of specification and plan apply before implementation or test files are edited. Controlled-clock tests and synthetic fixtures are required; applicable HTTP and application-boundary verification uses production migrations.
