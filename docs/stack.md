# SharedOPS — Technology Stack

**Status:** Backend foundation configured and smoke-tested.\
**Updated:** 18.10.2026
**Scope:** M0 backend and development tooling

## Environment requirements

| Requirement               | Version or condition                                                                   |
| ------------------------- | -------------------------------------------------------------------------------------- |
| JDK                       | **Eclipse Temurin 25 LTS, required**, without preview features                         |
| Maven                     | 3.10.0 through the committed Maven Wrapper                                             |
| Node.js                   | >= 22.12.0, required by Commitlint                                                     |
| Corepack                  | Available on the developer's PATH to select pinned pnpm                                |
| pnpm                      | 12.14.0                                                                                |
| pre-commit                | >= 4.0.0                                                                               |
| Docker-compatible runtime | Running for Testcontainers tests and the test development launcher                     |
| PostgreSQL                | Major version 18 for a separately managed database; tests use `postgres:18.6-bookworm` |

Use **Eclipse Temurin 25 LTS** consistently for the CLI (`JAVA_HOME` and `PATH`), IDE project SDK, Maven importer, Maven runner, and build/test runtimes, including CI when configured. Set the IDE language level to Java 25 and keep preview features disabled. The selected JDK distribution is Eclipse Temurin; the patch version is not pinned. The [verification record](evidence/toolchain-verification.md) identifies the exact Temurin build used for a specific run.

## Backend dependencies

Versions below come from [backend/pom.xml](../backend/pom.xml). “Boot-managed” means the Spring Boot dependency management selects the version.

| Component         | Technology                     | Version      |
| ----------------- | ------------------------------ | ------------ |
| Language          | Java                           | 25 LTS       |
| JDK distribution  | Eclipse Temurin                | 25 LTS       |
| Framework         | Spring Boot                    | 4.1.1        |
| HTTP              | Spring MVC                     | Boot-managed |
| Persistence       | Spring Data JPA / Hibernate    | Boot-managed |
| Migrations        | Flyway with PostgreSQL support | Boot-managed |
| Database driver   | PostgreSQL JDBC                | Boot-managed |
| Security          | Spring Security                | Boot-managed |
| Sessions          | Spring Session JDBC            | Boot-managed |
| Validation        | Bean Validation                | Boot-managed |
| Operations        | Spring Boot Actuator           | Boot-managed |
| JSON              | Jackson                        | Boot-managed |
| API documentation | springdoc OpenAPI WebMVC UI    | 3.1.1        |

These dependencies are installed. Product authentication/session rules, household authorization, domain workflows and production migrations still need implementation. OpenAPI is smoke-tested against a test-only endpoint.

## Test dependencies

| Tool                         | Version      | Use                                       |
| ---------------------------- | ------------ | ----------------------------------------- |
| JUnit Jupiter                | Boot-managed | Test execution                            |
| AssertJ                      | 3.27.7       | Assertions                                |
| Mockito / Spring Boot Test   | Boot-managed | Component and integration support         |
| Testcontainers               | Boot-managed | PostgreSQL containers                     |
| Spring Modulith starter test | 2.1.1        | Application module structure verification |
| Datafaker                    | 2.7.0        | Synthetic test data support               |

Spring Modulith is integrated: `ModulithStructureTest` calls `ApplicationModules.verify()`. As feature modules are added, architecture tests must cover the intended boundaries. Installing Modulith does not by itself establish a complete domain architecture.

Surefire runs `*Test` and the existing `*Tests` context check. Failsafe runs `*IT` during `verify`. Both groups include checks requiring Docker; see the [test strategy](test-strategy.md) for commands and planned coverage.

## Formatting and hooks

| Tool                    | Version             | Configuration                         |
| ----------------------- | ------------------- | ------------------------------------- |
| Spotless Maven Plugin   | 3.10.4              | `backend/pom.xml`                     |
| google-java-format      | 1.36.1, GOOGLE mode | `backend/pom.xml`                     |
| Checkstyle Maven Plugin | 3.6.0               | `backend/pom.xml`                     |
| Checkstyle engine       | 14.3.0              | `backend/config/checkstyle.xml`       |
| Prettier                | 3.9.10              | `.prettierrc.json`, `.prettierignore` |
| Commitlint              | 21.2.3              | `commitlint.config.mjs`               |
| pre-commit              | >= 4.0.0            | `.pre-commit-config.yaml`             |

Checkstyle enforces `NeedBraces`, `AvoidStarImport` and `AvoidModuleImport`. Prettier handles supported text and frontend formats; Java, XML and SQL are excluded. Hooks run repository hygiene checks, Java formatting/static checks, text formatting and Conventional Commit validation. File hooks may modify files and require restaging. The commit-msg hook has been verified with valid and invalid messages.

## Build and verification

Run from the repository root:

```bash
corepack pnpm install --frozen-lockfile
make check
make test
make verify
pre-commit run --all-files
```

`pre-commit run --all-files` runs file hooks; it does not exercise commit-msg validation. Check a message separately with:

```bash
pre-commit run commitlint --hook-stage commit-msg --commit-msg-filename /path/to/message
```

See the [project README](../README.md) for hook installation and backend launch instructions, and the [toolchain verification record](evidence/toolchain-verification.md) for recorded results.

## Planned components

- React / TypeScript / Vite web frontend.
- Product account, session, household and task workflows.
- Production schema migrations and deployment configuration.
- Additional feature-level architecture, authorization, concurrency and contract checks.
- SpotBugs static analysis.
- GitHub Actions CI/CD.

## Maintenance

Pin direct tool versions, commit the pnpm lockfile and Maven Wrapper, and use Spring Boot dependency management for supported libraries. Verify upgrades against the configured checks. Record changes to architectural decisions through an ADR and keep the [documentation index](readme.md) current.
