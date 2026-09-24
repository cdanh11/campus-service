# Phase 1 Release Runbook

## Purpose

This runbook defines the repository-level release gate for the Phase 1 Campus Service application. It produces and verifies one deployable container image. Selecting a hosting provider, creating production infrastructure, supplying secrets, and provisioning the first administrator remain environment-owner responsibilities.

## Required Configuration

Run the application with `SPRING_PROFILES_ACTIVE=production`. Supply these values through the deployment platform's secret or configuration facility; do not commit them:

- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USERNAME`, and `POSTGRES_PASSWORD`.
- `JWT_SECRET`: a Base64-encoded secret of at least 256 bits.
- `ALLOWED_ORIGINS`: the exact browser origins allowed to call refresh and logout endpoints.
- Optional approved overrides: `SERVER_PORT`, JWT issuer/audience and token lifetimes, and refresh-cookie name/path/SameSite.

The production profile runs Flyway, requires Hibernate schema validation, enables graceful shutdown, and always uses a Secure refresh cookie. Startup must fail rather than use local database credentials when required production values are absent.

## Release Gate

1. Confirm the release commit is on `main` and the working tree is clean.
2. Run `./mvnw clean verify` (`.\mvnw.cmd clean verify` on Windows).
3. Confirm all Flyway migrations validate and the V4-to-V5 upgrade integration test passes.
4. Build the image with `docker build --tag campus-service:<version> .`.
5. Scan the image using the organization's approved image scanner before publishing it.
6. Publish by immutable digest to the approved registry; do not deploy a mutable `latest` tag.
7. Configure the production environment and database backup policy before starting the application.
8. Deploy one instance, then verify `/actuator/health/liveness` and `/actuator/health/readiness` return `UP` without exposing component details.
9. Verify a database backup exists before applying migrations to an existing environment. Never run Flyway repair as a deployment step.
10. Follow [Initial Administrator Provisioning](initial-admin-provisioning.md) through the separately approved operational procedure.

## Rollback And Recovery

- Roll back application code by redeploying the previous immutable image digest.
- Do not automatically reverse an applied database migration. Stop the rollout and use the approved database recovery procedure if a migration causes failure.
- Do not restore a database without environment-owner approval and a verified recovery point.
- Preserve application, platform, and Flyway logs without recording credentials or tokens.

## Completion Evidence

Record the Git commit, image digest, CI result, migration result, health-probe result, deployment time, operator, and rollback image digest. Exclude passwords, JWT secrets, raw refresh tokens, database connection strings, and other private data.
