# ADR-0001: Adopt Java 25, Spring Boot and Maven

Date: 16.10.2026
Status: Accepted

## Context

The backend needs a consistent language level, framework baseline and build process across local development and CI. HO-002 establishes this baseline.

## Decision

- Require JDK 25 LTS without preview features for CLI builds and IDE compilation.
- Use Spring Boot 4.1.x with a pinned patch version and Maven through the committed Maven Wrapper.
- Use Spring Boot dependency management for supported libraries; pin other direct dependencies explicitly.
- Record exact versions and environment requirements in the [technology stack](../stack.md).

## Alternatives Considered

- Java 21 LTS: an alternative if the selected baseline must be revised; not the current requirement.
- Spring Boot 3.x: an alternative framework baseline requiring a separate compatibility decision.
- Gradle: not selected because Maven matches the current project workflow.

## Consequences

The wrapper and committed configuration make builds reproducible. JDK and framework upgrades require compatibility checks. IDE settings must use the same Java language level as Maven.

## Verification

The [toolchain verification record](../evidence/toolchain-verification.md) records startup, JSON, migration, persistence and OpenAPI smoke checks. A clean-checkout CI build remains part of the CI implementation work.
