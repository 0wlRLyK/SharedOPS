# Research: Responsibility ownership

## Existing baseline

V1 creates `responsibilities(id, household_id)` with unique `(id, household_id)` targeted by the task composite foreign key. No ownership, status or details exist. V7 introduces household memberships; V8 enforces active household slots, and existing membership insertion locks the household row. Responsibility has ID and mode/status enums but no implemented aggregate or API. `HouseholdLookup.isAccessible` checks authenticated active membership but does not offer owner eligibility or a transaction-held serialization contract.

## Decisions and alternatives

### Membership boundary and race

**Decision:** Add a narrow household-owned exported interface that acquires the household lock and resolves authenticated actor plus owner eligibility; use it inside the responsibility transaction. Back it with database-side coordination for membership deactivation and responsibility writes. Existing membership writer's household-row lock gives a common serialization point. **Alternative:** check membership once with ordinary SELECT and then save; rejected because owner or actor can become inactive between check and commit. Direct access to household persistence is rejected by architecture. Merely locking a membership row does not protect uncoordinated state changes or specify the account deletion interaction. Future HO-85 must follow the same order and transfer before deactivation.

### Optimistic concurrency

**Decision:** Version in every existing-resource mutation body, matching current OpenAPI `VERSION_CONFLICT`; create takes no version and returns initial version 0; check under transaction before state/no-op decisions, with conditional update/flush as final guard. **Alternative:** HTTP ETag/If-Match adds a separate convention and does not replace database enforcement. Household serialization simplifies membership races but does not eliminate required version comparisons.

### Persistence and migration

**Decision:** Extend V1 table via V9, preserving existing composite task reference. Guard against any existing placeholder responsibility rows before setting non-null title and owner; fail transactionally with actionable message. **Alternative:** infer owner/title or delete placeholders; rejected because no trustworthy mapping exists and linked tasks may exist. Populated-data remediation is separately designed and approved. Database-level same-household owner FK plus active-owner enforcement and lifecycle constraints complement application checks.

### Transfer facts

**Decision:** Dedicated append-only fact written in the same transaction as an explicit version-checked transfer with controlled clock. **Alternative:** async event or best-effort log cannot prove atomicity; whole activity feature exceeds scope. Existing tasks are untouched. The V1–V8 table has only identity and relationship columns; assignment and completion status do not exist until HO-18. Verify no task writes with a test-only fail-on-write trigger on successful commands and unchanged committed linked rows after success and rejection; actual assignment and reopening regression remains a coverage limitation for HO-18.

### Validation and representation

**Decision:** Required nonblank title, optional description, PostgreSQL `text` fields; preserve content without hidden trimming. Omitted or null description means no description on creation and clears it on full-replacement detail edits; empty string stays distinct. Restore owner omission selects the historical owner, while explicit null or malformed UUID rejects the request; input processing must distinguish omission and null. Description when supplied accepts only string or null and never coerces non-string JSON. Unknown properties are rejected on these new endpoints with a structured 400, including unsupported mode/status fields; enforce this at the responsibility request boundary without assuming a global Jackson default. No new product-specific numeric length limits. Explicit request schema documents null/empty semantics and zero-based nonnegative version. **Alternative:** arbitrary short maximum or normalization invents a product constraint. PostgreSQL and HTTP infrastructure have finite practical limits; do not advertise a numeric limit absent an agreed product decision. Validate whitespace using Java's documented blank predicate and demonstrate spaces/tabs in tests; database check supplements but application defines full Unicode behavior.

### API and lifecycle

**Decision:** Household-scoped REST resources and action endpoints, stable structured errors, separate list filters and explicit `version` fields. Repeated lifecycle action rejects; unchanged edits or same-owner transfers succeed without writing; stale check precedes either decision. **Alternative:** implicit owner change via general edit blurs auditable transfer and accepting repeated transitions conflicts with owner decision. Implement no SHARED mode, task reassignment, recurrence or account deletion.
