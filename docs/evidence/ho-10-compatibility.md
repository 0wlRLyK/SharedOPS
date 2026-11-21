# HO-10: household lookup and V7 compatibility

**Status:** Compatibility design and provisionally integrated implementation; final verification for the current batch candidate is **NOT RUN** and downstream sign-off is **OPEN**. Earlier candidate checks are historical evidence only. This record does not assert deployment or completed acceptance. Source: [plan](../../specs/001-household-creation-isolation/plan.md), [contract](../../specs/001-household-creation-isolation/contracts/household.md), and [architecture](../architecture.md).

## Named Java interface

The `household::lookup` named interface and propagated `HouseholdId` remain. The candidate removes `boolean existsById(HouseholdId)` and exposes `boolean isAccessible(HouseholdId)` instead. This is a **source, binary, and semantic break**: the former answered global existence; the latter answers whether the current authenticated account has an active membership in the requested household. Missing identity, null, absent, foreign, and inaccessible households yield `false`; no identity means denial before persistence access. There is no identity-free compatibility overload.

Consumers must update and recompile against `isAccessible`, establish an authenticated server-side context for each call, and handle `false` as inaccessible rather than proof of nonexistence. A privileged or background identity is not defined by this change and needs a separately approved policy. At the T001 baseline, [architecture](../architecture.md#4-public-module-contracts) records no identified in-repository production caller; the task module declares the permitted `household::lookup` dependency in [package-info.java](../../backend/src/main/java/dev/sharedops/task/package-info.java). This does not prove there are no external consumers. Identify downstream callers, agree migration and obtain their separate compatibility sign-off before integration or deployment. **Downstream identification and sign-off: OPEN.**

## V7 migration

[V7](../../backend/src/main/resources/db/migration/V7__household_memberships.sql) checks for existing `households` rows and raises an exception before its schema changes if any exist. It then adds required household settings and membership storage, including account and household foreign keys and a partial unique index for one active membership per account. V1–V6 remain immutable.

Before an upgrade, determine whether the target database contains any household rows and whether dependent task or responsibility data exists. If households exist, stop: do not delete or silently assign existing data, invent owners or currencies, or bypass the guard. A separate owner-approved migration/remediation and preservation plan is required before retrying. The candidate does not implement that remediation. Empty and populated upgrades must be verified using production Flyway migrations and committed-state/schema observations.

## Planned verification and outstanding evidence

| Check                                                             | Status                           | Purpose                                                                                                                  |
| ----------------------------------------------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `HouseholdLookupContractTest` and `AuthorizedHouseholdLookupTest` | NOT RUN for this batch candidate | Named interface shape and boundary behavior, including denial before persistence.                                        |
| `HouseholdLookupIT`                                               | NOT RUN for this batch candidate | Real exported bean, authorized and denied calls, committed-state comparisons, concurrency and actor-filter sensitivity.  |
| `HouseholdMigrationIT` and existing persistence integration tests | NOT RUN for this batch candidate | Empty upgrade, populated guard, preserved related data, foreign keys and active-membership uniqueness.                   |
| Whole-batch local checks and independent review                   | NOT RUN                          | Establish results on the final candidate revision and tree; record actual commands, environment and outcomes separately. |
| External consumer inventory and compatibility sign-off            | OPEN                             | Confirm downstream impact and migrate consumers before integration/deployment.                                           |
| Existing-data remediation                                         | OPEN when household rows exist   | Agree and approve a separate preservation and migration procedure before retrying V7.                                    |

Historical checks on a different worktree or revision are not evidence for this batch candidate. The orchestrator records observed outcomes and the tested revision after final verification; this document does not assert that the tests passed or that the feature was accepted.

## Scope allocation

HO-10 covers initial creation and the operations actually implemented in M0. Real invitation acceptance and create/join races belong to [HO-11](https://linear.app/home-0ps/issue/HO-11/implement-single-use-invitations-with-race-protection); financial-history currency changes belong to [HO-50](https://linear.app/home-0ps/issue/HO-50/implement-financial-request-deduplication-and-currency-freezing). Future operations own their corresponding isolation evidence. Those items are separate from current HO-10 acceptance, while the compatibility and migration risks above remain applicable.
