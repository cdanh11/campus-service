# ADR 0016 — Explicit first-administrator provisioning

Status: Accepted, 2026-10-05 (Phase 8 portable demo scope approved by the owner).

## Context

A new installation cannot authenticate before its first ADMIN exists. The previous runbook delegated this to an external procedure. A portable personal-project demo needs a repeatable setup path without a public registration endpoint or hardcoded credentials.

## Decision

Provide an explicit --bootstrap-admin command in the packaged application. It runs a non-web Spring context with the same Flyway, Hibernate validation, Identity repositories, domain validation and delegating bcrypt encoder. Input comes from hidden console prompts (including confirmation) or private process environment for setup wrappers; passwords are never command arguments. Normal web startup does not invoke it.

Provisioning obtains the existing Identity admin guard and refuses when any ADMIN exists, even suspended/disabled. Bootstrap is not recovery. Account and initial USER_CREATED audit commit atomically; the new administrator is both actor and target because there is no predecessor. Metadata is empty and contains no credential/contact snapshots. Unique email constraints remain authoritative.

## Consequences and verification

CLI startup closes its context and exposes no HTTP listener. Existing servlet authorization remains unchanged; its filter chain is conditional on servlet startup. The CLI uses a bounded PostgreSQL lock timeout. Integration tests must prove password hashing/profile validation, inactive-admin refusal, rollback on audit failure and concurrent first-account creation. Portable setup wrappers must keep configuration/credentials ignored and distinguish demo data loading from provisioning and normal application startup.

After setup, additional administrators are created through the normal authenticated account API. Reset/recovery requires a separate approved procedure; neither this command nor demo scripts may delete data or bypass the final-active-ADMIN guard.
