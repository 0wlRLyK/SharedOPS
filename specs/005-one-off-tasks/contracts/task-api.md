# Task HTTP contract (proposed)

Paths use `/api/v1/households/{householdId}/tasks`; session authentication and CSRF rules are unchanged. JSON objects reject unknown fields, duplicate keys, incorrect types and unsupported values; UUIDs use canonical form. Fields not listed cannot be written. The implementation updates `docs/api/openapi.yml` to match this contract and its existing ProblemDetail schema.

## Create

`POST` takes an object with required nonblank string `title` and optional `description` (string or null), `responsibilityId` (UUID or null), `priority` (LOW/NORMAL/HIGH), `assigneeAccountId` (UUID or null), and `deadline` (tagged object described below). Omitted responsibility and null mean standalone. Omitted assignee defaults to unassigned for standalone and current active owner for linked; explicit null always means unassigned. An explicit UUID must resolve to an active membership in this household. Omitted priority means NORMAL; explicit null is invalid. Omitted deadline means NONE; explicit null is invalid. Return 201 with `Location` pointing at the new resource and a Task view. Unknown, foreign or nonexistent responsibility returns the same 404; paused/archived context returns 409 INVALID_STATE. Invalid assignee yields generic 400 INVALID_ASSIGNEE for inactive, missing and foreign IDs, without confirming global existence.

## Read and edit

`GET /{taskId}` returns 200 Task view; inaccessible or missing task/household yields indistinguishable 404. `PATCH /{taskId}` requires nonnegative integer `version` and at least one of `title`, `description`, `priority`, `assigneeAccountId`, `deadline`. Missing editable fields preserve their values; `description: null` clears description, `assigneeAccountId: null` clears assignment, and `deadline: {"kind":"NONE"}` clears deadline. Title and priority cannot be null. Responsibility and status cannot be patched. Successful edit returns 200 view with version incremented once. Stale version gives 409 VERSION_CONFLICT; terminal task gives 409 INVALID_STATE. All validation/authorization failures roll back all writes, including audit facts.

## Deadline input

- `{ "kind": "NONE" }` has no other keys.
- `{ "kind": "DATE", "date": "YYYY-MM-DD" }` is parsed strictly, interpreted with locked household IANA zone, and stores original calendar date and zone.
- `{ "kind": "DATE_TIME", "instant": "<ISO-8601 UTC instant>" }` accepts an explicit UTC instant, preserving up to 9 fractional digits; it must meet `Deadline.DateTime` projectable bounds.
- `{ "kind": "DATE_TIME", "localDateTime": "<ISO local date-time>", "occurrence": "FIRST" | "SECOND" }` resolves against household zone using `Deadline.fromLocalDateTime`; occurrence is optional except in an overlap. Gap is invalid; overlap without a choice is invalid. `instant` and `localDateTime` are mutually exclusive. No client zone accepted.

## Task view

Return `id`, `householdId`, nullable `responsibilityId`, `title`, nullable `description`, `priority`, `status`, `version`, nullable `assigneeAccountId`, `deadline`, and boolean `overdue`. `deadline` is canonical `{kind:NONE}`, `{kind:DATE,date,zone}`, or `{kind:DATE_TIME,instant}`. An existing assignment may project its historical same-household account ID even after membership becomes inactive. `overdue` uses current injected clock and task status; it is never persisted. Each response reflects committed precision, including nanoseconds.

## Error and compatibility rules

Unauthenticated 401 UNAUTHORIZED; malformed/invalid field 400 INVALID_VALUE or VALIDATION_ERROR; inaccessible household or inaccessible/missing task/responsibility 404 NOT_FOUND; explicit invalid assignee 400 INVALID_ASSIGNEE; paused/archived context, terminal edits 409 INVALID_STATE; stale edit 409 VERSION_CONFLICT. Use existing structured `ProblemDetail` without resource-existence hints; reject before side effects. Contract tests compare missing versus foreign response code and payload. No existing public task API is replaced; document new response schemas and statuses as additive. Browser UX, task lists and lifecycle actions are outside this slice.
