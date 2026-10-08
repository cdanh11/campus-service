# Phase 3A — Program and Course Catalog

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

## Scope and ownership

Academic owns programs and courses. Each has a UUID, normalized unique code (2–32 PostgreSQL characters), name/title (2–160 characters), ACTIVE/INACTIVE status, organization unit UUID, row version and timestamps. Course credits are integers 1–30. Creating or updating requires an active organization unit checked through its application service; Academic never imports Organization persistence internals.

Boundary trimming covers space, tab, newline, carriage return, vertical tab and form feed. Codes use Locale.ROOT uppercase with length validated after conversion. V12/V13 also enforce case-insensitive uniqueness. No program-course relationship, term, section, enrollment, public self-service or deletion API is included. Existing organization deactivation does not cascade into catalog rows.

## Administrative HTTP contract

Both `/api/v1/admin/academic/programs` and `/api/v1/admin/academic/courses` require ADMIN:

- POST creates; status defaults to ACTIVE. Response is 201 with Location.
- GET /{id} reads a record.
- PUT /{id} replaces profile/status/ownership; requires nonnegative expectedVersion from the previous read. The adapter locks and refreshes the row before comparing versions. Changed writes advance rowVersion, preserve createdAt and refresh updatedAt. No-op updates need not advance the JPA version.
- GET searches code and name/title, case-insensitively, with literal wildcard characters. q is trimmed, at most 100 characters; an empty query means no text filter. Optional status is ACTIVE/INACTIVE.
- page is zero-based (default 0), size is 1–100 (default 20); page × size must fit the JPA integer offset. Response contains content, page, size, totalElements and totalPages; pagination/filtering/sorting run in PostgreSQL.
- sort defaults to code,asc. Both catalogs allow code, status, createdAt and updatedAt; Programs also allow name; Courses allow title and credits. Direction is asc/desc; id ascending breaks ties.

Errors use timestamp/status/code/message/path/traceId. Validation/malformed query is 400, missing record 404, duplicate code, stale version or unavailable organization 409; unauthenticated/unauthorized requests are 401/403. OpenAPI is generated from controllers and DTOs.

## Migration and review gate

V1–V11 are unchanged. Upgrade verification migrates one disposable PostgreSQL schema to V11, inserts representative identity/role/organization/student/personnel/audit fixtures, captures records/history, then applies exactly V12/V13. It checks preservation, column types/length/nullability/defaults, primary/unique/foreign keys, indexes and approved constraints through valid and invalid writes with SQLSTATE assertions. A minimal JPA context scans production entities and validates the same upgraded public schema with Flyway disabled; history remains unchanged.

Phase 3A requires domain, API authorization/validation/update/concurrency/search tests, focused upgrade verification and full clean verify before PASS and commit/push. Mutation audit expansion belongs to Phase 3D; Academic audit is not claimed in this subphase. Phase 3B must be scoped and reviewed separately.

## Verified outcome — 2026-10-03

Review: PASS for Phase 3A. Focused tests: BUILD SUCCESS, 31 tests, 3m46s. Full clean verify: BUILD SUCCESS, 121 tests, no failures/errors/skips, 5m29s. See `docs/testing.md` for the commands and scope. V1–V11 are unchanged. Phase 3B has not started; merge is the repository owner's decision.
