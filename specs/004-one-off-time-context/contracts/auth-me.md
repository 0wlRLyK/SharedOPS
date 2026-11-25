# Current Account Context Contract

## Application boundary

`CurrentContextService.get()` obtains account UUID from existing `CurrentAccount.authenticatedAccountId()` and rejects absent identity through the existing unauthenticated mechanism. It calls `CurrentContextStore.findForAccount(UUID accountId)` returning `Optional<CurrentContext>` where `CurrentContext(UUID accountId, String email, HouseholdSnapshot household)` has a nullable household. The persistence adapter reads current account and optional active membership/household using one statement scoped by account ID, with `active` in the join condition. No request-derived identity or household ID is accepted. Missing account is authentication failure without account or household disclosure. An inactive or missing membership yields null household. Projection fields are current stored values.

## HTTP and OpenAPI

Authenticated `GET /api/v1/auth/me` returns 200 JSON with required `account.id` UUID string, `account.email`, and required nullable `household`. A non-null household contains required UUID `id`, `name`, nullable `icon`, `currency` and `timezone`. Unauthenticated request returns existing `401` Problem Details with no account or household data. Every response for this route has `Cache-Control: no-store`, including a rejection before controller invocation; preserve existing error status/content and session/CSRF behavior. Update `docs/api/openapi.yml` to express success, nullable fields, 401/error and header. This is an additive endpoint; existing operations and clients remain compatible, no DB migration.

Application tests use synthetic actors to show no account selection by request, absent actor rejection and correct store inputs; PostgreSQL integration uses production migrations, distinct accounts/households and active, inactive and absent membership, asserts persisted current email and household fields, and verifies no foreign data. HTTP tests cover actual authentication/session behavior, both status paths and cache header, nulls and field shapes; contract test matches OpenAPI including 401 and headers. No credentials, cookies or personal data appear in logs/evidence.
