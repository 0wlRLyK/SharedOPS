# SharedOPS — Product Decision Register

**Status:** Accepted decisions and deferred proposals.\
**Updated:** 18.10.2026
**Scope:** D01–D15 and M1 Task–List references

Accepted describes an approved decision, not completed implementation. Deferred rows describe candidate behavior. Requirement IDs refer to the [requirements inventory](requirements.md); architectural records are indexed in the [documentation index](readme.md#architecture-decisions).

## Decisions

| Decision                                                          | Status        |
| ----------------------------------------------------------------- | ------------- |
| [D01 — One-off deadlines and time zones](#d01)                    | Accepted · M0 |
| [D02 — Pauses, resumes, and scheduler downtime](#d02)             | Deferred · M1 |
| [D03 — Month-end, DST and recurrence rule changes](#d03)          | Deferred · M1 |
| [D04 — Task mutation permissions and terminal states](#d04)       | Accepted · M0 |
| [D05 — List purpose and item reopening](#d05)                     | Deferred · M1 |
| [D06 — Linked ListItem → Task terminal outcomes](#d06)            | Deferred · M1 |
| [D07 — Session lifecycle and transient drafts](#d07)              | Accepted · M0 |
| [D08 — Account deletion and surviving Household](#d08)            | Accepted · M0 |
| [D09 — Exact money and equal split rounding](#d09)                | Deferred · M2 |
| [D10 — Expense corrections after Settlement](#d10)                | Deferred · M2 |
| [D11 — Today progress and attention](#d11)                        | Deferred · M1 |
| [D12 — Budget range and Expense date](#d12)                       | Deferred · M2 |
| [D13 — Currency freeze and financial locking](#d13)               | Deferred · M2 |
| [D14 — Financial creation idempotency](#d14)                      | Deferred · M2 |
| [D15 — Historical financial stages and replacement partner](#d15) | Accepted · M2 |

<a id="d01"></a>

### D01 — One-off deadlines and time zones

**Status:** Accepted — M0; implementation pending. Recurrence semantics excluded (D03).

Accept `NONE`, `DATE`, `DATE_TIME`. Household IANA zone selected at creation (default `Europe/Berlin`, read-only in M0). `DATE` overdue from start of next local calendar day; `DATE_TIME` overdue strictly after due instant. Reject explicit DST-gap local time; require offset choice for overlap.

**Requirements:** `FR-HH-07`, `FR-TASK-03`, `FR-TASK-11`

**Alternatives:** Store only UTC instants; interpret date-only as +24 hours; silently resolve ambiguous local times — not selected for M0.

**Consequences:** Preserves date-only meaning across DST and client time zones; requires variant-aware DTOs, zone-aware comparisons, boundary tests.

**Revisit:** Revisit only if timezone-edit scope, client contract, or boundary-test evidence changes; recurrence handled separately in M1.

**Sources:** Roadmap §5 D01; Research Register accepted baseline D01 / RD-18; `HO-081`, `HO-018`, `HO-082`.

<a id="d02"></a>

### D02 — Pauses, resumes, and scheduler downtime

**Status:** Deferred — M1.

Proposed behavior: pausing a Responsibility/template suppresses future generation; paused dates are not backfilled after resume. Unexpected downtime of an active template is caught up in bounded batches (demo default 100), advancing watermark only on committed success.

**Requirements:** `FR-RESP-07`, `FR-REC-03`, `FR-REC-04`, `FR-REC-05`, `FR-REC-07`, `NFR-10`

**Alternatives:** Backfill paused intervals; skip downtime occurrences; unrestricted catch-up — neither accepted.

**Consequences:** Distinguishes intentional pause from operational failure; requires generation watermark, duplicate prevention, recovery and concurrency tests.

**Revisit:** **Before `HO-016` / `HO-022`** and dependent recurrence implementation.

**Sources:** Roadmap §5 D02; Research Register §6 / RD-22; `HO-016`, `HO-022`.

<a id="d03"></a>

### D03 — Month-end, DST and recurrence rule changes

**Status:** **DEFERRED — M1**; proposal only; separate from accepted one-off DST rules in D01.

Proposed behavior: a missing monthly day (e.g. 31st) maps to that month's last day without changing the nominal anchor. For recurrence, DST gap → first valid instant, overlap → earlier offset, at most one occurrence per local date. Rule/zone changes apply only to not-yet-created occurrences.

**Requirements:** `FR-REC-02`, `FR-REC-03`, `FR-REC-04`, `FR-REC-06`, `FR-HH-07`

**Alternatives:** Skip short months; shift nominal anchor permanently; choose later overlap offset; rewrite historical occurrences.

**Consequences:** Deterministic calendars and stable history; requires DST/month-end tests and rule-change contract.

**Revisit:** **Before `HO-016` / `HO-021`**.

**Sources:** Roadmap §5 D03; Research Register §6 / RD-22; `HO-016`, `HO-021`.

<a id="d04"></a>

### D04 — Task mutation permissions and terminal states

**Status:** Accepted — M0; implementation pending.

Both partners may edit, reassign and cancel `OPEN` Tasks; only current assignee may complete (`UNASSIGNED` must first be taken). Cancel requires confirmation; terminal Tasks are read-only, with no reopen. Key action facts are recorded atomically. Recurring `SKIPPED` rules are later scope.

**Requirements:** `FR-TASK-03`, `FR-TASK-06`–`FR-TASK-10`, `FR-TASK-12`, `FR-TASK-13`, `NFR-07`

**Alternatives:** Owner-only edits; allowing anyone to complete; implicit completion from checklist; terminal reopen — not adopted.

**Consequences:** Clear actor/state/action contract; needs authorization, stale-version, atomic-action and UI tests; `SKIPPED` outside M0.

**Revisit:** Revisit for an explicit reopen/new authorization requirement or **before M1 recurring skip implementation**.

**Sources:** Roadmap §5 D04; Research Register accepted baseline / RD-19; `HO-019`, `HO-020`, `HO-083`.

<a id="d05"></a>

### D05 — List purpose and item reopening

**Status:** Deferred — M1.

Proposed behavior: `List.purpose` locks forever after the first-ever `ListItem` (even if items are deleted), backed by a marker. Regular checkbox completion is one-way in MVP; reopen needs an explicit later rule. Archiving removes a List from active views while retaining history.

**Requirements:** `FR-LIST-02`, `FR-LIST-04`, `FR-LIST-07`–`FR-LIST-09`, `FR-LIST-11`

**Alternatives:** Unlock purpose when List becomes empty; allow uncheck/reopen with defined audit; delete instead of archive.

**Consequences:** Avoids silent semantic changes; limits undo behavior; requires persisted `everHadItems`-equivalent invariant and UX explanation.

**Revisit:** **Before `HO-023` / `HO-024`**.

**Sources:** Roadmap §5 D05; Research Register §6 / RD-26; `HO-023`, `HO-024`.

<a id="d06"></a>

### D06 — Linked ListItem → Task terminal outcomes

**Status:** Deferred — M1.

Proposed behavior: only `COMPLETED` linked Task completes its `ListItem`; `SKIPPED` or `CANCELLED` leaves the item linked and incomplete with terminal reason. Linked item cannot be checked directly. No reconversion or relinking in MVP. Whole-List references are separately accepted for M1 HO-091; they do not change conversion invariants.

**Requirements:** `FR-CONV-01`–`FR-CONV-06`, `FR-TASK-09`, `FR-TASK-10`, `NFR-10`

**Alternatives:** Detach or reconvert after cancellation; mark item complete on any terminal result; allow direct checking — not selected.

**Consequences:** Requires unique link and same-transaction Task-completion propagation; cancelled Tasks do not imply purchased/completed items.

**Revisit:** **Before `HO-025` / `HO-030`** and conversion acceptance tests.

**Sources:** Roadmap §5 D06; Research Register §6 / RD-26; `HO-025`, `HO-030`.

<a id="d07"></a>

### D07 — Session lifecycle and transient drafts

**Status:** Accepted — M0; implementation pending.

Spring Security JDBC server-side sessions with cookie/CSRF: regular idle 30 min / absolute 8 h; opt-in persistent idle 7 d / absolute 30 d. Absolute age from login. Logout revokes current session; password change (current password required) revokes all including current. Nonsecret drafts exist only in tab memory and may be manually restored after same-account reauthentication and version check.

**Requirements:** `FR-AUTH-03`, `FR-AUTH-04`, `NFR-06`, `NFR-13`

**Alternatives:** JWT/refresh tokens; unlimited sliding sessions; durable/offline draft replay — not selected.

**Consequences:** Server-enforced age, global session revocation, CSRF protection and no stale auto-write; session/reauth/draft tests required.

**Revisit:** Revisit if security evidence or cross-client/native auth changes; M4 native adaptation separately.

**Sources:** Roadmap §5 D07; Research Register accepted baseline / RD-20; `HO-009`, `HO-083`.

<a id="d08"></a>

### D08 — Account deletion and surviving Household

**Status:** Accepted — M0; implementation pending. M2 Finance replacement follows D15 and HO-092.

Require current password and confirmation, revoke sessions/invitations, erase credentials/email/name while retaining pseudonymous historical facts. Transfer active/paused Responsibilities to survivor; unassign deleted actor's `OPEN` Tasks; preserve terminal/archive history. Last active member triggers immediate active-DB purge. M0 replacement via new invitation; deletion-safe restore uses protected deletion register.

**Requirements:** `FR-HH-02`–`FR-HH-05`, `FR-RESP-07`, `FR-RESP-10`, `FR-TASK-13`, `NFR-14`, `NFR-17`

**Alternatives:** Block deletion when work exists; cascade-delete shared history; transfer OPEN Tasks automatically; restore deleted identities — not selected.

**Consequences:** Surviving member retains actionable work without impersonation; backup and race handling must be verified. M0 replacement does **not** settle M2 financial party semantics.

**Revisit:** Revisit on new M1/M2 retained entities, failed restore/race tests, or Finance partner replacement (D15).

**Sources:** Roadmap §5 D08; Research Register accepted baseline / RD-21; `HO-085`, `HO-038`, later `HO-034`.

<a id="d09"></a>

### D09 — Exact money and equal split rounding

**Status:** Deferred — M2.

Proposed behavior: one Household currency and its minor-unit scale; positive Expenses/Settlements, nonnegative splits; payer takes extra minor unit in an odd equal split. Show exact amounts; negative expenses/refunds outside MVP; proposed maximum `10^12 − 1` minor units pending overflow check.

**Requirements:** `FR-HH-06`, `FR-FIN-05`–`FR-FIN-08`, `NFR-08`

**Alternatives:** Floating-point money; arbitrary fraction of minor units; alternate rounding recipient; refund support in MVP.

**Consequences:** Reproducible ledger arithmetic and rounding; validate split conservation and overflow.

**Revisit:** **Before `HO-042` / `HO-043`**, especially maximum/precision tests.

**Sources:** Roadmap §5 D09; Research Register §6 / RD-26; `HO-042`, `HO-043`.

<a id="d10"></a>

### D10 — Expense corrections after Settlement

**Status:** Deferred — M2.

Proposed behavior: preserve historical settlement facts when an Expense is corrected, recompute signed net (which may reverse direction); do not rewrite completed transfers to fit revised expense. Explicit Settlement edit/create validates against net excluding the old edited record; financial writes use shared guard.

**Requirements:** `FR-FIN-08`–`FR-FIN-12`, `NFR-07`, `NFR-08`

**Alternatives:** Rewrite prior Settlement amounts; clamp net at zero; prohibit corrections after settling.

**Consequences:** Accurate historical transfers and reversible balances; tests for corrections, settlement edits, audit and concurrency.

**Revisit:** **Before `HO-046` / `HO-049`**.

**Sources:** Roadmap §5 D10; Research Register §6 / RD-26; `HO-046`, `HO-049`.

<a id="d11"></a>

### D11 — Today progress and attention

**Status:** Deferred — M1.

Proposed behavior: progress = completed due-today visible Tasks / due-today visible non-cancelled, non-skipped Tasks. Visible personal set is own-assigned plus unassigned (exclude tasks assigned to other partner). Overdue separate; active Responsibility with `OPEN` overdue Task attracts attention; zero denominator → neutral empty state.

**Requirements:** `FR-HOME-01`–`FR-HOME-03`, `FR-NAV-01`, `FR-NAV-02`

**Alternatives:** Count all household Tasks; include cancelled/skipped; merge overdue into today's denominator; show 100% for empty days.

**Consequences:** Predictable personal progress; query/filter consistency and visibility tests needed.

**Revisit:** **Before `HO-027`** (earlier if Today moves into M0).

**Sources:** Roadmap §5 D11; Research Register §6 / RD-13, RD-24; `HO-027`.

<a id="d12"></a>

### D12 — Budget range and Expense date

**Status:** Deferred — M2.

Proposed behavior: UI date range inclusive, persistence half-open `[start, end+1 day)`; `expenseDate` in Household zone distinct from `createdAt`. Changes of date/context update old/new budget views; explicit period presets, no automatic rollover.

**Requirements:** `FR-BUD-01`–`FR-BUD-03`, `FR-FIN-01`, `FR-HH-07`, `NFR-08`

**Alternatives:** Persist inclusive end directly; assign budget using creation timestamp; automatic rollover of periods.

**Consequences:** Correct edge dates and late-entry attribution; period overlap and cross-period correction tests.

**Revisit:** **Before `HO-042` / `HO-047` / `HO-048`**.

**Sources:** Roadmap §5 D12; Research Register §6 / RD-26; `HO-042`, `HO-047`, `HO-048`.

<a id="d13"></a>

### D13 — Currency freeze and financial locking

**Status:** Deferred — M2.

Proposed behavior: lock Household currency permanently after the first-ever financial record (including later soft-deleted records). Serialize currency-vs-first-write with Household guard/marker; finance calculations use shared guard, lock order Household → Finance → record/budget rows.

**Requirements:** `FR-HH-06`, `FR-FIN-01`, `FR-FIN-09`, `NFR-08`, `NFR-10`

**Alternatives:** Unlock currency after all active records are deleted; rely only on application-side checks; unsynchronized lock order.

**Consequences:** Prevents silent reinterpretation of old amounts and races; requires transaction-order, deadlock and restore tests.

**Revisit:** **Before `HO-046` / `HO-050`**.

**Sources:** Roadmap §5 D13; Research Register §6 / RD-26; `HO-046`, `HO-050`.

<a id="d14"></a>

### D14 — Financial creation idempotency

**Status:** Deferred — M2.

Proposed behavior: `Expense`/`Settlement` creation uses `Idempotency-Key` scoped by user+Household+operation, with atomic fingerprint and replayable result. Same key+different payload → `409`. Demo retention 7 days; after expiry do not auto-replay uncertain requests.

**Requirements:** `FR-FIN-01`, `FR-FIN-09`, `NFR-10`

**Alternatives:** Client-only duplicate prevention; unscoped keys; indefinite or zero dedup retention.

**Consequences:** Prevents double charges/ledger facts on network retry; fingerprint, retention and transaction tests required.

**Revisit:** **Before `HO-050` / `HO-051`**; confirm retention and retry semantics.

**Sources:** Roadmap §5 D14; Research Register §6 / RD-26; `HO-050`, `HO-051`.

<a id="d15"></a>

### D15 — Historical financial stages and replacement partner

**Status:** Accepted — M2, 13.10.2026; technical design and implementation pending.

One Household contains successive immutable-participant stages A–B → personal B → B–C. Each composition change creates a new stage; new pair net zero, no debt/budget carryover. Access only to stage participants; closed Expenses/splits/budgets read-only. Survivor records unilateral real repayments up to old debt and audited annulments, without affecting other stages. Currency freeze and last-member purge preserved.

**Requirements:** FR-FIN-13–18, AC-20–22; FR-HH-03/04, FR-FIN-05/08–12, FR-BUD-03, NFR-06/07/10/14.

**Alternatives:** Block replacement (superseded candidate); new Household (not chosen); separate stages in same Household (accepted).

**Consequences:** Stage identity/access, old-debt audit, personal-stage finance, stage-bound retries and membership/finance transactions; old facts never reassigned to C.

**Revisit:** Before HO-092 implementation: schema/migration/API/locking design; HO-052/054 evidence before M2 release.

**Sources:** [Financial stages ADR](adr/0006-financial-stages.md); Roadmap §5; Research and decision register, accepted financial-stage decision; HO-034 M2 extension, HO-049/050/092/052/054; product decision accepted 13.10.2026.

## Decision status

D01, D04, D07 and D08 are accepted for M0. D15 is accepted product policy for M2, detailed in [ADR-0006](adr/0006-financial-stages.md). Technical design and implementation remain pending. The other deferred rows remain proposals and must be resolved before their listed implementation gates.

The original source decisions are recorded in the project roadmap and research and decision register; ticket references identify the corresponding implementation backlog work. These sources are maintained outside this repository. The accepted decisions summarized here take precedence over older proposals in their history.

## Task–List reference

Separate from D06 conversion: [reference ADR](adr/0005-task-list-reference.md), FR-TASK-14–17, AC-17–19, HO-091. Active SHOPPING warnings are server-checked; archived Lists retain references without warning. D05/D06 retain their deferred status for unrelated details. M0 unchanged.
