# Feature Specification — Current account and household

**Status** Draft, pending owner review

**Scope** Add the authenticated `GET /api/v1/auth/me` endpoint. This draft records the requested behavior; it does not approve implementation.

## User scenario

An authenticated client retrieves the current account and its household so it can establish the user's current context. An account without an active household membership can still retrieve its account information.

## Acceptance scenarios

1. Given an authenticated account with an active household membership, a request returns `200 OK` and the account and that household's data in the shape below.
2. Given an authenticated account without an active household membership, a request returns `200 OK`, the account data and `"household": null`.
3. Given an unauthenticated request, the endpoint returns `401 UNAUTHORIZED` and no account or household data.
4. The endpoint's responses include `Cache-Control: no-store`.
5. The household is determined from the authenticated account's active membership; another account's household data is not exposed. An inactive membership does not supply the household.

```json
{
  "account": {
    "id": "uuid",
    "email": "user@example.test"
  },
  "household": {
    "id": "uuid",
    "name": "Our home",
    "icon": null,
    "currency": "EUR",
    "timezone": "Europe/Berlin"
  }
}
```

`account.id` and `household.id` are UUIDs serialized as strings. `household.icon` may be null. The response contains the current stored values rather than the example values. The existing API error contract applies to the unauthorized response.

## Boundaries and verification

- This is a read-only endpoint; authentication, account creation and household membership changes are outside its scope.
- Document the public response and error contract in `docs/api/openapi.yml` as part of future implementation and assess compatibility with existing clients.
- Future HTTP and application-boundary tests must cover the acceptance scenarios, including distinct accounts and households, an inactive membership, nullable icon, actual response fields and the cache header on success and unauthorized responses. Use synthetic fixtures and the project's applicable production-migration integration tests.
- The full Spec Kit workflow applies to this public API and authorization change. The owner must approve the specification and plan before implementation or test files are edited.
