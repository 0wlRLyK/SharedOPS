# Data Model: Individual Responsibility

## Responsibility

`id` UUID; `household_id` UUID references household; `title` nonblank text (preserve original content); optional `description` text (null and empty distinct); `mode` exactly INDIVIDUAL; `status` ACTIVE, PAUSED or ARCHIVED; `owner_membership_id` non-null UUID; `version` nonnegative long. Unique `(id, household_id)` remains the existing task-FK target. A composite FK from `(owner_membership_id, household_id)` to a unique membership `(id, household_id)` proves same-household historical owner. Owner membership identity resolves to an account for responses; keep stable membership identity when inactive. Database checks reject invalid enum/status/version and current responsibilities with invalid owner. Database coordination described in plan makes active eligibility durable across concurrent membership updates. Missing/invalid legacy details cannot be inferred: fail V9 when V1 placeholder rows exist.

| From             | Command | To       | Owner requirement                                               |
| ---------------- | ------- | -------- | --------------------------------------------------------------- |
| New              | create  | ACTIVE   | selected active same-household membership                       |
| ACTIVE           | pause   | PAUSED   | remains active                                                  |
| PAUSED           | resume  | ACTIVE   | remains active                                                  |
| ACTIVE or PAUSED | archive | ARCHIVED | retain historical owner                                         |
| ARCHIVED         | restore | ACTIVE   | historical owner if still active or explicit active replacement |

All other transitions, including same-state and COMPLETED, reject; archived detail edit/transfer rejects. Rejected and stale requests leave row and facts unchanged. Unchanged detail and same-owner transfer are current-version no-ops. Every mutation of an existing responsibility checks the supplied version; creation requires no client version and initializes version 0. Successful real changes to an existing responsibility advance exactly once. Restore may select an eligible replacement even when historical owner remains active; any chosen owner must be valid. Restore does not produce an explicit transfer fact unless independently specified.

## Ownership transfer fact

Append-only record: unique fact ID, responsibility ID and household ID, actor account ID, occurrence instant, prior and new membership owner IDs. All relevant IDs are constrained to their household where appropriate, with immutable same-household reference semantics compatible with eventual account-deletion policy. A fact exists only for a successful explicit owner change; row update and fact insertion commit or roll back together. Retain history through archive. Account deletion pseudonymization and last-member purge are HO-85; do not claim implementation here.

## Membership concurrency and future deletion

Application acquires household row first, validates actor and target active membership, then locks/updates responsibility. Database trigger/constraint design must also serialize relevant direct writes with household lock and disallow deactivating an owner of ACTIVE/PAUSED responsibility. Before deactivation, a future deletion workflow must transfer or otherwise resolve current ownership atomically; archived membership links may remain inactive. Foreign or missing resources yield no data. Race tests prove invariant under both commit orders and inspect committed state.
