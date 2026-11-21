# Planned verification guide: HO-10

**Status**: verification instructions for the current M0 candidate, not evidence that checks have passed. [Plan and acceptance trace](plan.md#acceptance-trace-and-planned-evidence)

## Preconditions

- Owner approves the feature specification, detailed plan and contracts; finish the workflow checklist, tasks and analyze gates before implementation.
- HO-15 and HO-87 are not prerequisites to start, per owner decision 2026-11-19. Record unavailable CI gates. Check legacy household data and obtain an approved separate remediation before migrating any nonempty household table.
- Use Temurin JDK 25, committed Maven Wrapper, Node and pinned pnpm, Docker/Testcontainers and PostgreSQL 18 per [stack](../../docs/stack.md). Use only synthetic accounts and households. No real passwords, tokens or private data in evidence.

## Planned checks

From repository root on the tested revision:

```sh
git rev-parse HEAD
make check
make test
make verify
```

`make verify` needs Docker and runs PostgreSQL integration coverage and formatting. Record actual prerequisites, command, exit status, test reports and skips; a failed or unrun mandatory check is an open gate, not a pass. `make check` and `make verify` rely on pinned pnpm; report environment failures rather than weakening checks.

Once relevant feature tests exist, additionally run focused unit, contract, integration and concurrency tests by their actual test names. For every criterion in the [trace table](plan.md#acceptance-trace-and-planned-evidence), record test identifier, command, result, revision and environment. Expected observations:

1. Real HTTP creation succeeds with one committed household and membership, 201, Location and id/name/icon/currency/timezone; exercise name trimming and Unicode length boundaries, icon absent/null/blank/overlength and unchanged nonblank value. Invalid settings and a second active membership (409 ACTIVE_HOUSEHOLD_EXISTS) are rejected without changes.
2. Production Flyway upgrades an empty database; with a pre-existing household, migration stops and existing data and schema remain unchanged.
3. A genuine fault after a real write in the proxy-invoked creation service leaves neither record committed. Read using a separate transaction after service completion, without relying on a transactional test rollback. Demonstrate the regression test detects bypassed transaction interception.
4. Coordinated competing creations yield at most one active membership and no orphan household. HO-11 owns and verifies actual create/join concurrency.
5. For each actual scoped HTTP operation, parameterize allowed, foreign, absent and inactive-member cases; compare externally observable foreign/absent responses and inspect committed side effects. Call the real named `HouseholdLookup.isAccessible` interface directly through its application bean with trusted authentication contexts: active owner true; foreign, absent, inactive, null and no-auth false; no-auth performs no persistence read. Snapshot household and membership rows before and after denials with fresh committed reads. Verify concurrent and interleaved two-actor isolation; verify an isolated intentional actor-filter bypass makes the test fail, then restore and rerun. Repeat other exported operations directly through their application beans.
6. Explicit/default/invalid timezones, forbidden edits and initial currency selection are verified against the approved contract, including acceptance of all ten supported values and rejection of missing, lower-case and unsupported codes. Do not release currency changes until the financial-history check is agreed, implemented and verified; HO-50 owns currency-change acceptance. Future input classes and operations are verified by their owning issues; they are not acceptance criteria of HO-10.

Record OpenAPI compatibility and migration review, any missing coverage, and review outcome in the final evidence. Feature is not complete while a mandatory criterion is unverified or unresolved without an owner-approved baseline decision.

## Named interface compatibility check

Verify architecture tests still recognize `household::lookup` and propagated `HouseholdId`, search for and migrate all old `HouseholdLookup.existsById` call sites, and document that replacing the method with `isAccessible` intentionally breaks source and binary compatibility for unknown downstream clients. Check compiled module contracts contain no public identity-free existence method or bypass overload. Do not claim unknown external consumers have been migrated without separate evidence.
