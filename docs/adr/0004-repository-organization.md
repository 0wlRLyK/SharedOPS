# ADR-0004: Keep Backend, Frontend and Documentation in One Repository

Date: 16.10.2026
Status: Accepted

## Context

The backend, planned frontend and documentation share API contracts and product decisions. One repository keeps related changes reviewable together while allowing independent builds.

## Decision

Use separate top-level directories:

```text
backend/   # Spring Boot application, Maven Wrapper and tests
web/       # React/TypeScript frontend, when introduced
docs/      # Requirements, decisions, test strategy and evidence
```

Keep the product decision register in `docs/decision-register.md` and ADRs in `docs/adr/`. The root README documents setup and links to the documentation index. Keep the backend as one Maven module until implementation boundaries justify more. CI should use separate backend and frontend jobs with cross-stack checks for shared contracts.

## Alternatives Considered

- Separate backend and frontend repositories: adds coordination for shared changes.
- A separate documentation repository: separates design review from implementation changes.
- A mixed source directory: obscures tooling and build boundaries.

## Consequences

One repository simplifies navigation and coordinated changes. CI must avoid skipping required checks when filtering by path. Independent release or access-control needs may justify revisiting this decision.

## Verification

The current tree contains `backend/` and `docs/`; `web/` is planned. Validate README commands and relative documentation links. Frontend builds, CI separation and cross-stack contract checks are verified when those components are introduced.
