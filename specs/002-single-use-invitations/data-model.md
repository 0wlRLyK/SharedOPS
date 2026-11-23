# Data model: Single-use household invitations

## Invitation

- `id`: UUID primary key, non-secret identifier for authorized revocation.
- `household_id`: required foreign key to household.
- `token_hash`: required unique fixed-length SHA-256 digest of random bearer token; no plaintext storage.
- `created_at`, `expires_at`: required UTC instants; expiry exactly 24 hours after creation.
- `consumed_at`, `revoked_at`: nullable instants; database check prevents both being set. State is derived: consumed, revoked, expired if current instant >= expiry, otherwise usable. No secret or URL in representations.
- An authorized active member can create or revoke; an eligible authenticated account may accept only usable invitation. Acceptance atomically inserts an active membership and sets consumed timestamp. Revocation cannot undo consumption. Multiple unused invitations may coexist; each acceptance rechecks household capacity.
- Constraint checks include valid timestamp order and terminal-state exclusivity; token hashes are unique. A collision is handled without revealing a token or overwriting an existing invitation.

## Membership and household

- Existing membership `id`, `account_id`, `household_id`, `active` remain. Add nullable `slot` with check: active membership requires `slot IS NOT NULL AND slot IN (1, 2)`; inactive membership requires `slot IS NULL`. Explicit non-null is essential because PostgreSQL CHECK accepts UNKNOWN for a null comparison. Partial unique `(household_id, slot)` where active protects two-member maximum; existing partial unique `account_id` where active protects one active household per account.
- A new membership requires household-row lock before selecting a free slot. Household creation and invitation acceptance both use the allocation path; the DB constraints remain decisive if a writer bypasses that path. Lock household first, then invitation; validate and consume in one transaction. No service exposes an arbitrary caller-supplied slot.
- Upgrade backfills active rows deterministically, fails before mutation if any household has more than two active memberships, preserves IDs and current associations, and leaves inactive slots null. Verify a direct active NULL-slot insert fails even when no occupied slots exist. Use a transactional migration and test it against nonempty old-schema fixtures and invalid data. Coordinated stop-the-world upgrade required for legacy writers that do not supply slot.

## Concurrency states

`usable → consumed` and `usable → revoked` are serialized transitions; expiry is determined from current clock, not a mutable expiration transition. Failed acceptance makes no membership and does not itself consume an otherwise unused invitation. A separate concurrent successful acceptance may consume that same invitation. Unique-account conflicts are translated after rollback; no orphan household or partial invitation change survives.

A controlled PostgreSQL test must hold the household row lock, start acceptance before expiry on an independent transaction, advance the shared test clock to the exact deadline while acceptance waits, and release the lock. Assert rejection and unchanged committed membership and invitation state from a fresh connection; this detects validity being checked only before the lock wait.
