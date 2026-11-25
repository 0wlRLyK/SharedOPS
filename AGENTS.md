# SharedOPS agent instructions

## Scope and specifications

- Preserve unrelated changes. Use the task's clean branch or isolated worktree and keep the diff scoped.
- For each issue, use the dedicated named branch the owner created for that issue, including when working in an isolated worktree. Check existing branches before creating one; do not replace the owner's chosen branch with a generated name. If no dedicated branch exists, create one as `<type>/<issue-number>-<short-name>` (for example, `feat/HO-12-responsibility-ownership`), using an appropriate type such as `feat`, `chore`, `test`, or `docs`.
- Deliver each issue on its own branch and PR with exactly one final commit. Use a Conventional Commit title and a body summarizing the issue's substantive changes; consolidate local commits before handoff.
- Prefer codebase-memory MCP for code discovery. Use local search for literal strings, configuration, or gaps in the graph.
- For tracked changes, read the Linear issue and use the relevant [requirements](docs/requirements.md), [decisions](docs/decision-register.md), and [constitution](.specify/memory/constitution.md). Resolve conflicting requirements with the owner before implementing the affected behavior.
- For changes to authorization, household isolation, transactions, concurrency, migrations, public API, or mandatory CI checks, follow the full Spec Kit workflow and obtain owner approval of the spec and plan before editing implementation files. Bounded, unambiguous changes may use the short workflow.
- When available, use Superpowers for focused practices within Spec Kit, such as brainstorming, TDD, debugging, review, and verification. Record agreed requirements, plans, and implementation tasks in the corresponding Spec Kit artifacts.

## Code review rules

- Respect [module boundaries](docs/architecture.md). Keep the domain framework-independent and use module exports for cross-module calls.
- Check household access in the application layer before reads and writes. Derive scope from active membership. Keep secrets and private household data out of prompts, logs, and artifacts. Use synthetic fixtures.
- Review changes to public behavior against [OpenAPI](docs/api/openapi.yml). Verify database invariants on PostgreSQL with production Flyway migrations and inspect committed outcomes.
- Flag swallowed errors that let a failed operation continue, duplicated business rules that can diverge, and authorization enforced only at a controller when an application operation has other callers. Identify the affected behavior and location.
- Flag tests that lack assertions on the relevant outcome or committed state after a rejected operation. Flag public contract or migration changes without a compatibility assessment.

## Local verification and handoff

- Use `make check` for formatting and Java lint, `make test` for fast backend tests, and `make verify` for full backend verification with Docker. Select checks by change scope and the [test strategy](docs/test-strategy.md). For docs-only changes, check formatting and links in the affected files.
- Report the actual commands and outcomes, including failed, skipped, or blocked mandatory checks. Never weaken an assertion or bypass a required check to get a pass.
- For each changed behavior, plan a positive and applicable negative scenario, test level, and expected observable outcome. For database-backed isolation or transactions, include committed state after success and rejection.
- For risk-scoped changes, provide a table mapping each acceptance criterion to its test or verification, command, result, and tested revision. Mark unexecuted or blocked mandatory checks explicitly.
- Hand off a reviewable local diff and evidence. Publishing, merging, and deploying belong to the owner.
