# Data model: HO-18

## Task

Existing `tasks` id, household_id and optional responsibility_id remain. V10 adds nonblank title TEXT, nullable description TEXT, priority constrained to LOW/NORMAL/HIGH, status constrained to OPEN/COMPLETED/CANCELED/SKIPPED (creation OPEN; terminal transitions remain future scope), nonnegative BIGINT version initially 0, nullable assignee_membership_id, deadline_kind NONE/DATE/DATE_TIME, nullable due_date_iso TEXT, nullable due_zone TEXT, nullable due_epoch_second BIGINT and due_nano INTEGER. Check constraints make variant columns mutually exclusive, nanoseconds 0..999999999, title nonblank using the same Java whitespace set as V9, and version nonnegative. Strict calendar, zone and full projectable-range validation is in the domain and persistence adapter; malformed stored rows must fail closed, not fall back. Composite foreign key `(assignee_membership_id, household_id)` references `(id, household_id)` in household_memberships; existing responsibility composite FK prevents cross-household linkage. No active-membership trigger on historical assignments: active status is checked under household lock on creation/reassignment, and later deactivation must not rewrite existing tasks.

DATE stores the original date and household zone at assignment; DATE_TIME stores epoch seconds and nanoseconds exactly. NONE stores no due columns. Zone is immutable in M0; date reconstruction uses its captured IANA zone. Reads compute overdue via `DeadlineEvaluator`; there is no overdue column or status. Explicit input validation additionally rejects dates/instants outside `Deadline`'s representable domain, including local DST gaps and ambiguous overlaps without choice.

## Atomic edit fact

`task_edit_facts`: UUID id, task_id, household_id, actor_account_id, occurred_at (UTC instant), previous_version, new_version and changed_fields. Composite FK references `(tasks.id, household_id)` via new unique key, actor references accounts. Versions are nonnegative and new = previous + 1. A trigger prevents fact updates; fact insertion and task CAS commit together. A failed edit inserts no fact. Restrict field identifiers to title, description, deadline, priority, assignment. Do not record user-provided text in facts.

## Transitions and invariants

Create at version 0, status OPEN; successful PATCH with at least one editable field advances exactly once even when a submitted value equals the old value. No field or fact changes on invalid/stale/terminal edit. Responsibility link is immutable in this slice. A resolved owner membership is copied on create and never followed dynamically. Membership and responsibility checks happen under authorized household lock; lock order is household, responsibility (if needed), task. Task update uses version predicate as defense even under locking. Concurrent transfer or deactivation cannot commit an invalid snapshot.

## Migration

V10 first checks `EXISTS (SELECT 1 FROM tasks)` and aborts with an explicit data-preserving migration message before alterations; it must not delete or invent values. With zero tasks, retain existing tables/FKs/checklist behavior, add constraints and fact table. Verify empty upgrade, rejected populated upgrade with data intact, fresh installs and Hibernate validation with production Flyway migrations on PostgreSQL. No rollback-by-data-loss or implicit backfill.
