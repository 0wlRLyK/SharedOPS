# SharedOPS Constitution

## Core Principles

### I. Traceable Changes

- A behavior change MUST link its Linear issue and applicable requirement or decision IDs to a scoped specification, observable acceptance criteria, and verification evidence.
- Use [requirements](../../docs/requirements.md) for baselined scope, the [decision register](../../docs/decision-register.md) and [ADRs](../../docs/adr/) for accepted decisions, and Linear for issue state and acceptance criteria. The Spec Kit spec describes the change against those sources.
- When sources conflict on behavior, surface the discrepancy and obtain the owner's decision before implementing the disputed behavior. Update affected sources with that decision.

### II. Module Boundaries and Compatibility

- Preserve the [modular monolith boundaries](../../docs/architecture.md). Keep domain code independent of Spring and persistence frameworks. Cross-module calls MUST use documented exports. Direct access to another module's repository is prohibited.
- Treat [OpenAPI](../../docs/api/openapi.yml) as the authoritative public HTTP contract. Review API and migration impact with the implementation. Preserve compatibility or record the approved break and migration path before implementation.

### III. Household Isolation and Data Protection

- Household-scoped operations MUST derive access from authenticated identity and active membership and check it at the application boundary before side effects. Treat resource identifiers from the client as untrusted input.
- Inaccessible household data MUST remain unreadable and unmodified. Error responses must avoid revealing whether a foreign resource exists. Keep credentials, tokens, and private household data out of logs, agent prompts, and verification artifacts. Use synthetic fixtures.

### IV. Verification Evidence

- Verify changed behavior and regressions at the smallest level that proves the requirement. Cover applicable negative paths, boundaries, and committed side effects. Use controlled time and explicit coordination for time-sensitive and concurrent tests.
- Verify database-dependent security, migration, constraint, transaction, and race behavior against PostgreSQL with production Flyway migrations. Assert the relevant committed state. Follow the [test strategy](../../docs/test-strategy.md).
- For each applicable acceptance criterion, record the check and observed result, including the command, tested revision and any material environment details. A failed, skipped, blocked, or unrun mandatory check prevents merge until resolved or formally addressed through an owner-approved baseline decision.

### V. Bounded Agent Execution

- Use a clean branch or isolated worktree appropriate to the task. Keep changes small enough to review and preserve unrelated work.
- Agents operate within the authorized scope and hand off a local diff and verification evidence. The owner reviews the diff and controls publication, merge, and deployment. Record solo self-review explicitly when applicable.

## Engineering Constraints

Use the pinned project toolchain and repository commands. Document the reason for changes to architecture, public contracts, or dependencies in the relevant plan or ADR. Apply least privilege when running tools and keep production credentials and real household data outside agent and test environments. See the [technology stack](../../docs/stack.md).

## Development Workflow and Gates

The full Spec Kit sequence is specify → clarify → plan → checklist → tasks → analyze → implement → converge. The short sequence is specify → plan → tasks → implement → converge.

| Gate                           | Required evidence                                                                                                                                                                                                                                                                                                                                                     |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Scope and plan                 | Link the issue and acceptance criteria. For authorization, tenant isolation, transactions, concurrency, migrations, public API, or mandatory CI changes, use the full Spec Kit workflow. Resolve clarification and analysis findings and obtain owner approval of the spec and plan before implementation. Bounded, unambiguous changes may use the shorter workflow. |
| Implementation and convergence | Implement in reviewable slices and trace applicable acceptance criteria to tests or other verification. Reconcile the diff with the approved scope before handoff. Return material changes in scope or externally visible behavior to specification review.                                                                                                           |
| Verification and owner review  | Use applicable repository checks, review invariants and API or migration impact, and disclose failures or missing evidence. No required check may be silently suppressed or weakened.                                                                                                                                                                                 |

The current local checks and their coverage are defined in the [test strategy](../../docs/test-strategy.md). GitHub CI enforcement and contribution checks are tracked in HO-15 and HO-87. While automated enforcement is pending, the owner verifies available evidence and records gaps. Broader rollout requires evidence that a failed required GitHub check blocks merge.

## Governance

The constitution defines engineering rules for feature specifications, plans, implementation, and review. Repository-specific operating instructions live in [AGENTS.md](../../AGENTS.md).

Amendments require the owner's approval and an impact review of affected specs, repository guidance, and verification gates. Record the amendment date and use semantic versioning. Use a major bump for incompatible policy changes, a minor bump for new or materially expanded rules, and a patch bump for clarifications. Reviewers check applicable principles before merge.

**Version**: 1.0.0 | **Ratified**: 2026-11-17 | **Last Amended**: 2026-11-17
