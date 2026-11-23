# Validation guide: HO-11

## Prerequisites

Use the project-pinned JDK 25, Maven wrapper, Corepack/pnpm, and running Docker-compatible runtime. Run from repository root. Use only synthetic accounts and tokens; do not copy tokens, cookies or full URLs into logs or evidence. The plan and spec must be approved before implementation.

## Validation after implementation

1. Run `make verify`. Expect unit and Failsafe suites, PostgreSQL production Flyway migrations, Java formatting/lint and pnpm formatting to pass; record exact candidate commit/tree and command result. Run any changed test target not covered by a fresh receipt separately under the workflow's target protocol.
2. In PostgreSQL integration tests, create a household and invitation, accept as an eligible account, then query committed state from a fresh connection: exactly two active members, the invitation consumed and no plaintext token stored. Repeat acceptance and verify no additional changes.
3. With a controlled clock, check just-before and exactly-at expiry (24 hours after creation); create separate revoked, unknown and consumed cases. Each rejection leaves membership and invitation state unchanged. Hold the household row lock in one transaction, begin acceptance before expiry in another, advance the controlled clock to the deadline while it waits, then release the lock; acceptance must reject and committed state remain unchanged. Verify authorized revocation and foreign/absent authorization without disclosing household data.
4. Coordinate independent service transactions using latches/barriers and bounded timeouts, not sleeps: two invitations for the final slot, two accepts of one token, revoke versus accept, and household create versus accept for one account. After both finish, inspect committed rows; assert the relevant unique constraints, no over-capacity, no orphan household and no loser-caused consumption.
5. Exercise a real failure between membership insert and consumption through the proxied service, then inspect committed state. Verify direct application callers cannot bypass authorization and test DB constraints through direct bypass writers, including rejection of an active membership with a NULL slot.
6. Check HTTP authentication/CSRF, response schemas and error parity against `docs/api/openapi.yml`, and inspect captured synthetic logs for absence of token and complete invitation URL on success and failure. Verify no-store responses and no secret-bearing request path or Location header.
7. Test Flyway upgrade from the existing production schema with one and two active memberships, plus an over-capacity preexisting fixture that fails without partial mutation. Record migration/deployment compatibility notes.

Record each applicable spec scenario, command, observed status, environment and tested revision. Missing, skipped or failing mandatory evidence is not passing. Real deletion/replacement workflow remains HO-85; financial-stage changes remain HO-92.
