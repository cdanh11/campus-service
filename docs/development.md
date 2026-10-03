# Development Guide

## Current State

Phase 1 and Phase 2 are complete; Phase 3A implements Program/Course catalogs. The repository contains JWT authentication, Spring Security authorization, Identity persistence, and administrator user-management endpoints backed by PostgreSQL and Flyway. Phase 1 release readiness adds CI, a production profile, health probes, a container build, and a release runbook. Selecting production infrastructure and running the release remain environment-owner responsibilities.

## Prerequisites

The development and test environment requires:

- Java 21 (a JDK, not only a runtime)
- Maven Wrapper (`mvnw` or `mvnw.cmd`); a global Maven installation is needed only to regenerate the wrapper
- Docker Desktop or another Docker-compatible runtime for local PostgreSQL and Testcontainers
- Git

The bootstrap was verified with Java 21.0.12.1 and Maven Wrapper 3.9.14 on Windows. Docker Desktop was available for Testcontainers PostgreSQL.

## Environment Variables

Use `.env.example` as a list of safe local-development variable names. Its values are placeholders, not credentials. A future local `.env` must remain untracked and must not be copied into logs, issue descriptions, commits, or documentation.

Expected variables cover the application profile, server port, PostgreSQL connection, JWT settings, and optional future Redis settings. Redis is not an initial requirement.

## Local Workflow

1. Install the verified JDK.
2. Create an untracked local environment file or configure variables through the operating system or IDE.
3. Run the verified build and integration-test command:

   ```powershell
   .\mvnw.cmd clean verify
   ```

4. For local PostgreSQL, configure the values from `.env.example` in an untracked `.env`. Docker Compose reads this file, but Spring Boot does not.
5. Start local PostgreSQL with `docker compose --env-file .env up -d postgres`, then configure the same variables in the IDE or shell before running the application.
6. Review migrations, logs, and the diff before opening a change for review.

The Maven Wrapper command above is the required verification command on Windows. Testcontainers starts an isolated PostgreSQL instance; Docker Compose and a long-running local application are optional local-development workflows.

## Production Profile And Container Build

Use `SPRING_PROFILES_ACTIVE=production` only when the deployment platform supplies the PostgreSQL connection values, `JWT_SECRET`, and exact `ALLOWED_ORIGINS`. The production profile has no local datasource fallback, keeps Hibernate at `validate`, enables Flyway, and forces Secure refresh cookies.

Build the neutral deployment artifact with `docker build --tag campus-service:<version> .`. The image runs the Spring Boot jar as a non-root user. Complete release steps, deployment checks, and recovery guidance are in [Phase 1 Release](runbooks/phase-1-release.md).

## Branch and Commit Workflow

- Start from the current approved branch and keep changes focused.
- Use short-lived `feature/<function-or-phase>` branches. Each phase/subphase requires a PASS review before proceeding; split commits by function.
- Write imperative, scoped commit messages, for example `docs: define modular monolith decision`.
- Do not include secrets, generated local files, unrelated formatting, or unrelated user changes.
- Inspect `git status` and `git diff` before committing or reporting work.

## Formatting, Validation, and Migrations

`mvnw clean verify` is the current validation command. Formatting and static-analysis tooling has not been selected yet and must not be represented as configured.

Flyway will own schema evolution. New migrations must be ordered, reviewed, and tested against a clean PostgreSQL database. Do not alter a migration after it has been applied outside disposable local development; create a corrective migration instead.

V1–V11 are the merged baseline and must not be edited. Phase 3A adds V12/V13. Never use Flyway repair to mask a checksum mismatch; investigate the migration history and add an approved corrective migration when needed.

## Windows and WSL

Campus Service supports native Windows development and WSL-based development. WSL is optional. Use paths, line endings, Docker access, and shell commands consistently within the chosen environment. If Maven or Docker runs in WSL, avoid mixing its generated files with tools configured against a different Windows path unless that workflow has been verified.
