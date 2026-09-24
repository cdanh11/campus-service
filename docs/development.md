# Development Guide

## Current State

Phase 1A bootstrap and Phase 1B Identity and Access are complete. The repository contains JWT authentication, Spring Security authorization, Identity persistence, and administrator user-management endpoints backed by PostgreSQL and Flyway. Phase 1 release readiness adds CI, a production profile, health probes, a container build, and a release runbook. Selecting production infrastructure and running the release remain environment-owner responsibilities.

## Prerequisites

The expected Phase 1 environment is:

- Java 21 (a JDK, not only a runtime)
- Maven Wrapper (`mvnw` or `mvnw.cmd`); a global Maven installation is needed only to regenerate the wrapper
- Docker Desktop or another Docker-compatible runtime for future local PostgreSQL and Testcontainers use
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
- Use short-lived branches when repository workflow is established.
- Write imperative, scoped commit messages, for example `docs: define modular monolith decision`.
- Do not include secrets, generated local files, unrelated formatting, or unrelated user changes.
- Inspect `git status` and `git diff` before committing or reporting work.

## Formatting, Validation, and Migrations

`mvnw clean verify` is the current validation command. Formatting and static-analysis tooling has not been selected yet and must not be represented as configured.

Flyway will own schema evolution. New migrations must be ordered, reviewed, and tested against a clean PostgreSQL database. Do not alter a migration after it has been applied outside disposable local development; create a corrective migration instead.

Warning: V5 is pre-release and its checksum can change before release. If V5 has already run against a disposable local database, do not use `flyway repair`; stop the local stack, remove that disposable database volume, recreate it, and rerun migrations. Once V5 is released or has run outside disposable local development, do not edit it; add a corrective migration.

## Windows and WSL

Campus Service supports native Windows development and WSL-based development. WSL is optional. Use paths, line endings, Docker access, and shell commands consistently within the chosen environment. If Maven or Docker runs in WSL, avoid mixing its generated files with tools configured against a different Windows path unless that workflow has been verified.
