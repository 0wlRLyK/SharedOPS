# SharedOPS — Risk Register

**Status:** Active planning baseline; ratings provisional.\
**Updated:** 18.10.2026
**Scope:** M0 delivery and engineering risks; M1/M2 product risks

**Owner:** Project author

## Purpose and operating rules

This register covers M0 delivery and engineering risks. M1 Task–List and M2 financial-stage risks are tracked separately below. Manage confirmed defects in implementation tickets and reassess the related risks after each fix.

- **Likelihood / Impact:** `LOW`, `MEDIUM`, `HIGH`. Provisional ratings are conservative planning judgements and may change when new evidence becomes available. Reassess ratings using implementation findings, test results and delivery forecasts.
- **Status:** `OPEN` (requires monitoring), `MITIGATING` (risk-response work under way), `CLOSED` (no longer applicable or explicitly retired with a reason). All listed risks are open.
- **Review:** Review weekly during M0 planning and whenever a trigger occurs. Review the M1/M2 supplement before committing dependent M1/M2 implementation estimates and at the relevant integration/UI/release gates. Record material rating changes and their reasons in the project progress log.
- **Effort convention:** One _planned working day_ is provisionally **4 hours of productive project work** (conservative capacity assumption). The current M0 recovery calendar uses **4–5 productive hours per weekday**. Saturdays and Sundays are **contingency reserve only**, not baseline capacity. Reconcile work-package actuals, remaining effort and the calendar at the BRP-MS2 milestone review.
- **Delivery baseline:** Compare forecasts with the current approved M0 schedule. Any indication that the fixed submission deadline could be missed requires immediate attention, even before a quantitative trigger is met. Do not remove mandatory M0 requirements or weaken security/quality gates without an explicit scope decision.

## Active risk summary

| ID    | Risk                                                                     | Likelihood                                | Impact                                   | Owner          | Status |
| ----- | ------------------------------------------------------------------------ | ----------------------------------------- | ---------------------------------------- | -------------- | ------ |
| R-001 | M0 delivery delay                                                        | MEDIUM (provisional; assessed LOW–MEDIUM) | MEDIUM                                   | Project author | OPEN   |
| R-002 | Unexpected React/Spring implementation complexity                        | MEDIUM (provisional; assessed LOW–MEDIUM) | HIGH                                     | Project author | OPEN   |
| R-003 | Unauthorized access and cross-household data exposure                    | LOW (provisional; reassessment pending)   | HIGH                                     | Project author | OPEN   |
| R-004 | Frontend effort underestimation                                          | MEDIUM                                    | HIGH (provisional; assessed MEDIUM–HIGH) | Project author | OPEN   |
| R-005 | Late clarification of public-hosting requirements                        | MEDIUM (provisional; assessed LOW–MEDIUM) | HIGH                                     | Project author | OPEN   |
| R-006 | Concurrent operations violate domain invariants or transaction atomicity | LOW (provisional; reassessment pending)   | HIGH                                     | Project author | OPEN   |

## Risk details and response plans

### R-001 — M0 delivery delay

- **Risk statement:** Underestimated remaining work or overestimated capacity can delay M0 and reduce time for testing, documentation and submission.
- **Likelihood:** `MEDIUM (provisional)`; initial assessment `LOW–MEDIUM`. The basic integration spike succeeded, but remaining-work estimates and a representative capacity sample are not yet fully validated.
- **Impact:** `MEDIUM` for a recoverable 2–3-working-day slip; escalation becomes urgent if the submission deadline is threatened.
- **Mitigation:** Track actual focused work hours; inventory unfinished M0 tickets and acceptance criteria; estimate remaining effort and dependencies; refresh the completion forecast weekly; preserve schedule contingency rather than planning weekends as ordinary capacity.
- **Trigger:** Forecast completion shifts **at least two working days** beyond the approved baseline. A one-day shift is ordinarily treated as planning variance unless it threatens critical dependencies or the final deadline.
- **Contingency:** Reassess the critical path and remaining effort; defer optional enhancements first; use weekend reserve if necessary; seek explicit approval before changing mandatory M0 scope or acceptance criteria.
- **Related planning:** M0 schedule/PSP, `HO-086` (pilot release gate).
- **Status / owner:** `OPEN` / Project author.

### R-002 — Unexpected React/Spring implementation complexity

- **Risk statement:** Unfamiliar React or Spring Boot behavior can cause defects, extend debugging and delay critical M0 workflows.
- **Likelihood:** `MEDIUM (provisional)`; initial assessment `LOW–MEDIUM`. Basic client/API/database integration is demonstrated, but advanced security, transaction, and UI behavior has not been comprehensively verified.
- **Impact:** `HIGH`, because small technical errors in critical workflows can cause substantial debugging time or incorrect behavior.
- **Mitigation:** Build narrow, independently verifiable implementation slices; investigate uncertain behavior early; use focused technical spikes when useful; run integration and failure-path checks as features are built.
- **Trigger:** An unresolved technical difficulty causes a task's **actual effort to exceed its estimate by at least one planned working day** (provisionally about 4 productive hours).
- **Contingency:** Request instructor assistance where available. If the problem remains unresolved, investigate a simpler technical alternative that still satisfies mandatory behavior, security and quality criteria; re-estimate the task and update the delivery forecast.
- **Related planning:** Technical implementation tickets; Spring/React integration; relevant `HO-084` verification work.
- **Status / owner:** `OPEN` / Project author.

### R-003 — Unauthorized access and cross-household data exposure

- **Risk statement:** If server-side authentication/authorization checks, current membership validation, or household-scoped resource queries are missing or bypassed, users may access or modify data or operations belonging to another Household, invalidating mandatory M0 security requirements.
- **Likelihood:** `LOW (provisional; reassessment pending)`, carried forward from the original combined R-003 as an initial planning judgement. Reassess after implementing and testing household isolation and authorization.
- **Impact:** `HIGH`, because unauthorized disclosure or modification of Household data would violate security and mandatory acceptance requirements.
- **Mitigation:** Determine the actor from the authenticated server-side security context and validate current Household membership. Scope resource reads and mutations to the authorized Household; validate every user-supplied reference to another existing resource (for example, Task assignee or Responsibility) against membership and applicable action permissions. Do not trust client-provided actor/Household identity. Design negative authorization tests for at least two Households and distinct authenticated sessions, covering reads, edits, deletion and references, and assert persisted data remains unchanged after rejected writes. Include security checks with affected features rather than postponing them to the release gate; static analysis and linters are not substitutes for authorization tests.
- **Trigger:** Any confirmed critical authentication/authorization bypass, unauthorized cross-Household data access, or unauthorized modification. Investigate suspected critical exposures promptly without waiting for confirmation.
- **Contingency:** Block the affected functionality from release; reproduce and correct the missing/bypassed authorization boundary; add regression tests and rerun security and PostgreSQL integration checks. Do not release with an unresolved known critical access-control defect.
- **Related requirements:** `LH-07`, `NFR-06`, `NFR-15` (including security tests); for deletion-related identity/access checks also `NFR-14`. Relevant implementation/verification: `HO-009`, `HO-010`, `HO-013`, `HO-008`, `HO-086`.
- **Status / owner:** `OPEN` / Project author.

### R-004 — Frontend effort underestimation

- **Risk statement:** Forms, responsive layout, state handling, error feedback, localization and accessibility may take longer than estimated, reducing time for M0 testing and documentation.
- **Likelihood:** `MEDIUM`. React/Next.js experience exists, but the complete M0 frontend is more demanding than the minimal spike.
- **Impact:** `HIGH (provisional)`; initial assessment `MEDIUM–HIGH` due to possible several-day delay in reaching an integrated release.
- **Mitigation:** Implement frontend features directly as small end-to-end vertical slices against the actual backend. Check essential happy/error/loading states during implementation and update estimates as complexity becomes visible. No standalone prototyping phase is required.
- **Trigger:** An individual frontend task exceeds its original effort estimate by **at least one planned working day** (provisionally about 4 productive hours) because of unanticipated UI complexity or troubleshooting.
- **Contingency:** Prefer simpler implementation alternatives that preserve mandatory M0 functionality, accessibility, essential UX, and correct backend behavior. Re-estimate affected work and revise the delivery forecast. Do not silently drop accepted criteria.
- **Related planning:** `HO-082` and `HO-083` (M0 browser slices), `NFR-11`, `NFR-12`, `NFR-13`.
- **Status / owner:** `OPEN` / Project author.

### R-005 — Late clarification of public-hosting requirements

- **Risk statement:** A late requirement for public hosting would add deployment work and could delay submission.
- **Likelihood:** `MEDIUM (provisional)`; initial assessment `LOW–MEDIUM`. Public hosting is not confirmed as a course submission obligation. Confirm the applicable rule with the course reviewer.
- **Impact:** `HIGH` if the requirement is imposed too late to plan and verify deployment safely.
- **Mitigation:** Keep the M0 application reproducibly deployable through containerization, environment-specific configuration, secure externalized secrets, CI/CD, deployment instructions, and health/recovery checks. These measures follow `NFR-17`; verify them through the corresponding implementation work.
- **Trigger:** **Authoritative confirmation** that a public deployment is required for project submission.
- **Contingency:** Use the simplest suitable hosting option, reusing prepared container/build/deployment configuration without changing the selected architecture or mandatory M0 functional scope; re-plan and verify HTTPS, configuration, secrets and smoke/restore behavior for that environment.
- **Related requirements:** `LH-08`, `NFR-17`; packaging/operations work `HO-036`–`HO-040`.
- **Status / owner:** `OPEN` / Project author.

### R-006 — Concurrent operations violate domain invariants or transaction atomicity

- **Risk statement:** Concurrent Invitation acceptance, Task transitions or related writes may exceed the two-member limit, lose updates or persist partial state when transaction and concurrency controls are incomplete.
- **Likelihood:** `LOW (provisional; reassessment pending)`, carried forward from the earlier combined R-003. Reassess after implementing transaction boundaries, locking/version policies and real-database tests.
- **Impact:** `HIGH`, because contradictory membership, lost updates, or partial persistence can corrupt critical shared state and fail mandatory acceptance criteria.
- **Mitigation:** Define transaction boundaries and invariants explicitly in application services; use a verified concurrency-control strategy (for example, a lock and fresh membership check inside one transaction, optimistic version checks, or appropriate isolation/constraints) rather than relying on `@Transactional` alone. Enforce the two-active-member limit on Invitation acceptance and stale-write handling for critical Task transitions, including correct conflict responses where applicable. Test with a real PostgreSQL instance and independent transactions/sessions; coordinate overlap deterministically rather than using arbitrary sleeps. Verify both the HTTP/API result and committed database state, including the absence of partial writes or false success/audit records on rejected requests.
- **Trigger:** Any confirmed critical concurrency, stale-update, transaction-atomicity or invariant violation in an M0 workflow (for example, three active Household members, a lost Task update, or partly committed invalid state). Investigate suspected critical defects immediately.
- **Contingency:** Prevent release of the affected workflow; reproduce with controlled concurrent transactions, correct the transaction/locking or version policy, and add regression coverage for successful and conflicting operations. Ensure rejected operations have no partial persisted side effects; rollback when a transaction has performed changes before failure. Re-run relevant PostgreSQL and API integration checks.
- **Related requirements:** `FR-HH-03`, `FR-HH-04`, `FR-TASK-06`–`FR-TASK-10`, `NFR-07`, `NFR-08`, `NFR-10`, `NFR-15`; for deletion/restore transaction boundaries also `NFR-14`. Relevant implementation/verification: `HO-011`, `HO-019`, `HO-008`, `HO-084`, `HO-086`.
- **Status / owner:** `OPEN` / Project author.

## Coverage boundaries and follow-up

- **Build and deployment reproducibility** remains monitored through the implementation tickets and their acceptance criteria (clean builds, migrations, container startup and CI), rather than being a separate M0 risk. Reconsider a separate risk if repeat failures or forecast impact justify escalation.
- **M1/M2 scope.** Task–List is accepted for M1; D15 is `ACCEPTED` as M2 product policy, while schema, migration, implementation and application verification remain pending. Their risks are tracked in the M1/M2 supplement. Other deferred decisions retain their decision-register revisit gates.
- **R-003 / R-006 boundary:** R-003 owns authentication/authorization and cross-Household access; R-006 owns concurrent state transitions and transactional consistency. A defect involving both concerns can be linked to both without silently treating them as one mechanism.
- **Control review.** Use implementation/test evidence and weekly forecast changes to adjust likelihood, impact and status. Keep the original requirement baseline and release exclusions intact.

## Sources and review history

The preliminary feasibility study (11.10.2026) supplies the capacity assumptions and initial integration findings. The implementation backlog defines delivery work and acceptance criteria; the course project submission draft supplies the submission context. These planning sources are maintained outside this repository.

Risk ratings and response plans were reviewed on 13.10.2026. Access control (R-003) and concurrency/atomicity (R-006) were separated on 14.10.2026 and accepted for planning on 15.10.2026. Ratings remain provisional until implementation and execution evidence support reassessment.

See the [requirements inventory](requirements.md), [product decision register](decision-register.md) and [test strategy](test-strategy.md) for the corresponding scope and checks.

## M1/M2 risk supplement

These risks cover the accepted M1 Task–List and M2 financial-stage decisions. M0 ratings, scope and estimates remain unchanged. The project author owns the risks and reviews them before implementation and release. The controls below are planned.

Assess likelihood and impact before committing to M1/M2 delivery, using design, migration and implementation findings.

| ID       | Stage / tickets      | Risk and consequence                                                                                                    | Planned response and verification                                                                                                                                                                                                                                                                                                                                                 | Status |
| -------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| R-M1-01  | M1 / HO-091          | Stale or incorrectly eligible shopping acknowledgement misses outstanding items or blocks completion unnecessarily.     | Warn only for an active List with purpose exactly SHOPPING and outstanding items; server checks final state, assignee and Task version, refreshes acknowledgement for a changed reference or newly outstanding items, and records confirmation/final outstanding count. Verify archive, races, read failure and retries.                                                          | OPEN   |
| R-M1-02  | M1 / HO-091          | Reference is confused with checklist/conversion, causing item mutations, unauthorized links or reciprocal dependencies. | Enforce 0/1 same-Household reference, shared across Tasks/Responsibilities; both partners may version-check link edits only in OPEN Tasks. Keep terminal links immutable, archived references accessible, recurring templates unchanged and statuses independent. Separate conversion source/public contracts; verify own-source exclusion and atomic source completion/rollback. | OPEN   |
| R-M2-01  | M2 / HO-092          | Old debt, budget money or backdated records cross stages and become new-partner obligations.                            | Immutable stage/participants, zero new-pair mutual balance, no budget/allocation carryover, stage-bound reads/writes/retries and expenseDate-independent ownership. Verify legacy migration, personal stages without mutual debt, repeated replacements and frozen closed records; preserve Household currency and D08 purge rules.                                               | OPEN   |
| R-M2-02  | M2 / HO-092          | Membership exposes old financial records through Activity, export, dashboard, audit or reverse source links.            | Participant authorization on every financial surface; direct/indirect negative access scenarios.                                                                                                                                                                                                                                                                                  | OPEN   |
| R-M2-03  | M2 / HO-092 / HO-050 | Invitation/deletion/financial-write races assign the wrong stage or duplicate repayment.                                | Departure closes the pair stage and opens a personal stage; invitation acceptance, not creation, opens the new pair stage. Use a consistent transaction boundary and stage-bound request identity; verify stale/rollback/retry/transition scenarios, including returning partners creating a new stage.                                                                           | OPEN   |
| R-M2-04  | M2 / HO-092 / HO-049 | Unilateral repayment is mistaken for agreement; errors change debt without a trail.                                     | Explicit unilateral-author label, debt cap, immutable audit, reasoned annulment; verify both directions and audit failure.                                                                                                                                                                                                                                                        | OPEN   |
| R-M12-01 | M1/M2 / HO-001       | Added scope silently consumes old estimates or is reported as implemented.                                              | Re-estimate HO-091/092 before commitment; preserve M0 and separate planned acceptance from evidence.                                                                                                                                                                                                                                                                              | OPEN   |

### Triggers, contingencies and acceptance links

- **R-M1-01:** Trigger: stale acknowledgement is accepted, an ineligible List produces a Warning, or List-read failure is treated as an empty List. Contingency: block the affected completion path until corrected; return a visible retry/renewed-confirmation flow without changing Task status. Planned cases include absent/empty/fully checked/non-SHOPPING/archived Lists, new unchecked items, replacement, and removal/checking of previously outstanding items (which alone does not require renewed acknowledgement). Trace: `FR-TASK-14`–`FR-TASK-17`, `AC-17`–`AC-19`, `HO-091`.
- **R-M1-02:** Trigger: a reference changes after termination, crosses Household boundaries, mutates unrelated ListItems on completion, or conversion leaves partial state. Contingency: reject invalid link edits and roll back failed conversion completion; repair the contract/UI distinction before release. Planned cases cover stale versions, both partners, Responsibility independence, archived access and own-source exclusion only (other outstanding converted items still count). Trace: `FR-TASK-14`–`FR-TASK-17`, `AC-17`–`AC-19`, `HO-091`.
- **R-M2-01:** Trigger: a new pair starts with inherited debt, old budgets/allocations transfer, expenseDate changes stage ownership, or closed financial records are edited outside permitted repayment handling. Contingency: block the affected Finance release/migration and correct stage ownership with an audited repair design; do not silently move history to the current partner. Trace: `FR-FIN-13`–`FR-FIN-18`, `AC-20`–`AC-22`, `HO-092`.
- **R-M2-02:** Trigger: a non-participant can read any previous pair or personal-stage financial data directly or indirectly. Contingency: block the affected surface and Finance release, investigate exposure and add regression checks across API, balances, Activity, dashboard, audit, export and reverse source references. Planned cases include C denied A–B/personal-B history and a later survivor retaining only histories they participated in. Trace: `FR-FIN-13`–`FR-FIN-18`, `AC-20`–`AC-22`, `HO-092`.
- **R-M2-03:** Trigger: concurrent departure/invitation acceptance/writes or retries create overlapping active stages, wrong-stage records, resumed historical stages or duplicate settlements. Contingency: reject/retry conflicting operations under the stage transition contract; require rollback and idempotency evidence before release. A pending invitation alone must not close the personal stage. Trace: `FR-FIN-13`–`FR-FIN-18`, `AC-20`–`AC-22`, `HO-050`, `HO-092`.
- **R-M2-04:** Trigger: repayment exceeds remaining historical debt, appears jointly confirmed without evidence, changes current-stage balances/budgets, or annulment removes the original record or lacks a reason/audit trail. Contingency: reject invalid writes; preserve facts and correct mistakes through reasoned audited annulment and, if needed, a new repayment. Planned cases cover both debtor-to-creditor directions, no self-payment/write-off, debt-cap races and audit-failure rollback. Trace: `FR-FIN-13`–`FR-FIN-18`, `AC-20`–`AC-22`, `HO-049`, `HO-092`.
- **R-M12-01:** Trigger: HO-091/092 effort enters M0 estimates, mandatory dependencies are omitted, or planned scenarios are reported as passed tests. Contingency: correct milestone estimates, dependency/gate coverage and evidence labels before commitment or release. Keep documentation consistency checks separate from pending application verification. Trace: `HO-001`, `HO-091`, `HO-092` and their dependent integration/UI/release gates in the active backlog.
