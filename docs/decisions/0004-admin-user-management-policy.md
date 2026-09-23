# ADR 0004: Administrator User Management Policy

## Status

Accepted on 2026-09-22. Implementation may begin only from this accepted decision.

## Decision

Only JWT-authenticated `ROLE_ADMIN` actors manage Identity users. Actor identity is the validated JWT subject UUID. Public registration, deletion, self-administration, email delivery, and temporary-password generation are excluded.

Administrators supply initial/reset passwords subject to Slice 3 password validation. Security-sensitive status, role, and password changes revoke every target session family transactionally and increment `securityVersion`; short-lived JWTs remain valid until their existing expiry.

The service preserves at least one active administrator using PostgreSQL singleton guard-row locking. Every reducing operation locks `identity_admin_guard.guard_id = 1 FOR UPDATE`, locks the target, recounts active users assigned `ADMIN`, rejects `LAST_ACTIVE_ADMIN_REQUIRED` when needed, and retains the guard lock through commit/rollback. This is not an in-memory or target-only lock.

`rowVersion` is a separate JPA `@Version` concurrency field. `securityVersion` is never used for JPA optimistic locking. Mutations require expected row version and conflicts return `CONCURRENT_MODIFICATION`.

Administrative audit events persist only actor, target, action, timestamp, and non-sensitive metadata in the successful mutation transaction. They never contain passwords, hashes, tokens, JWTs, secrets, or complete payloads.

## Consequences

- An external operational process provisions the initial administrator.
- V5+ migrations add display name, row version, guard row, and audit persistence; V1-V4 remain unchanged.
- `/me` remains unchanged; display name is only an admin API field in Slice 3.
