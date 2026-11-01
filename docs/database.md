# Database Configuration

## Overview

SharedOPS uses PostgreSQL 18 with Spring Data JPA/Hibernate, Flyway migrations, and HikariCP for JDBC connection pooling.

- **Development database:** PostgreSQL via Docker Compose.
- **Integration tests:** Isolated PostgreSQL via Testcontainers.
- **Schema management:** Versioned Flyway SQL migrations.
- **Hibernate schema management:** `ddl-auto=validate`.
- **Open Session in View:** Disabled.
- **Connection pooling:** HikariCP.

The committed business schema is managed through Flyway. Hibernate validates mapped tables but does not create or modify them. JDBC session schema and its migration are pending the session implementation in HO-9; test configuration currently permits separate session schema initialization.

## Connection Pool Configuration

HikariCP connection pool parameters are configurable through environment variables.

| Spring Boot property                          | Environment variable | Default    |
| --------------------------------------------- | -------------------- | ---------- |
| `spring.datasource.hikari.maximum-pool-size`  | `MAXIMUM_POOL_SIZE`  | `5`        |
| `spring.datasource.hikari.connection-timeout` | `CONNECTION_TIMEOUT` | `30000 ms` |

### Maximum Pool Size

`maximum-pool-size` limits the maximum number of JDBC connections available to each application instance.

Each application instance maintains its own connection pool. Consequently, scaling the application horizontally increases the total potential demand for database connections.

### Connection Timeout

`connection-timeout` specifies how long an application request may wait to acquire a connection from the pool.

If no connection becomes available within the configured timeout, HikariCP throws a connection acquisition timeout exception.

This setting does **not** control SQL query execution time.

## Connection Budget

The maximum application connection budget is calculated as:

    Application connections = Application instances × Maximum pool size

With the configured pool size of five connections:

| Scenario                  | Application instances | Pool size per instance | Maximum application connections |
| ------------------------- | --------------------: | ---------------------: | ------------------------------: |
| Local development         |                     1 |                      5 |                               5 |
| Two-instance deployment   |                     2 |                      5 |                              10 |
| Three-instance deployment |                     3 |                      5 |                              15 |

The multi-instance deployments are illustrative capacity scenarios, not current production deployment configurations.

### Operational Headroom

Application connection pools are not the only consumers of PostgreSQL connections.

Additional capacity must be reserved for:

- Database administration and monitoring.
- Flyway migrations and other migration tooling, when using separate connections.
- Other database clients and maintenance operations.

The overall connection budget must satisfy:

    Application connections
    + Additional client connections
    + Operational reserve
    <= PostgreSQL available connection capacity

PostgreSQL's available capacity must account for its configured `max_connections` and reserved connection slots.

**Capacity rule:** Before increasing the number of application instances or pool size, recalculate the connection budget and confirm sufficient PostgreSQL capacity.

The current pool size is an initial configuration value, not a performance-tested production recommendation.

## Data Persistence

Docker Compose uses the named volume `postgres_data` for PostgreSQL data storage.

This volume separates database storage from the lifecycle of application containers.

Expected behavior:

- Recreating or replacing the application container does not remove PostgreSQL data.
- Recreating the PostgreSQL container preserves data when the same named volume is reused.
- Running `docker compose down` without `-v` preserves named volumes.
- Running `docker compose down -v` removes Compose-managed named volumes and deletes the database data stored in them.

Persistent volumes provide data durability across container replacement but are **not a substitute for database backups**.

## Verification

Review on 2026-11-01: working tree on `feat/HO-6-provision-persistence`, based on commit `19ab6ea39955aebd9c56e48e8c30c10cb50bf73f`, including uncommitted changes. This record does not attest to other working-tree revisions.

| Check                    | Evidence and result                                                                                                                                                                                                                                                        |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Full verification        | `make verify` succeeded on 2026-11-01 with access to the local Maven cache and Docker: 50 Surefire tests and 18 Failsafe tests passed with no failures, errors, or skips. Spotless, Checkstyle, and Prettier checks passed. Maven used existing incremental build outputs. |
| PostgreSQL               | Testcontainers connected to Docker Desktop 29.4.1 and started `postgres:18.6-bookworm`. Flyway applied the production migration to an empty test database and the application context started with Hibernate schema validation.                                            |
| Configuration inspection | Compose specifies PostgreSQL 18.6 and a named volume; the application specifies disabled Open Session in View and configurable pool size and timeout. Effective pool values were not checked in this run.                                                                  |

The passing PostgreSQL integration tests cover production migration reapplication, modified migration checksum, cross-household references, checklist ownership, and rollback on explicit flush and implicit flush at commit. The checksum test does not demonstrate detection of actual database schema drift. The rollback tests exercise a constructed transaction, not a completed application use case.

Data survival after application container replacement was manually checked by the developer, independently of `make verify`. The commands and environment for that manual check were not recorded. The current Compose file defines no application container.

The following remain **not verified** by this run: Compose startup and connectivity, effective runtime pool settings, and database schema drift detection. Record the command, environment, revision, and outcome when these checks are run.
