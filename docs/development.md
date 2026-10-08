# Development Guide

## Current State

Backend Phases 1–6 and the Phase 7 OpenAPI contract prerequisite are complete and reviewed PASS; see the corresponding plans/reviews and [Testing](testing.md) for historical build evidence. Frontend Phase 7 is complete in the separate campus-client repository. Remaining Phases 8–9 follow the approved [local testing/demo plan](plans/phase-8-9-local-demo.md); no production infrastructure or Workflow/AI is required. Optional production configuration/runbooks remain reference material.

## Prerequisites

Frontend lives independently in [campus-client](https://github.com/cdanh11/campus-client). Production OpenAPI uses owner-qualified implicit schema names (`springdoc.use-fqn`) to prevent nested DTO collisions; explicit @Schema names remain stable. Regenerate frontend snapshots/types after an approved contract change and record the exact backend commit. See [Phase 7 contract prerequisite](plans/phase-7-api-contracts.md) and [review](reviews/phase-7-api-contract-review.md).

The development and test environment requires:

- Java 21 (a JDK, not only a runtime)
- Maven Wrapper (`mvnw` or `mvnw.cmd`); a global Maven installation is needed only to regenerate the wrapper
- Docker Desktop or another Docker-compatible runtime for local PostgreSQL and Testcontainers
- Git

The bootstrap was verified with Java 21.0.12.1 and Maven Wrapper 3.9.14 on Windows. Docker Desktop was available for Testcontainers PostgreSQL.

## Environment Variables

Use `.env.example` as a list of safe local-development variable names. Its values are placeholders, not credentials. A local `.env` must remain untracked and must not be copied into logs, issue descriptions, commits, or documentation.

Local variables cover the application profile, server port, PostgreSQL connection and JWT settings. Redis is not required; optional placeholder names do not imply a running dependency.

## Local Workflow

1. Install the verified JDK.
2. Create an untracked local environment file or configure variables through the operating system or IDE.
3. Run the verified build and integration-test command:

   ```powershell
   .\mvnw.cmd clean verify
   ```

4. For local PostgreSQL, configure the values from `.env.example` in an untracked `.env`. Docker Compose reads this file, but Spring Boot does not.
5. Run `.\scripts\start-local.ps1` to import the file into the process, start/wait for Compose PostgreSQL and run the backend. The script requires the local profile and a valid Base64 JWT key. Use `-SkipDatabase` only when the configured PostgreSQL is already running. See [local demo](runbooks/local-demo.md).
6. Review migrations, logs, and the diff before opening a change for review.

The Maven Wrapper command above is the required verification command on Windows. Testcontainers starts an isolated PostgreSQL instance; Docker Compose and a long-running local application are optional local-development workflows.

Application integration tests pair `@SpringBootTest` with test-only `@PostgresApplicationTest`: a Spring-managed PostgreSQL container bean supplies the service connection and `AFTER_CLASS` cleanup closes dependent pools/context before the container. Each class gets an isolated database. Do not reintroduce a static JUnit-owned container while leaving its Spring context cached beyond the class. Exact historical upgrade tests retain independently owned containers and close their minimal Hibernate validation contexts explicitly. No developer database, container reuse flag, longer fork timeout or skipped assertion is required.

## Production Profile And Container Build

For the separate local demonstration, see [Docker demo](runbooks/docker-demo.md). `scripts/start-demo.ps1 -Seed` builds both sibling repositories, retains a dedicated volume and generates private per-installation credentials; it does not require host Java/Node. This local profile/HTTP setup is separate from the optional production profile below.

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

V1–V18 are the merged baseline and must not be edited. Delivered Phase 4A1 adds V19; Phase 4A2 adds V20; Phase 4B1 adds V21; Phase 4B2 adds V22. Subsequent changes must preserve delivered migrations and use later versions. Never use Flyway repair to mask a checksum mismatch; investigate the migration history and add an approved corrective migration when needed.

## Windows and WSL

Campus Service supports native Windows development and WSL-based development. WSL is optional. Use paths, line endings, Docker access, and shell commands consistently within the chosen environment. If Maven or Docker runs in WSL, avoid mixing its generated files with tools configured against a different Windows path unless that workflow has been verified.

Delivered Phase 5 adds V23 (Notification), V24 (Event) and V25 (Library). Preserve V1–V25 for subsequent slices. Historical V24 validation freezes its original 32 entity packages; exact V25 validates 36; future exact upgrades must validate the full new entity set against that same upgraded database/schema with Flyway disabled, never repair migration metadata.

Audit viewing adds owner query ports without a migration/entity. Before future entities are added, freeze historical V25 scanning to its original 36 production entities. Use feature/<function-or-phase> branches and per-slice PASS/FAIL reviews; commit by function only after PASS and authorization. Keep project-roadmap.md local/untracked. Frontend, deployment and next-phase features are not part of Phase 5 backend completion.
