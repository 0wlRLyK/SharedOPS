# ADR-0006: Preserve Financial History Across Partner Changes

Date: 13.10.2026
Status: Accepted

## Context

Replacing a partner must not transfer old debt or reveal previous financial histories to the new partner. This M2 product decision replaces the earlier invitation-blocking proposal (D15). It covers HO-092, FR-FIN-13–18, AC-20–22 and the M2 extension of HO-034 and HO-049–054.

## Decision

- Each financial stage has immutable historical participants and belongs to one Household. Expenses, splits, Settlements, budgets, allocations and financial audit queries are scoped to that stage.
- Departure/account deletion closes A–B for ordinary financial writes and opens a personal B stage atomically. Invitation creation does not change stages; acceptance closes personal B and opens B–C with mutual balance zero. Every subsequent composition change creates a new stage, including a returning partner. No previous stage is resumed.
- Existing Finance data introduced into this model belongs to its original participants, never inferred from a later replacement's membership. Stage assignment is independent of expenseDate: backdating cannot insert a new Expense into a closed history. Migration and transactional lifecycle mechanics must be specified before M2 implementation.
- Personal stages allow the remaining user's own Expenses and budgets; there is no mutual debt or self-Settlement. Joining a partner does not carry personal expenses, balances, budgets or allocations forward.
- History access derives from participation in that stage, not current Household membership alone. B can access A–B and personal B; C can access B–C only. A deleted account cannot authenticate. If B later leaves, C retains B–C as its participant, but receives no access to A–B or personal B.
- Apply the access boundary to API records, balances, budgets, dashboards, exports, audit and Activity. Shared Task/List/Responsibility links must not expose old financial payloads through reverse references.
- Closed Expenses, splits, budgets and allocations are read-only. Preserve their actual values and original planned date ranges; closure marks lifecycle, not an arbitrary rewrite of dates. No carryover of budget money or allocations.
- The surviving participant may record actual repayments in either debtor-to-creditor direction in a closed shared history, within its outstanding debt. These are one-sided records by B, not verified agreement by A. They affect only A–B, never B–C, personal-stage budgets or old budget spending.
- Erroneous historical repayment records may be annulled with reason and immutable audit. Annulment removes their balance effect while retaining the fact; corrections use a new repayment record. Validate resulting debt/direction and retries under the finance guard; forbid overpayment, self-payments and debt write-off in this scope. Original financial records are not silently edited.
- Preserve Household currency freeze and D08 last-active-member purge. History boundaries do not imply separate Households, separate currencies or immortal retention.

### Module/API boundary

Membership transitions and stage creation/closure commit consistently with financial writes; invitations and deletion cannot race to attach money to the wrong participant set. Finance interfaces must accept/resolve an authorized stage, and reads must distinguish current and historical balances. Idempotency must bind stage identity so replay from an earlier stage cannot write into a new one. Exact schema, migration, locking and wire fields require the M2 technical design; they are not implemented here.

## Alternatives Considered

- Block replacement while financial history exists: rejected because history would prevent replacement indefinitely.
- Create a new Household: rejected because coordination data would need to be migrated.
- Keep successive stages within one Household: selected to preserve history and separate participants.

## Consequences

Stage authorization applies to direct and indirect financial access. Closed records retain their original participants and values. Technical schema, migration, locking and API contracts must be designed before implementation. Existing Household currency and last-member deletion rules remain in force.

## Verification

Implementation is pending. Before M2 release:

- Verify A–B → B → B–C, repeated replacement and returning partner, history access including indirect surfaces, zero new net, immutable old facts, personal spending, no budget carryover, repayment in both directions, overpayment rejection, audited annulment and last-member purge.
- Verify invitation acceptance/deletion/finance-write races, transaction rollback, stage-bound retries, backdating and migration of historical participants.

See the [requirements inventory](../requirements.md), [product decision register](../decision-register.md) and [test strategy](../test-strategy.md) for related requirements and release checks.
