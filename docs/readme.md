# SharedOPS — Documentation

Start with the [project README](../README.md) for setup and development commands.

## Project documents

| Document                                                     | Contents                                                                                          |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------- |
| [Technology stack](stack.md)                                 | JDK requirement, installed dependencies, tool versions and build commands                         |
| [Requirements inventory](requirements.md)                    | Functional requirements, non-functional requirements, acceptance criteria and release assignments |
| [Product decision register](decision-register.md)            | Accepted behavior, deferred proposals, alternatives and revisit conditions                        |
| [Risk register](risk-register.md)                            | Delivery and engineering risks, triggers and response plans                                       |
| [Test strategy](test-strategy.md)                            | Test levels, critical scenarios, quality gates and evidence requirements                          |
| [Toolchain verification](evidence/toolchain-verification.md) | Recorded backend smoke checks, formatting, hooks and commit-message validation                    |

## Architecture decisions

| ADR                                            | Decision                                              |
| ---------------------------------------------- | ----------------------------------------------------- |
| [0001](adr/0001-java-spring-maven-baseline.md) | Java 25, Spring Boot and Maven                        |
| [0002](adr/0002-blocking-persistence-stack.md) | Spring MVC, JPA, PostgreSQL and Flyway                |
| [0003](adr/0003-automated-code-quality.md)     | Automated formatting and code quality                 |
| [0004](adr/0004-repository-organization.md)    | Backend, frontend and documentation in one repository |
| [0005](adr/0005-task-list-reference.md)        | Task–List references and shopping warnings            |
| [0006](adr/0006-financial-stages.md)           | Financial history across partner changes              |

Use the [ADR template](adr/0000-template.md) for new decisions.

## Documentation conventions

- Write in English. Use direct descriptions, stable terminology and descriptive link labels.
- Use one document title and unnumbered section headings. Keep status, update date and scope concise where needed.
- Write dates as `DD.MM.YYYY`. Prefer descriptive filenames without dates; if a date is needed, use the same format.
- Link to repository documents with relative paths. Describe sources maintained outside the repository by their purpose and relevant revision or section.
- Preserve requirement and ticket IDs. Distinguish approved decisions, installed dependencies, implemented behavior and executed checks.
- Keep ADR sections aligned with the template. Put execution results in evidence records and link to them from decisions and strategy documents.
- Format Markdown with the repository's pinned Prettier version.
