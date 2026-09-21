# Development Guide

## Current State

CampusOne is in Phase 0. The repository has no Maven project, Spring Boot application, database migration files, or local infrastructure configuration. Commands for building, running, testing, formatting, and migrations will be added only after Spring Boot initialization.

## Prerequisites

The expected Phase 1 environment is:

- Java 21 (a JDK, not only a runtime)
- Maven compatible with the selected Spring Boot baseline
- Docker Desktop or another Docker-compatible runtime for future local PostgreSQL and Testcontainers use
- Git

Exact versions and installation commands remain proposed until the build is bootstrapped and verified.

## Environment Variables

Use `.env.example` as a list of safe local-development variable names. Its values are placeholders, not credentials. A future local `.env` must remain untracked and must not be copied into logs, issue descriptions, commits, or documentation.

Expected variables cover the application profile, server port, PostgreSQL connection, JWT settings, and optional future Redis settings. Redis is not an initial requirement.

## Proposed Local Workflow

After Spring Boot initialization:

1. Install the verified JDK and Maven versions.
2. Create an untracked local environment file or configure variables through the operating system or IDE.
3. Start only the approved local dependencies.
4. Run the documented Maven validation and test commands.
5. Review migrations, logs, and the diff before opening a change for review.

### Commands Pending Bootstrap

The following command categories are intentionally placeholders until `pom.xml` and local infrastructure files exist:

- Compile, test, and package commands
- Application run commands
- Formatting and static-analysis commands
- Flyway migration commands
- Docker Compose commands

Do not invent or report these commands as working before their supporting files are created.

## Branch and Commit Workflow

- Start from the current approved branch and keep changes focused.
- Use short-lived branches when repository workflow is established.
- Write imperative, scoped commit messages, for example `docs: define modular monolith decision`.
- Do not include secrets, generated local files, unrelated formatting, or unrelated user changes.
- Inspect `git status` and `git diff` before committing or reporting work.

## Formatting, Validation, and Migrations

Formatting and validation tools will be selected with the Maven build. Once selected, they must run consistently in local development and later CI.

Flyway will own schema evolution. New migrations must be ordered, reviewed, and tested against a clean PostgreSQL database. Do not alter a migration after it has been applied outside disposable local development; create a corrective migration instead.

## Windows and WSL

CampusOne supports native Windows development and WSL-based development. WSL is optional. Use paths, line endings, Docker access, and shell commands consistently within the chosen environment. If Maven or Docker runs in WSL, avoid mixing its generated files with tools configured against a different Windows path unless that workflow has been verified.
