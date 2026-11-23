# Implementation Plan: Single-use household invitations

**Branch**: `feat/HO-11-workflow` | **Date**: 2026-11-22 | **Spec**: [spec.md](spec.md)

**Input**: HO-11; owner-approved spec SHA-256 `0924479f4f53447a3582947ea3349920d083901c65a74cc2d900f11a2c59d7ca`.

## Summary

Deliver creation, revocation and acceptance of 24-hour single-use household invitations. Store only a digest of a random bearer token. Serialize acceptance against revocation and competing joins; commit membership and consumption together. Database constraints enforce one active household per account and two active memberships per household even for concurrent writers. See [research](research.md), [data model](data-model.md) and [contract](contracts/invitations.md).

## Technical Context

**Language/Version**: Java 25 (Temurin)  
**Primary Dependencies**: existing Spring Boot 4.1.1 MVC, Security, JDBC, Flyway; no new dependency  
**Storage**: PostgreSQL 18, production Flyway migrations  
**Testing**: JUnit, Spring Boot, Testcontainers PostgreSQL; Surefire and Failsafe  
**Target Platform**: server application  
**Project Type**: modular monolith HTTP service  
**Performance Goals**: no new performance target; minimize critical-section work and avoid external calls in transactions  
**Constraints**: authenticated session and CSRF; application-boundary membership checks; one active household per account, two active members per household; no invitation secrets or complete URLs in logs; controlled clock and synchronized races  
**Scale/Scope**: M0 household invitation lifecycle; no frontend delivery, account deletion or financial stages.

## Constitution Check

**Before research and after design: PASS**, subject to verification and owner approval. Trace HO-11/FR-HH-02–05/NFR-08/AC-01 and each approved spec scenario to tests and candidate revision. Preserve module boundaries and use app ports; check active membership before create/revoke side effects; protect private data. Extend authoritative OpenAPI compatibly; migrate existing membership rows safely. Verify against production migrations and actual proxy-visible service operations with committed-state assertions and controlled concurrency. Work is isolated, full Spec Kit applies, and owner approves this plan before implementation.

## Project Structure

### Documentation

```text
specs/002-single-use-invitations/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/invitations.md
└── tasks.md                  # subsequent task-design stage
```

### Source and tests

```text
backend/src/main/java/dev/sharedops/household/{api,application,domain,infrastructure}/
backend/src/main/resources/db/migration/
backend/src/test/java/dev/sharedops/household/
backend/src/test/java/dev/sharedops/security/
docs/api/openapi.yml
```

**Structure Decision**: Extend the existing household module and its app ports/JDBC adapters; current implementation uses `dev.sharedops` despite the issue's older `com.homeops` path. Keep domain logic framework-independent; no cross-module repository access.

## Design and Integration

1. Add additive migration for invitation records and bounded membership slots. Preflight existing active memberships, abort migration on over-capacity data, and backfill existing rows deterministically; preserve active-account uniqueness and existing households. Require `slot IS NOT NULL AND slot IN (1, 2)` for active rows, and null for inactive rows; PostgreSQL CHECK accepts UNKNOWN, so explicit `IS NOT NULL` is essential. Add unique `(household_id, slot)` for active rows. All membership writers allocate under the household row lock and constraints reject races or bypasses. Inactive memberships have no occupied slot. Migration tests exercise existing single/two-member rows and violation rejection; do not silently repair invalid data.
2. Generate a 32-byte `SecureRandom` token encoded base64url without padding. Store unique SHA-256 digest only; expose the plaintext exactly once in the create response. Never include it in URI, logs, exception messages, `toString`, evidence or tracing. Request/response bodies carrying the secret are not logged; return `Cache-Control: no-store`. Controlled `Clock` defines `expires_at = created_at + 24h`; at/after deadline is invalid.
3. Create/revoke operations authenticate and authorize active household membership within the transaction before mutation, using a common not-found response for foreign/absent households or invitation IDs. Acceptance uses a token in a POST body, generic invalid result for unknown/expired/revoked/consumed token, and no household details on failure. Define exact HTTP mappings in the contract and add them to authoritative OpenAPI with existing error conventions. Prevent accidental query/path token logging.
4. Acceptance starts a proxy-visible transaction. Resolve token digest to household without revealing its contents, lock household row, then lock/re-read invitation; perform all validity checks including current time after acquiring locks, check active membership, allocate slot, insert active membership, then conditionally consume the invitation and commit. An exception rolls back both. Revocation locks in the same household-then-invitation order and serializes with acceptance. Existing account partial unique index remains the final authority against creation-versus-acceptance race; translate only known constraint violations _outside_ the failed transaction and do not swallow unrelated errors. A conflict rolls back a newly created household and a joining transaction's consumption. Test exact race outcomes, including full household, same token, revocation and expiry boundary.
5. Keep authority on the server: no user-supplied account identity or capacity; only current account and active membership. Never retain request identity in singleton mutable fields. Design port interfaces so no controller-only enforcement exists.

## Compatibility and Migration

New endpoints are additive; document precise schemas/statuses/security in OpenAPI and contract tests. Existing household endpoints and response schemas remain unchanged. Migration adds invitation table and membership slots without replacing records or rewriting old migrations. Refuse invalid preexisting >2-active data with a clear operator action; preserve original data transactionally on failure. Database uniqueness protects both old and new application writers, but deployments must apply migration and compatible application together: older code cannot insert active memberships without slots after migration. Use a coordinated upgrade with writes stopped; rollback requires restoring compatible application/schema from a verified backup, not rewriting Flyway history. No existing invitations exist to migrate.

## Verification and AC Trace

Use `make verify` (unit, PostgreSQL/Failsafe, Spotless, Checkstyle, pnpm format) on final exact candidate; run any missing changed-target tests under workflow targeting. Record command, result, revision and sanitized evidence; all checks below are **NOT RUN** at planning.

| Acceptance / invariant                                                        | Test level and proof                                                                                                                                                                                                                                                                                        | Command / status        |
| ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| Generate secret, hash-only persistence; 24h boundary; no secrets/URLs in logs | Unit with fixed clock and synthetic tokens; PostgreSQL persistence; captured logs across success/failure and no-store/body handling                                                                                                                                                                         | `make verify` / NOT RUN |
| Valid join creates membership and consumes once                               | Real service PostgreSQL IT asserts both committed effects from a fresh connection; retry rejected with unchanged state                                                                                                                                                                                      | `make verify` / NOT RUN |
| Expired, revoked, consumed, invalid tokens rejected                           | Controlled-clock/revocation IT; assert invitation and membership state after each denial, including exactly at expiry; hold the household lock, start acceptance before expiry, advance controlled clock to the deadline while blocked, release the lock and assert rejection and unchanged committed state | `make verify` / NOT RUN |
| Explicit revocation and foreign authorization                                 | HTTP and direct service negative tests with distinct households, no side effects, indistinguishable absence/foreign result; successful revocation prevents acceptance                                                                                                                                       | `make verify` / NOT RUN |
| Failure atomicity                                                             | Inject a real failure between persistence steps through proxy-visible transaction, verify neither membership nor consumption committed; include bypass-detecting control                                                                                                                                    | `make verify` / NOT RUN |
| Final slot and maximum two                                                    | Independently connected coordinated transactions, two distinct valid invitations; assert at most one joins, committed count <=2, loser does not itself consume; database constraint test with bypass writers, including direct active NULL-slot insertion rejection                                         | `make verify` / NOT RUN |
| Already-member and create-versus-join                                         | Committed-state IT for active membership rejection; controlled create/accept race for same account with <=1 active membership, no orphan or losing consumption                                                                                                                                              | `make verify` / NOT RUN |
| Same-token and revoke-versus-accept races                                     | Coordinated independent transactions; one winner at most, valid serialized lifecycle and unchanged losing side effects                                                                                                                                                                                      | `make verify` / NOT RUN |
| Contract and migration                                                        | OpenAPI/security/CSRF HTTP tests and clean/upgrade Flyway PostgreSQL tests, preserving existing rows and constraints                                                                                                                                                                                        | `make verify` / NOT RUN |

## Decomposition

Design task DAG so migration and shared ports/contracts precede dependent application operations; create/accept/revoke, HTTP/OpenAPI, and tests can be independent only where file/resource scopes and interfaces permit. Keep behavior and regression tests together. Test execution and whole-code review follow the workflow's batch protocol. No scope changes or implementation before plan approval.

## Complexity Tracking

No constitution violations are proposed. Slot allocation and unique index add schema complexity to make the two-member invariant enforceable under all concurrent membership writers, rather than relying solely on an application count.
