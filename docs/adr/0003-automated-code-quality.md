# ADR-0003: Automate Formatting and Code Quality Checks

Date: 16.10.2026
Status: Accepted

## Context

Consistent formatting and a small set of structural rules reduce mechanical review work. HO-002 establishes local tooling; HO-015 supplies CI enforcement.

## Decision

- Format Java with Spotless and the pinned google-java-format version in GOOGLE mode.
- Use Checkstyle for `NeedBraces`, `AvoidStarImport` and `AvoidModuleImport`.
- Format supported text and frontend files with pinned Prettier; exclude Java, XML and SQL.
- Keep line endings and whitespace conventions in committed editor and Git configuration.
- Run repository hygiene, formatting and static checks through pre-commit. Validate Conventional Commit messages through the commit-msg hook.
- Keep mandatory CI enforcement separate from local hooks, which can be bypassed.

## Alternatives Considered

- IDE-only formatting: does not provide a reproducible CLI check.
- Manual formatting review: spends review time on rules tools can enforce.
- Multiple Java formatters: risks conflicting output.

## Consequences

Tool versions and rules need maintenance. Hooks can change files, requiring review and restaging. Formatter and static-rule checks should cover different concerns. The [technology stack](../stack.md) records versions and commands.

## Verification

The [toolchain verification record](../evidence/toolchain-verification.md) records formatting, static checks and commit-message validation. CI enforcement and negative formatter/static-rule fixtures remain work for the corresponding implementation tickets.
