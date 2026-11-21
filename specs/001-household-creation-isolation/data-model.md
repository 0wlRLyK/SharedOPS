# Data model: HO-10

**Status**: design record for the M0 candidate, pending review against the reconciled plan. It does not by itself prove migration execution. [Plan](plan.md) | [Specification](spec.md)

## Entities and invariants

| Entity                  | Proposed data and rules                                                                                                                                                                                                                                                   | Relationships                                                                                                    |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Account                 | Existing UUID identity; authentication comes from the existing account/session context.                                                                                                                                                                                   | Zero or more historical memberships; at most one active membership.                                              |
| Household               | Existing UUID plus required normalized name (1–100 Unicode code points), optional unchanged icon identifier (nonblank, at most 100 code points), explicit supported currency and required valid IANA timezone. `Europe/Berlin` applies only when creation omits timezone. | One or more memberships after successful creation; existing task/responsibility references remain intact.        |
| Membership              | Account ID, household ID and active/inactive state sufficient to distinguish current access from historical association.                                                                                                                                                  | Foreign keys to account and household; partial unique index on account ID for active rows.                       |
| Household-scoped object | Existing or future object whose household determines access.                                                                                                                                                                                                              | Foreign relationships must stay within the same household; validate membership and resource scope before access. |

The exact representation of membership lifecycle and names is a design proposal, not a grant to implement invitation or deletion flows. Initial creation inserts one household and one active membership as a single transaction. The database constraint handles conflicting concurrent create/join operations; translate a unique violation only after the failed transaction is rolled back. The constraint does not replace authorization checks.

## Settings and state transitions

- `name` is trimmed at the edges; nonblank after trimming, 1–100 Unicode code points. `icon` absent/null means no icon; nonblank strings up to 100 Unicode code points remain unchanged. No icon catalogue validation in M0. Household data is immutable after creation in M0; membership changes through HO-11 are separate.

- Creation: require explicit valid currency; omitted timezone defaults to `Europe/Berlin`, explicit null/empty is invalid in this proposed contract. Timezone must be a recognized IANA zone ID; offset-only identifiers do not satisfy the IANA requirement.
- Currency: creation requires one of `EUR`, `UAH`, `USD`, `GBP`, `PLN`, `CHF`, `CZK`, `DKK`, `NOK`, `SEK`; all other values are invalid. Do not release currency changes until the financial-history check is agreed and implemented. The conditions for future changes are owned by HO-50; no unapproved policy is assumed. Financial-history policy remains open in [research](research.md#currency-and-financial-history).
- Timezone: immutable after creation in M0, including explicit attempts to submit a new timezone.
- Membership: inactive state conveys no household access. The representation must support future invitation flows without weakening the active-per-account constraint; their lifecycle remains HO-11 scope.

## Migration and compatibility

Current production migrations V1–V6 are not edited. A following Flyway migration checks for any pre-existing `households` rows and fails before changing data when present. Existing dependent rows are never removed or silently reassigned. An owner-approved separate remediation is a prerequisite for upgrade on such a database. For an empty household table add non-null settings, the membership table, foreign keys, relevant checks and the active-membership unique index. Migration tests must inspect failure and successful committed states using production Flyway, not Hibernate schema generation. Hibernate remains `ddl-auto=validate`.
