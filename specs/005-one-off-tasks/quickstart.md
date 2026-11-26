# Validation guide: HO-18

Prerequisites: Java 25, project-pinned Corepack/pnpm and Maven wrapper, a running Docker daemon accessible to Testcontainers. Use synthetic accounts and household fixtures only. From repository root:

```sh
make verify
```

Expect Maven Surefire and Failsafe (including production Flyway PostgreSQL integration and architecture tests), Spotless/Checkstyle and pnpm format check to pass. Docker may require the approved elevated environment. Capture final revision, tree, exact command, class-level results and no skipped mandatory cases. For targeted diagnostics use `cd backend && ./mvnw -Dtest=DeadlineTest,DeadlineEvaluatorTest test`; the full verify remains required. Execute changed test targets not covered by fresh full-verification reports separately.

Exercise these scenarios through the test suite using controlled clock and committed-state queries: title-only standalone -> NORMAL/unassigned/NONE; linked default owner versus explicit unassigned or other member; owner transfer does not reassign earlier task; paused/archived/foreign context and inactive/foreign assignee are rejected without committed changes. Patch fields and clear nullable values, then verify stale and terminal edits change neither task nor audit. Synchronize competing operations with database locks/latches, asserting valid serialized results and exactly one winning stale-version edit. Check date next-day start and date-time strict-after boundaries including DST gap/overlap, NONE/terminal and nanosecond persistence. Verify foreign and nonexistent identifiers produce the same error and direct application entry points enforce household membership. Test empty and populated migration paths and existing checklist persistence.

Consult [API contract](contracts/task-api.md) and [data model](data-model.md) for exact payloads and invariants. Record each acceptance-criterion mapping and any failed, skipped or blocked check; do not claim success from plans or prior-tree receipts.
