# Research: Single-use household invitations

## Token and expiry

**Decision:** 32 cryptographically random bytes, base64url encoding, SHA-256 digest for lookup/storage; fixed 24-hour lifetime from creation, invalid at or after expiry. Secret appears once in a no-store create response and is supplied for acceptance in a request body; never in a URL or logs.

**Rationale:** High entropy makes fast digest lookup appropriate without password-style slow hashing; 24 hours is the owner decision. A request body avoids bearer secrets in ordinary access-log paths. Scrub body logging, exception text, object representations and test artifacts.

**Alternatives:** Plaintext storage exposes valid bearer secrets on database disclosure; deterministic identifiers or low-entropy codes make guessing feasible; secret in path/query leaks through common logging; password hashing alone prevents practical indexed lookup. A link may later be composed by a client, but this feature need only return a shareable code.

## Atomicity and capacity

**Decision:** Lock household before invitation on accept/revoke. Under a proxy-visible transaction re-read invitation, validate expiry using current clock after lock, check membership, allocate one of two slots, insert member and mark consumed atomically. Add a database unique active household-slot constraint and retain the unique active-account index. Reuse a shared slot allocation path across existing and new membership writes.

**Rationale:** A count alone races. A household lock serializes compliant capacity checks and revocation, while bounded slots and uniqueness defend against concurrent or accidental bypass writers. The account unique index arbitrates create/accept races across different households. Translate only the named known conflicts after rollback.

**Alternatives:** In-memory locking fails across application instances. Optimistic count alone lacks a database invariant. An advisory lock is easier to omit for another writer. A trigger-based count requires careful lock ordering and implicit side effects; explicit slots provide a directly inspectable constraint and migration path.

## Contract and upgrade

**Decision:** Add HTTP operations to existing authoritative OpenAPI, keeping existing operations compatible. No token in URI. Introduce a forward migration that backfills active slots and fails on invalid existing state; deploy migration with a compatible application while writes are stopped.

**Rationale:** Existing API has no invitation operations; additive operations avoid breaking existing clients. Existing V7 rows lack slots, and old writers would violate the new invariant unless deployment is coordinated. Do not silently delete or rearrange existing memberships.

**Alternatives:** App-only guard allows unexpected SQL writers to overfill; silently assigning excess users loses data. Breaking existing API or changing previous Flyway migrations compromises compatibility.

## Tests

**Decision:** Use production migrations, independent PostgreSQL transactions and coordinated starts/barriers, real application services and fresh connections to inspect committed state; fixed/mutable clock for boundary tests. Unit tests cover token/clock decisions; HTTP tests cover auth, CSRF, error and log boundary; `make verify` is final broad check.

**Rationale:** Mocked stores cannot prove constraints, transaction proxy behavior or races. Controlled synchronization and explicit final state detect false successes.

**Alternatives:** H2 and rollback-only test transactions are not evidence of PostgreSQL commits; sleeps are nondeterministic. Account-deletion-to-revocation belongs to HO-85 and is not simulated as complete here.
