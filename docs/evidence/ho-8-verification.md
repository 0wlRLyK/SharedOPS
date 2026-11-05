# HO-8 — Local verification

**Date:** 05.11.2026  
**Tested artifact:** Working tree based on `a0a805ea42f6`, including uncommitted HO-8 changes.  
**Environment:** macOS ARM64, JDK 25.0.4.1, Spring Boot 4.1.1, PostgreSQL 18.6 through Testcontainers.

| Command from `backend/` | Result                                                                                                                               |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `./mvnw clean test`     | Passed: 70 Surefire tests, 0 failures, 0 errors, 0 skipped. Docker was stopped; `DOCKER_HOST` also pointed to an unavailable socket. |
| `./mvnw clean verify`   | Passed after Docker was started: 70 Surefire and 16 Failsafe tests, 0 failures, 0 errors, 0 skipped. Spotless and Checkstyle passed. |

The local Maven cache was copied to a temporary writable location and selected with `MAVEN_OPTS`. The first clean verification attempt, before Docker was started, failed because Testcontainers could not find a Docker environment; it reported 14 integration errors. The successful retry included `ChecklistPositionRaceIT` and the `local` and `test` profile startup checks using production migrations.

This is local verification only. A run on an actual fresh CI runner remains pending HO-15.
