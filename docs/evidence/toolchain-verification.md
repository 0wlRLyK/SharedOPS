# SharedOPS — Toolchain Verification

**Status:** Passed backend smoke and tooling checks; feature coverage pending.\
**Updated:** 18.10.2026
**Scope:** M0 backend foundation and local development tooling

## Environment

The recorded run on 18.10.2026 used macOS 15.4.1 (arm64), Eclipse Temurin JDK 25.0.4.1, Maven Wrapper 3.10.0, Node.js 22.9.0, Corepack pnpm 12.14.0, pre-commit 4.7.0 and Docker engine 29.4.1. PostgreSQL containers used `postgres:18.6-bookworm`.

The project requires **JDK 25 LTS**. The recorded vendor and patch identify the test environment. Node.js 22.9.0 is below Commitlint's declared requirement of >= 22.12.0; new development environments must meet the [documented requirements](../stack.md#environment-requirements), even though message validation succeeded in this run.

## Backend verification

`make verify` completed successfully on 18.10.2026. Maven reported `BUILD SUCCESS` with three Surefire tests and three Failsafe integration tests; all six passed with no failures, errors or skipped tests.

| Test                               | Observed result                                                                  |
| ---------------------------------- | -------------------------------------------------------------------------------- |
| `SharedopsBackendApplicationTests` | Application context started against PostgreSQL                                   |
| `JsonSerializationTest`            | JSON serialization and deserialization passed                                    |
| `ModulithStructureTest`            | `ApplicationModules.verify()` passed                                             |
| `FlywayMigrationIT`                | Test migration applied to empty PostgreSQL; second migration run made no changes |
| `PersistenceSmokeIT`               | Test entity persisted, flushed and reloaded                                      |
| `OpenApiDocumentationIT`           | Authenticated OpenAPI request returned the test endpoint and response schema     |

Surefire reports are generated in `backend/target/surefire-reports/`; Failsafe reports are generated in `backend/target/failsafe-reports/`. They are local build artifacts and are replaced by later runs. No commit SHA is available because the repository has no initial commit yet.

## Formatting and hooks

| Check                                              | Observed result                                    |
| -------------------------------------------------- | -------------------------------------------------- |
| Spotless                                           | No Java files required formatting changes          |
| Checkstyle                                         | Zero violations                                    |
| `corepack pnpm run format:check`                   | Passed for the recorded backend/toolchain snapshot |
| `pre-commit run --all-files` with pre-commit 4.7.0 | All applicable file hooks passed                   |
| commit-msg: valid Conventional Commit              | Accepted                                           |
| commit-msg: `invalid message`                      | Rejected with `subject-empty` and `type-empty`     |

The commit-msg hook was checked separately with `pre-commit run commitlint --hook-stage commit-msg --commit-msg-filename <message-file>`. It is verified; the earlier pending status was stale. File-hook execution alone does not exercise this hook.

Both message cases also passed their expected outcomes with pre-commit 4.7.0 using the repository's local-hook definitions in an isolated configuration. Direct Commitlint execution produced the same results. The system `pre-commit` executable is version 3.3.3; use an installation meeting the documented minimum.

## Coverage limits

These tests cover the backend foundation. The migrations, persistence entity and OpenAPI endpoint are test fixtures. Household authorization, account/session lifecycle, domain concurrency, production migrations, browser workflows, performance and restore objectives need their own implementation evidence under the [test strategy](../test-strategy.md).

CI and frontend verification remain pending. Update this record when the tested artifact or environment changes, keeping execution results separate from planned checks.
