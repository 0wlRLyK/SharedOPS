# Research: HO-18

## Authorization and concurrency

**Decision:** Task acquires a new `household::task` transaction-scoped authorized locked view; responsibility provides a narrow task-facing locked snapshot. Acquire household lock before responsibility or task lock and hold it until transaction commit. **Rationale:** `HouseholdResponsibilityAccess` and `ResponsibilityService` already serialize membership, ownership and lifecycle on the household row, but task is allowed only `household::lookup` today; extending an explicit named interface avoids leaking another module's persistence. **Alternatives:** unrestricted household module dependency or direct SQL across modules breaks architecture; unlocked reads race transfers/deactivation. The household view returns IANA zone, actor account, active membership lookup and historical account lookup needed to project existing assignments, without exposing global data. A responsibility snapshot is read only after the task service establishes that view, then locks the responsibility by household and id in the same transaction.

## Storage and migration

**Decision:** Expand V1 placeholder tasks with V10, rejecting upgrade if any existing task rows exist, as V9 did for unrepresentable placeholder responsibilities. Preserve checklist table and existing keys. Store assignee membership ID, not dynamic owner; use composite household foreign keys. Store deadline date as strict ISO text and date-time as epoch-second BIGINT plus nano INT, so Java's supported projectable range and nanosecond boundary semantics survive PostgreSQL persistence without rounding. **Alternatives:** invent placeholder titles or silently default legacy rows loses data; timestamp microsecond truncation changes strict overdue boundary. Keep inactive historical assignments valid after creation; only newly selected assignees must be active.

## Public interface and mutation semantics

**Decision:** use household scoped POST, GET, PATCH with version body, explicit omission vs JSON null and a tagged deadline object; contract describes exact representation. Continue strict unknown-field rejection and structured errors in existing responsibility API. `NORMAL` omission is owner-approved. **Alternatives:** full replacement loses omitted-field semantics; treating omitted and null identically cannot distinguish linked owner default and explicit unassigned. Adding endpoints is additive to current OpenAPI; there is no pre-existing task endpoint. Existing database placeholder clients are not silently migrated; V10 stops with clear error if data exists.

## Time and consistency

**Decision:** construct the existing `Deadline` variants from explicit tagged inputs and locked household zone; evaluate with `DeadlineEvaluator` and injected clock at projection time. Local DATE_TIME input is resolved through `fromLocalDateTime` with FIRST/SECOND for overlaps; absolute instant accepted separately. Use strict precision-preserving storage. **Alternatives:** client/host zone or plus-24-hour calculation violates D01; stored overdue flag becomes stale. The projectable range and DST handling remain HO-81's existing domain rules.

## Audit and validation

**Decision:** nonblank title and nonnegative version follow responsibility conventions; description is nullable text; priority only LOW/NORMAL/HIGH. Avoid arbitrary length limits absent an existing contract. Reject invalid JSON types, unsupported fields and contradictory deadline variants. Record actor, time, task, household, prior/new version and changed field names as an immutable task edit fact in the same transaction. **Alternatives:** controller-only validation allows direct application bypass; asynchronous audit risks partial commits. This records D04 action facts without storing sensitive content in logs.
