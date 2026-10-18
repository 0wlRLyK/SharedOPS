# ADR-0005: Allow a Task to Reference a List

Date: 13.10.2026
Status: Accepted

## Context

Tasks may need a shared List as reference material. Shopping reminders should inform the assignee without making Task completion depend on every item being checked. This M1 decision covers HO-091, FR-TASK-14–17 and AC-17–19; ListItem conversion remains a separate workflow.

## Decision

- A concrete Task references zero or one List; a List may be referenced by multiple Tasks in the same Household, regardless of their Responsibilities. Linking never transfers Responsibility, assignee or financial context.
- Both active partners may attach, replace or detach an active List on an OPEN Task with an expected Task version. Terminal Tasks retain an immutable reference. The referenced List itself remains live and may change; it is not a historical snapshot or a checklist.
- Archiving a referenced List preserves the reference and authorized access but disables the shopping warning. Archived Lists cannot be newly attached. Hard deletion of Lists is not introduced.
- RecurringTaskTemplate has no List field in M1. Users may attach a List to an existing generated occurrence. No cloning, item reset or inheritance from a template is added.
- On completion, the server checks the current reference and current items. An active List with purpose exactly SHOPPING and outstanding unchecked items requires a warning acknowledgement. Other purposes, absent references, empty/fully checked Lists and archived Lists do not warn. Purpose, not capabilities, determines warning eligibility.
- The warning offers “Return to list” and “Complete anyway”. Acknowledgement never bypasses current-assignee authorization, expected Task version or terminal-state checks. Reading the List unsuccessfully fails visibly with retry; unavailable data is not treated as an empty List.
- A new reference or newly outstanding items invalidates the acknowledgement and requires a refreshed warning; checking or deleting previously outstanding items alone does not require another warning. Check and completion must have a defined transaction boundary so new items cannot slip between the final check and commit. Exact protocol/locking mechanics belong to M1 technical design.
- Conversion is a separate link with a separate invariant. The completing Task's own source ListItem, if it belongs to the referenced List, is excluded from the warning because it completes atomically with that Task. Other converted items remain eligible if unchecked. Failure of conversion propagation still rolls back completion.
- Reference completion never changes other ListItems, archives Lists, creates Expenses, reopens Tasks or changes other Tasks. No automatic follow-up Task for remaining items is added.
- Completion history records whether a shopping warning was acknowledged and the count outstanding at the accepted final check, excluding the own source item. Preserve the reference identity; do not copy the whole List or claim that unchecked means physically unpurchased.

### Module/API boundary

Coordinate linking, warning checks and completion through public application contracts. Keep task domain independent of list persistence; avoid reciprocal task/list implementation imports. The future API needs reference read/update and completion-warning/acknowledgement behavior usable by all clients, with stable errors and stale-version handling. Exact endpoint names and DTOs are deferred to implementation design, to be specified before implementation.

## Alternatives Considered

- Multiple Lists per Task: deferred to keep reference selection and warning scope small.
- Blocking completion until all items are checked: rejected because Task and List completion are independent.
- A UI-only warning: rejected because other clients could bypass it.

## Consequences

The server must coordinate List reads, warning acknowledgement and Task completion through public application contracts. The reference stays live rather than becoming a historical snapshot. Task completion must leave unrelated ListItems and Tasks unchanged.

## Verification

Implementation is pending. Before M1 release:

- Verify no reference, one reference, same/cross Responsibility, shared List across Tasks, foreign Household rejection, both-partner OPEN edits, terminal immutability, archived/no-warning, non-SHOPPING/no-warning, empty/complete/no-warning and concrete recurrence linking.
- Verify warning acknowledgement, cancellation, fresh-item/reference changes, checked/deleted-item shrinkage, reassignment/stale versions, read failure, completion retry and conversion rollback/source exclusion.

See the [requirements inventory](../requirements.md), [product decision register](../decision-register.md) and [test strategy](../test-strategy.md) for related requirements and release checks.
