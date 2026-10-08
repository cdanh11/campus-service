# Phase 3B — Academic terms, offerings and sections

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

## Implementation plan and boundaries

Build in dependency order: AcademicTerm, CourseOffering, ClassSection. Use ADMIN-only POST/GET/list/PUT APIs, bounded database queries and expectedVersion on mutations. Academic owns all three resources and exchanges faculty information through the Personnel application service. No enrollment, schedules, rooms, grades, fees or frontend work is included. Preserve V1–V13; add V14/V15/V16 and exact V13→V16 upgrade validation.

## Business rules

- Term: normalized unique code 2–32 characters, name 2–160, inclusive LocalDate start/end with start <= end. Lifecycle PLANNED → ACTIVE → CLOSED; PLANNED may be CANCELLED. CLOSED/CANCELLED are terminal. Dates become immutable after activation. Closing requires no OPEN offerings.
- Offering: one course per term, UUID and organization ownership copied from its course at creation. References are immutable after creation. The course and owning organization must be ACTIVE when creating/opening. Term must be PLANNED or ACTIVE at creation. All delivery mutations lock term, then offering, then section where applicable; this serializes parent closure and child opening without arbitrary sleeps. Lifecycle DRAFT → OPEN → CLOSED; DRAFT may be CANCELLED. OPEN requires ACTIVE term. Closing requires no OPEN sections.
- Section: normalized code 2–32 unique within its offering, positive integer capacity, optional single faculty assignment while DRAFT. OPEN requires OPEN offering, ACTIVE term and active FACULTY assignment. STAFF cannot be assigned; cross-organization teaching is allowed. Faculty Identity account is not required. Faculty must be valid whenever newly assigned; code, capacity and faculty are frozen after opening, because enrollment will depend on them in 3C. Lifecycle DRAFT → OPEN → CLOSED; DRAFT may be CANCELLED.
- No implicit cascade lifecycle changes or physical deletion. Closed/cancelled records are retained. Catalog/organization/personnel deactivation does not cascade into historical records; opening performs current-reference validation.
- Text normalization follows the six explicit ASCII whitespace characters used by the catalog; code length is checked after ROOT uppercase.

The user approved optional faculty while DRAFT and mandatory active FACULTY when OPEN on 2026-10-03.

## Verification gate

Domain lifecycle/date/course-reference rules; ADMIN authorization and uniform errors; create/read/query/update and duplicate/stale writes; competing updates; faculty/organization/catalog boundaries; migration columns/defaults/constraints/indexes/data preservation; minimal Hibernate ddl-auto=validate on the exact V13→V16 upgraded schema with Flyway disabled. Run focused tests and full clean verify, review diff and update actual evidence before PASS. Commit by function after PASS; roadmap remains local.

Status: implemented; final review PASS on 2026-10-03; merged through PR #9 at `a903c3e`.

## HTTP contract

All routes require ROLE_ADMIN. POST starts a term as PLANNED and an offering/section as DRAFT; lifecycle changes use PUT with nonnegative expectedVersion from a previous read. GET /{id} returns the resource. References termId/courseId on offerings and offeringId on sections are immutable; offering ownership is copied at creation. No DELETE is exposed.

List responses contain content/page/size/totalElements/totalPages. page defaults to 0; size defaults to 20, allowed 1–100; page × size must fit JPA's integer offset. Optional status is validated against each resource's lifecycle. Terms support literal case-insensitive q on code/name (maximum 100 characters), sorting by code/name/startDate/endDate/status/createdAt/updatedAt, default code,asc. Offerings filter by termId/courseId and sort by status/createdAt/updatedAt, default createdAt,desc. Sections support literal q on code and offeringId filter, sorting by code/capacity/status/createdAt/updatedAt, default code,asc. Each sort ends with id ascending as a deterministic tie-breaker.

Errors share timestamp/status/code/message/path/traceId: validation/malformed request/query 400, missing Academic resource 404, duplicate resource/stale version/invalid lifecycle/unavailable external reference 409. Dates and capacity are enforced by both domain and PostgreSQL. FK guarantees existence of faculty; ACTIVE/FACULTY rules remain in the application layer. Opening requires current reference validation; closing historical records remains possible after external deactivation.

CLOSED/CANCELLED are terminal lifecycle states, rather than a blanket ban on every metadata edit. Term dates are frozen after PLANNED; section code/capacity/faculty are frozen after DRAFT. Draft children retained under closed/cancelled parents cannot be opened. Reference identifiers never change.

## Verified outcome

- Focused domain/API/upgrade + historical V13 validation: BUILD SUCCESS, 39 tests, 1m04s.
- Full clean verify: BUILD SUCCESS, 154 tests, no failures/errors/skips, 3m53s.
- Review checks domain policies, HTTP contracts, migration constraints/defaults/FKs, legacy preservation, exact Hibernate validation, lifecycle lock order, version rollback, bounded queries and module boundaries. V1–V13 unchanged; migrations added are V14–V16.
- Roadmap remains local; commit groups separate domain/schema, administrative APIs, migration tests and documentation. Enrollment is tracked separately in the Phase 3C plan.
