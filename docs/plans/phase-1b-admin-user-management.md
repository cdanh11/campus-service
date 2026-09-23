# Phase 1B Slice 3: Administrator User Management

## Status

Approved on 2026-09-22. Implementation may begin only from this accepted version. V1 through V4 are immutable.

## Scope And Security

Only authenticated `ADMIN` users manage Identity users. Spring Security requires `ROLE_ADMIN` for `/api/v1/admin/**`; application use cases also require the authenticated JWT subject UUID as actor identity. Public registration, deletion, email delivery, MFA, OAuth, Redis, frontend work, and generic CRUD are excluded.

An active administrator is exactly an `ACTIVE` user currently assigned `ADMIN`. `SUSPENDED` and `DISABLED` administrators do not count. Existing account statuses remain `ACTIVE`, `SUSPENDED`, and `DISABLED`; no new status is added.

## DTO Contracts

| Endpoint | Request | Success |
| --- | --- | --- |
| `POST /api/v1/admin/users` | `email`, `displayName`, `initialPassword`, non-empty distinct `roles`, optional `status` defaulting to `ACTIVE` | `201 Created`, `AdminUserResponse` |
| `GET /api/v1/admin/users` | query contract below | `200 OK`, `AdminUserPageResponse` |
| `GET /api/v1/admin/users/{userId}` | UUID path | `200 OK`, `AdminUserResponse` |
| `PATCH /api/v1/admin/users/{userId}/status` | `status`, non-negative `expectedVersion` | `200 OK`, `AdminUserResponse` |
| `PUT /api/v1/admin/users/{userId}/roles` | non-empty distinct `roles`, non-negative `expectedVersion` | `200 OK`, `AdminUserResponse` |
| `POST /api/v1/admin/users/{userId}/password-reset` | `newPassword`, non-negative `expectedVersion` | `204 No Content` |

`AdminUserResponse` contains only `id`, `email`, `displayName`, `status`, `roles`, `securityVersion`, `rowVersion`, `createdAt`, and `updatedAt`. It never contains passwords, hashes, refresh values/hashes, sessions, JWTs, or audit data. `/me` is unchanged in Slice 3 and does not return `displayName`.

`email` is normalized by the existing trim/lowercase rule. `displayName` is required, trimmed, non-blank, 2-100 Unicode characters after trimming, and not unique. `roles` are replacement semantics, not additive; each element is non-null/non-blank, valid, and distinct. An `ADMIN` may create another `ADMIN`; every created user must receive at least one valid existing role. `status` may initially be `ACTIVE`, `SUSPENDED`, or `DISABLED`.

`initialPassword` and `newPassword` are required, non-blank, 12-64 Unicode characters, and at most 72 UTF-8 bytes. Validation occurs before BCrypt encoding. Plaintext passwords are never returned, persisted, audited, or logged; only structurally valid BCrypt values reach the domain/persistence boundary.

## Query Contract

`GET /api/v1/admin/users` uses `page` (zero-based, default 0, minimum 0), `size` (default 20, 1-100), optional trimmed `q` (email/display-name search, maximum 100 characters), optional exact `status`, optional exact `role`, and `sort` exactly in `field,direction` syntax.

Allowed sort fields are `email`, `displayName`, `status`, `createdAt`, and `updatedAt`; directions are `asc` and `desc`. Default ordering is `createdAt,desc` with `id,asc` deterministic tie-breaker. Empty results return `200` with `content: []` and complete page metadata: `page`, `size`, `totalElements`, and `totalPages`. Invalid page, size, filter, enum, sort field, direction, or syntax returns `400 INVALID_QUERY_PARAMETER`.

## Status, Self, And Role Policies

| From | Allowed target |
| --- | --- |
| `ACTIVE` | `SUSPENDED`, `DISABLED` |
| `SUSPENDED` | `ACTIVE`, `DISABLED` |
| `DISABLED` | `ACTIVE` |

The same status is idempotent. Other transitions return `409 INVALID_STATUS_TRANSITION`.

An actor cannot change their own status, replace their own roles, or reset their own password through this API. Each returns `409 SELF_MODIFICATION_NOT_ALLOWED`, independently of final-admin protection. Self-reset is out of scope.

## Final Active Administrator Protocol

V5 creates `identity_admin_guard(guard_id SMALLINT PRIMARY KEY)` and seeds exactly row `guard_id = 1`.

Every transaction that can reduce active administrators must first execute `SELECT guard_id FROM identity_admin_guard WHERE guard_id = 1 FOR UPDATE`, retain that lock until commit/rollback, then lock/load the target user. It recalculates users with both `status = ACTIVE` and assigned `ADMIN`, rejects with `409 LAST_ACTIVE_ADMIN_REQUIRED` if the mutation leaves fewer than one, then performs mutation, session revocation, and audit in the same transaction.

This applies to removing `ADMIN`, replacing a role set that removes `ADMIN`, and changing an active administrator to `SUSPENDED` or `DISABLED`, including future reducing operations. Adding `ADMIN` does not require final-admin rejection, but follows normal optimistic-lock rules. Target-only locks, unlocked counts, JVM locks, and in-memory synchronization are prohibited.

## Persistence, Versions, And Audit

V5 adds nullable `display_name`, backfills existing rows with the explicitly approved trimmed value `SUBSTRING(email FROM 1 FOR 100)`, then makes it `NOT NULL`; the migration must reject/repair impossible blank backfills before that constraint. V5 also adds `row_version BIGINT NOT NULL DEFAULT 0`, `identity_admin_guard`, and `identity_admin_audit_events`.

`rowVersion` is mapped as JPA `@Version`, represented in the domain model, returned in admin responses, and checked through required `expectedVersion` mutation requests. Version mismatch or `ObjectOptimisticLockingFailureException` returns `409 CONCURRENT_MODIFICATION`. `securityVersion` remains separate security state and is never JPA `@Version`; security-sensitive status, role, and password changes increment it, while ordinary persistence increments `rowVersion` automatically.

Audit rows contain actor UUID, target UUID, action enum, timestamp, and bounded non-sensitive metadata only. They never contain plaintext passwords, password hashes, raw refresh tokens, token digests, JWTs, signing secrets, or complete request payloads.

## Transaction Rules

Create validates, hashes, persists user/roles, and audits in one transaction; it creates no login session. Suspend/disable, role replacement, and password reset lock as required, revoke every target-user session family, increment `securityVersion`, mutate, and audit in one transaction. Failed mutations must not partially revoke sessions or write success audits. Existing normalized-email uniqueness remains the final concurrency guarantee and maps to `409 EMAIL_ALREADY_EXISTS`.

## Error Contract

Use the existing JSON envelope. Malformed UUID path values return `400 MALFORMED_REQUEST`; valid UUIDs without a user return `404 USER_NOT_FOUND`.

| HTTP | Code | Message |
| --- | --- | --- |
| 400 | `MALFORMED_REQUEST` | `Request is invalid` |
| 400 | `VALIDATION_FAILED` | `Request validation failed` |
| 400 | `INVALID_QUERY_PARAMETER` | `Query parameter is invalid` |
| 400 | `UNKNOWN_ROLE` | `Role is not recognized` |
| 401 | `MISSING_ACCESS_TOKEN` | `Access token is required` |
| 403 | `FORBIDDEN` | `Access is forbidden` |
| 404 | `USER_NOT_FOUND` | `User was not found` |
| 409 | `EMAIL_ALREADY_EXISTS` | `Email is already in use` |
| 409 | `INVALID_STATUS_TRANSITION` | `Account status transition is not allowed` |
| 409 | `SELF_MODIFICATION_NOT_ALLOWED` | `Self modification is not allowed` |
| 409 | `LAST_ACTIVE_ADMIN_REQUIRED` | `At least one active administrator is required` |
| 409 | `CONCURRENT_MODIFICATION` | `User was modified concurrently` |

## Domain And Implementation Touchpoints

Update `UserAccount` factory, constructor, rehydration, invariants, entity, mapper, repository ports/adapters, DTOs, and tests for `displayName` and `rowVersion`. Add locked user lookup, guard-row lock, active-admin recount, page search, and bulk session-family revocation. Add `@Version` only to the new row-version mapping.

## Exact Test Matrix

- DTO display-name, password character, UTF-8 72-byte, role, and expected-version boundaries.
- Display-name normalization/backfill; pagination/filter/sort syntax, deterministic ties, and empty results.
- Malformed UUID versus unknown UUID; duplicate email; create user including ADMIN role.
- Anonymous 401 JSON, non-admin 403 JSON, and admin allow paths.
- Self-status, self-role, and self-password-reset rejection.
- Allowed/invalid status transitions; unknown/duplicate/empty roles.
- Row-version conflict and separation from securityVersion.
- Two concurrent removals of two active admins serialize through guard row: exactly one succeeds, one returns `LAST_ACTIVE_ADMIN_REQUIRED`, and one active admin remains.
- Suspend/disable, role replacement, and reset revoke sessions atomically; failed mutation leaves sessions/audit unchanged.
- Audit commit/rollback and absence of password/hash/token fields from APIs and audit rows.
- V1-V5 migration from empty PostgreSQL Testcontainers, plus all existing Slice 2 and health/bootstrap tests.

## Documentation And Completion

Update `README.md`, `docs/development.md`, `docs/testing.md`, and `docs/architecture.md` only after verification. Correct architecture documentation to describe accepted ADR 0003 and completed Slice 2, but do not claim Slice 3 implementation until all checkpoints pass.

## Checkpoints

1. V5, domain/persistence, display-name backfill, versions, guard lock, audit, and PostgreSQL tests.
2. Use cases, security, DTOs, controller, errors, and admin authorization tests.
3. Session/audit atomicity, full concurrency matrix, documentation, and `clean verify`.
