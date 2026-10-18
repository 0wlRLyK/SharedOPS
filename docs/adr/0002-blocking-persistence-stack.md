# ADR-0002: Adopt Spring MVC and PostgreSQL Persistence

Date: 16.10.2026
Status: Accepted

## Context

The application needs a REST API and transactional relational persistence. Domain rules must remain independent of HTTP and persistence implementation details. This decision covers HO-002–008 and the persistence requirements in the [requirements inventory](../requirements.md).

## Decision

- Use Spring MVC for blocking HTTP request processing.
- Use Spring Data JPA and Hibernate in persistence adapters.
- Use PostgreSQL 18 and Flyway SQL migrations.
- Keep domain objects independent of Spring MVC, JPA and database infrastructure; map them in adapters.
- Enforce domain invariants with transaction boundaries and database constraints. Use optimistic concurrency and `409 Conflict` responses where NFR-07 requires them.
- Allow targeted SQL projections when a query warrants them.

## Alternatives Considered

- WebFlux: adds reactive infrastructure without benefiting the selected blocking persistence model.
- Spring JDBC / JdbcClient: remains an option for targeted SQL; JPA is selected for ordinary relational mapping.
- MariaDB: PostgreSQL is selected based on existing experience.
- Liquibase: Flyway SQL migrations meet the initial migration needs.

## Consequences

JPA reduces repetitive persistence code but introduces lifecycle, lazy-loading and N+1 risks. Adapter mappings add work while preserving domain boundaries. Locking, rollback and conflict handling require explicit implementation and tests.

## Verification

The [toolchain verification record](../evidence/toolchain-verification.md) covers PostgreSQL startup, test migrations and persistence smoke checks. Feature work must add production migrations, adapter constraints, committed rollback/concurrency tests, query-count checks and domain dependency checks as described in the [test strategy](../test-strategy.md).
