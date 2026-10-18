# SharedOPS — Test Strategy

**Status:** M0 test strategy; feature and release checks pending.\
**Updated:** 18.10.2026
**Scope:** M0 Coordination Pilot; later stages verified separately

**Owner:** Project author

## Purpose and governing principles

This strategy defines the test scope, environments, responsibilities and evidence for the M0 pilot. It complements the requirements inventory, acceptance criteria, `/Fnn/ → /Tnn/` catalogue and implementation tickets; it does not replace them.

1. **Requirement- and risk-driven:** verify applicable M0 functional requirements, NFRs, ticket acceptance criteria (AC), critical domain invariants and negative paths. Code coverage is a diagnostic measure, never proof of requirement fulfillment.
2. **Production-relevant persistence:** use real PostgreSQL, production Flyway migrations and actual transaction/constraint behavior for persistence, security and race assertions. H2 is not a substitute.
3. **Verify outcomes and side effects:** for rejected commands, assert the API response **and** relevant committed state, including related records and domain facts.
4. **Determinism:** explicit test data, controlled `Clock`, stable ordering, independent test isolation, bounded waits and controlled concurrency barriers rather than `Thread.sleep`.
5. **Fail closed:** any mandatory failed, blocked, skipped or unexecuted gate prevents merge/promotion until resolved, rerun or formally addressed through an authorized baseline decision. No silent weakening of assertions.
6. **Evidence over assertion:** record actual tested revision, environment, command, outcome and artifacts. `Planned` is not `Passed`.
7. **Incremental delivery:** tests accompany implementation slices; the full M0 release gate is not deferred to the final day.

## Scope and boundaries

**In scope for M0:** registration and authenticated sessions; household creation, two-active-member maximum and invitations; INDIVIDUAL Responsibility lifecycle and owner transfer; one-off Tasks, assignment/Take/Take over/completion/cancellation, deadline semantics and checklist; My/All queries and pagination; localization en/uk/de, mobile browser workflows and essential accessibility; deletion and synthetic demo reset; server-side authorization, concurrency, API contracts, test/CI reproducibility, deployment security, performance baseline and restoration/recovery. Include negative, boundary and failure cases.

**Not part of M0 functional acceptance:** SHARED Responsibilities, recurring scheduling, Lists and conversion, full Activity/Home/Quick Add, Finance, Android. Their later-phase tests remain in their own tickets. Do not claim a later-stage ticket completed because a pilot subset is tested.

**Test basis:** current applicable FR/NFR/AC and LH identifiers; BRP Pflichtenheft `/Fnn/`, `/Dnn/`, `/Lnn/`, `/Bnn/` and test cases `/T10/`–`/T210/`; M0 HO ticket acceptance criteria; the [risk register](risk-register.md). Confirm document revision/AC index when implementing tests; older reference counts are not authoritative when changed.

## Test levels and responsibilities

| Level / suite                | Focus and examples                                                                                                                     | Tooling and planned extensions                                                              | Environment                                             |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| **Unit / domain**            | Typed IDs, immutable values, state rules, permission decisions expressible without persistence, time boundaries using injected `Clock` | JUnit Jupiter, AssertJ; Mockito only where isolation adds value                             | JVM, no Spring/DB where possible                        |
| **Component / slice**        | MVC DTO validation, error mapping, serialization, controlled Spring configuration, negative startup/config tests                       | Spring test slices / MockMvc / `ApplicationContextRunner` where supported by pinned stack   | Selected Spring components                              |
| **Architecture**             | Module dependence, no domain-to-framework imports, no cross-module repository access                                                   | Spring Modulith (integrated); feature-specific architecture assertions as modules are added | Build gate                                              |
| **PostgreSQL integration**   | Flyway, JPA mappings, FK/unique constraints, tenant isolation, transaction rollback, session security, race conditions                 | Testcontainers PostgreSQL + Spring Boot integration tests                                   | Real PostgreSQL and production migrations               |
| **API / contract**           | OpenAPI schema, error codes, pagination/version contract, generated-client compatibility, intentionally breaking fixture               | OpenAPI validation + contract/HTTP tests                                                    | Running API; CI                                         |
| **Browser E2E**              | Two-account journey, Task takeover/conflict, errors, localization, mobile and keyboard paths                                           | Playwright or equivalent selected implementation                                            | Browser + API + PostgreSQL                              |
| **Performance / operations** | Pilot query baseline, bounded SQL counts, restore, smoke, service failure and recovery                                                 | k6 or equivalent; PostgreSQL backup/restore scripts                                         | Isolated documented profile, not ordinary unit-test job |

Use fast domain tests for business rules, database integration tests for transaction and authorization boundaries, and a small set of critical browser journeys. Choose the test level by the behavior being verified.

## Test environment and deterministic fixtures

- **Backend:** JDK 25 LTS and the Maven Wrapper; dependency versions are recorded in the [technology stack](stack.md).
- **Database:** Testcontainers PostgreSQL matching the target major version; run committed Flyway migrations. Ensure DB failures are observed at `flush` or `commit`; implicit test-level rollback must not hide commit-time errors.
- **Isolation:** no reliance on test order or developer-local databases. Use controlled per-test cleanup, isolated schemas/databases or carefully managed transactions. Concurrency tests require separate connections and committed-state checks.
- **Core identity graph:** Household A with partner A1/A2; independent Household B with B1; dedicated pending candidates/invitations for capacity tests. Task, Responsibility, memberships and actor identities must be explicit and consistent.
- **Data factories:** named Test Data Builders/fixture factories set business-critical fields (role, status, membership, version, relationships, deadline, invite state). Seeded Datafaker may fill noncritical display text; do not randomize expected invariants.
- **Time:** inject a controllable `Clock` and IANA household timezone; include Europe/Berlin DST gap/overlap and date-only boundary cases, independent of host/device clock.
- **Security:** independent authenticated sessions, valid CSRF contexts and deliberate unauthenticated/foreign-household calls. Never use real credentials or private household data in test artifacts.
- **Browser matrix:** declare exact browser/OS/device viewport before execution; at minimum cover a primary Chromium E2E path plus meaningful Firefox/Safari smoke, supported locales, mobile widths, keyboard focus and form labels. Confirm the matrix before execution.

## Critical verification scenarios

The course submission test catalogue groups scenarios under `/Tnn/` IDs. Each group may require several concrete tests, traced to the applicable ticket acceptance criteria:

| Scenario      | Critical property / evidence                                                                                                                                                             | Main tickets / risk        |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| **T10–T20**   | Unique normalized email even in registration race; secure login/session rotation, expiry, logout, password-change revocation, CSRF and no sensitive logging                              | HO-009/013; R-003/R-006    |
| **T30**       | Cross-household GET/mutation/reference rejection at API **and application-use-case** boundaries; no data disclosure, modified fields, false audit events or unauthorized DB writes       | HO-010; R-003              |
| **T40**       | Expired/revoked/consumed invitations; deterministic final-slot race with at most two active members; rejected attempt leaves no partial membership/invite changes                        | HO-011; R-006              |
| **T50–T60**   | INDIVIDUAL Responsibility lifecycle, valid owner/restore, context validation, rejection of SHARED and out-of-scope states                                                                | HO-012/018                 |
| **T70–T80**   | One successful concurrent assignment/version update, conflict for stale caller (`409` where contract specifies); no stale completion or false completedBy/At; valid terminal transitions | HO-019; R-006              |
| **T90–T110**  | Checklist consistency/reordering, deadline and DST, My/All household isolation, stable paginated reads, no unbounded N+1                                                                 | HO-020/081/082; R-003      |
| **T120–T130** | Two distinct browser sessions complete the invitation → Responsibility → Task → takeover/checklist → completion journey; error recovery, en/uk/de, mobile, keyboard and focus            | HO-083; R-004              |
| **T140–T150** | Reauthenticated account deletion, token/session/invite revocation, ownership/actor history, rollback and races; authorized synthetic reset cannot affect another household               | HO-085; R-003/R-006        |
| **T160–T180** | OpenAPI/client contract, no-cache performance/SQL checks, security/ops controls, migrations, architecture and clean CI/build                                                             | HO-002–008/013/014/084/087 |
| **T190**      | Isolated backup restore, current deletion-replay protections, invalidated restored sessions/invites, measured recovery                                                                   | HO-038/039; R-006          |
| **T200**      | All applicable M0 AC, release candidate, runbook, operational checks and actual reviewer outcome                                                                                         | HO-086; BRP-D04            |
| **T210**      | Distinct product pilot evidence: independent non-author scenarios and 5–7-day couple usage where feasible; keep observations separate from engineering and course acceptance             | HO-086                     |

**Cross-household assertions:** cover all relevant read, edit, delete and cross-resource-reference paths; for forbidden writes, inspect persisted state through a fresh persistence context/connection after transaction completion. Correct HTTP denial alone is insufficient. Choose equivalence classes based on requirements and risks.

**Race harness:** independently started transactions/connections; synchronized start/critical overlap via `CountDownLatch`/`CyclicBarrier` or equivalent, futures with bounded timeouts (no arbitrary sleeps). Assert final committed membership/task versions and rejected-operation side effects. `Awaitility` may await eventual conditions but is not itself a substitute for controlling concurrency overlap.

## Execution strategy and quality gates

| When                                   | Expected run                                                                                                                                                            | Policy                                                                                         |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **While coding**                       | Changed and nearby unit tests; fast local formatting/compilation as needed                                                                                              | Fast developer feedback; not a merge authorization                                             |
| **Before commit**                      | Full unit tests + pinned formatter/linter + compile                                                                                                                     | Keep changes locally sound                                                                     |
| **Before PR / push**                   | Unit tests and affected slice/PostgreSQL tests                                                                                                                          | Prefer feedback before CI; complete CI remains authoritative                                   |
| **Every PR (mandatory CI)**            | Clean build; format/static/architecture; unit/slice; real PostgreSQL migration, integration/security/concurrency; contract compatibility; agreed critical browser paths | All required jobs must succeed; no bypass based on a large number of other passes              |
| **Release candidate (mandatory gate)** | Full M0 AC/traceability, browser localization/accessibility, contract/performance, restore/rollback, operational/security smoke, evidence and documentation             | No release with unresolved Must/critical defects or missing mandatory proofs                   |
| **Optional separate runs**             | Extensive browser matrix, prolonged performance/soak or drill rehearsals                                                                                                | May be scheduled separately **only** if all required release checks still run before promotion |

### Current commands

Run from the repository root:

| Command       | Current coverage                                                                                                   |
| ------------- | ------------------------------------------------------------------------------------------------------------------ |
| `make test`   | Surefire: JSON serialization, application context startup and Modulith structure                                   |
| `make verify` | Surefire plus Failsafe: Flyway migrations, persistence and OpenAPI smoke checks; Spotless, Checkstyle and Prettier |
| `make check`  | Formatting and Java static checks                                                                                  |

Both test commands need Docker because the context and integration checks use PostgreSQL Testcontainers. The [toolchain verification record](evidence/toolchain-verification.md) records current smoke results. Business authorization, concurrency, browser, performance and recovery tests are still planned. Browser and load-test commands will be documented when implemented. CI may run Maven directly while Testcontainers launches PostgreSQL.

**Mandatory checks:** a test assertion failure, missing required execution, test infrastructure blockage, flaky behavior, controlled architecture/static-analysis violation, broken schema/client compatibility or critical security/concurrency violation blocks merge. Diagnose and rerun failures; automatic retries must not erase the original failure. An informational warning follows its published classification, not an improvised veto.

## Results, requirements coverage and exit criteria

**Test execution status:**

- `PASSED` — ran and met its defined assertions.
- `FAILED` — ran and violated expectations; attach defect and retest link.
- `BLOCKED` — could not run because a prerequisite, environment or dependency failed. A CI job may technically report `failure` while test-management classification is `BLOCKED`.
- `NOT RUN` — implemented but not executed for this revision/environment.
- `NOT IMPLEMENTED` — required verification case still absent.
- `SKIPPED` — explicitly suppressed; reason, scope, approval and revisit needed. Mandatory skipped checks are not passing.

**Requirement verification status:** `Not Verified`, `Partially Verified`, `Verified`, `Failed`, `Blocked`. `Verified` requires all applicable mandatory cases to pass, traceable evidence and accepted business outcomes; four passed and one unimplemented case is **Partially Verified**, not Verified. Any observed failing mandatory case makes the requirement **Failed** until corrected and retested. A blocked prerequisite cannot be treated as a pass.

**Coverage metrics:** maintain a trace from `LH/FR/NFR ↔ /Fnn/ ↔ /Tnn/ ↔ HO-AC ↔ concrete test ↔ evidence`. Track required-case completeness and actual successful execution separately. Line/branch coverage may guide test additions; do not adopt an arbitrary percentage as the proof of critical business behavior.

**Exit gate for a release:** all relevant M0 ticket AC and accepted decisions verified; critical scenarios T10–T200 and applicable product evidence appropriately assessed; zero unresolved blocking defects; mandatory CI green on the candidate; PostgreSQL/restore/deployment evidence complete; documentation and actual reviewer/course disposition recorded. Record the course reviewer's acceptance separately from engineering results. Scope changes need explicit authorization and revised baseline.

## Performance baseline (T160 / HO-084)

Control **the entire workload profile**, not just a headline concurrency number: build/commit, API route, auth session, dataset seed/distribution, page size/sorting/filtering, caching policy, PostgreSQL/application versions, hardware/resources, arrival rate or VU model, warm-up and measurement duration. Verify achieved throughput, errors and generator capacity (`dropped_iterations` if using k6 arrival-rate executor). Compare like-for-like runs.

Record **p50/p95/p99**, throughput, error rate, SQL query count, pagination and N+1 evidence. High p99 without a predefined p99 gate triggers diagnosis; do not retroactively redefine a passed pre-agreed p95 assertion as a failed test.

**Existing engineering proposal in HO-084 (not yet validated):** ten concurrent users against **20 households × 500 Tasks per household**, with a **proposed 500 ms p95 target** for the pilot API, evaluated in a documented no-cache profile. HO-084 explicitly requires an unmet target to be resolved or to block promotion pending a recorded scope/capacity decision. Before first baseline: approve/clarify target endpoints, query and hardware profile, statistical run method, acceptable error rate, query-count limits and handling of noise; save this decision.

## Backup, restore and failure recovery (T190 / HO-038/039)

Backup job success is **not** proof of restorability. The planned runbook and isolated restore drill must:

1. Identify an encrypted, integrity-checked, off-host backup from the intended retention set. The current HO-038 baseline prescribes nightly encrypted off-host backups, **seven-day retention without extended weekly copies**.
2. Restore into a fresh isolated PostgreSQL instance with recorded tool, backup ID, keys/prerequisites, compatible application image and migrations. Never test by overwriting production.
3. Check recovered households, memberships, Responsibilities, Tasks, checklists, historical actor fields and relevant database constraints; exercise real login and a core two-user Task workflow.
4. Follow the HO-038 **deletion-replay safety** contract: reapply confirmed post-backup deletions from a separately protected **current** durable deletion register; discard restored sessions/invitations; verify affected identities/households. If register currency cannot be established, **do not reopen traffic**.
5. Record backup data age, actual recovery duration, application/DB health, smoke outcomes, failures and residual limitations; rehearse process restart, DB outage and release recovery as separately applicable.

**M0 recovery objectives (accepted 15.10.2026):** **RPO ≤ 24 hours** and **RTO ≤ 60 minutes**. These are acceptance objectives, **not** achieved measures or service guarantees. The RPO must be assessed from the newest actually recoverable, integrity-verified backup; a nominal nightly schedule alone cannot prove compliance after failed or missed backups. RTO measurement includes safe application recovery, restoring/revalidating deletion-register safeguards, rejecting restored sessions/invitations and completing the defined smoke gate before reopening traffic. Record actual recoverable data age and elapsed restoration time in T190 evidence. An unmet objective blocks release until repaired or an explicit authorized baseline/capacity decision is recorded; do not silently relabel it as successful.

## Browser, localization and accessibility (T120/T130)

Verify at least one complete two-partner workflow end-to-end plus negative cases: unauthorized/expired session, stale update, network failure and visible conflict recovery; unsupported M1/M2 controls must not appear operative. Exercise locale detection/manual change, locale-specific date rendering, unchanged user-generated text and long German labels in **English, Ukrainian and German**. Record a reproducible primary Chromium desktop/mobile viewport, targeted Firefox/Safari checks and actual browser versions/OS used.

For essential accessibility: keyboard-only navigation, visible and logical focus, modal focus return, associated labels, meaningful error feedback, non-color-only status, responsive layout, text zoom/contrast and where available an assistive-technology smoke check. Automated accessibility scans help but **do not establish comprehensive WCAG conformance**. Use a proportional risk-driven matrix rather than every browser × locale × device combination.

## Evidence and reporting

Every mandatory verification record should make **expected versus actual** auditable:

| Field           | Required content                                                                                                       |
| --------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Trace IDs       | Concrete test ID, `/Tnn/`, `/Fnn/`, LH/FR/NFR, applicable HO and exact AC revision                                     |
| Purpose         | Invariant, expected positive/negative outcome and relevant risk                                                        |
| Tested artifact | Commit SHA / release tag / immutable image digest where applicable                                                     |
| Environment     | OS/browser/version, JDK, Spring/DB version, profile, dataset seed, locale, timezone and Clock when relevant            |
| Execution       | UTC/date and time, duration, author/CI run, exact command or procedural steps                                          |
| Result          | `PASSED`/`FAILED`/`BLOCKED`/`NOT RUN`/`NOT IMPLEMENTED`/`SKIPPED` as appropriate                                       |
| Evidence        | JUnit XML, CI link, Playwright trace/screenshots, sanitized logs, persisted DB end-state, k6 metrics, restore manifest |
| Follow-up       | Defect ID, reason, owner, retest revision and actual outcome if not passed                                             |

Store implementation evidence in versioned or CI-retained artifacts, e.g. `docs/evidence/` with an index from the release manifest. Never include passwords, session cookies, full invitation URLs, unredacted tokens or actual household personal data. A screenshot alone cannot prove persisted state; a green unit suite alone cannot prove integrated application behavior.

## Risk-to-test linkage

| Risk                                 | Verification/control linkage                                                                                                              |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **R-001** schedule                   | Weekly actual/remaining forecast, prerequisite/AC status, critical-path and release evidence review (management control, not a unit test) |
| **R-002** Spring/React complexity    | Early thin slices, startup/config/transaction tests, negative UI/API cases and logged technical blockers                                  |
| **R-003** unauthorized access        | T20/T30/T140/T150/T170; distinct households, app-layer and API authorization, no unauthorized side effects                                |
| **R-004** frontend effort            | T120/T130; mobile/locale/error/accessibility verification during slices, time forecast                                                    |
| **R-005** public hosting uncertainty | Deployment/HTTPS/recovery evidence and documented instructor clarification; confirm applicable public-hosting requirements                |
| **R-006** concurrency/atomicity      | T10/T40/T70/T140/T190; real PostgreSQL, deterministic races, rollback, committed-state invariants                                         |

A tested mitigation does not automatically close a planning risk; update its owner, likelihood/impact and trigger evidence during the documented review.

## Remaining decisions

| ID        | Decision before execution                                                                                                                                               | Owner / tracking   |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| **TS-01** | Current Maven naming and commands are documented above; implement and verify the CI jobs                                                                                | HO-008/015         |
| **TS-02** | Confirm browser/OS/mobile viewport matrix and accessibility test tools/assistive-technology availability                                                                | HO-083; T130       |
| **TS-03** | Approve exact performance workload/endpoint(s), proposed 500 ms p95 target, error and SQL-query thresholds, hardware/duration                                           | HO-084; T160       |
| **TS-04** | **RPO ≤ 24h and RTO ≤ 60m accepted 15.10.2026**; still verify deletion-register freshness and end-to-end recovery against these targets through an actual restore drill | HO-038/039; T190   |
| **TS-05** | Build the separate `/F/ → /T/ → HO-AC → test → evidence` traceability skeleton and mark genuinely unimplemented cases                                                   | P05a / BRP-D03/D04 |
| **TS-06** | Record BRP-MS2 top-3 risks, actual/remaining capacity, GO/REVISE/NO-GO and real reviewer status                                                                         | P05a / AP9.2–9.4   |

The remaining items are tracked through their implementation and planning tickets. Release approval requires the evidence described above.

## Sources

- [Requirements inventory](requirements.md): FR/NFR, product acceptance criteria and release assignments.
- [Risk register](risk-register.md): security, concurrency, delivery and browser risks.
- [Product decision register](decision-register.md): accepted behavior and unresolved implementation decisions.
- Implementation backlog: HO-008, HO-038/039, HO-083/084/086/087 and their acceptance criteria.
- Course project submission draft: scenario groups, traceability and reviewer milestones.
- M0 daily execution plan: capacity and implementation dependencies.

The backlog, submission draft and execution plan are planning sources maintained outside this repository. Recovery objectives were accepted on 15.10.2026; the performance workload remains a proposal pending TS-03.
