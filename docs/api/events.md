# Event API — Phase 5B

Phase 5B review PASS; final clean verify passed 360 tests/60 suites (6m29s). Evidence is in the Phase 5B review. Bearer JWT is required. Catalog is readable by authenticated accounts, including DRAFT rows. All mutations and administrative paths require ADMIN. No deployment or runtime provisioning is included.

| Operation | Current behavior |
| --- | --- |
| POST /api/v1/admin/events | code/title/description/startsAt/endsAt/capacity → 201 with Location, DRAFT/version 0 |
| GET /api/v1/admin/events/{id} | Catalog row or 404 |
| PUT /api/v1/admin/events/{id} | Full catalog fields, status and expectedVersion → stored row or 409 |
| GET /api/v1/admin/events | Bounded catalog page |
| GET /api/v1/events/{id} | Authenticated catalog read |
| GET /api/v1/events | Authenticated bounded catalog page |

Code is ROOT uppercase after trimming exactly space/tab/LF/CR/VT/FF; normalized length 2–32 Unicode characters, case-insensitive uniqueness. Title 2–160, description 2–4000 Unicode characters. startsAt/endsAt are UTC-compatible ISO instants with start strictly before end; capacity is a positive JSON integer up to 2147483647, rejecting fractions, numeric strings and overflow. No automatic schedule/state transition exists.

DRAFT may remain draft, open or cancel; OPEN may remain open, close or cancel; CLOSED/CANCELLED terminal. Application checks expectedVersion with a refreshed PostgreSQL write lock. Actor comes exclusively from JWT. Creation/update synchronously write status-only audit; audit failure rolls back business row/version. Stored PostgreSQL precision is returned rather than unsaved timestamps.

List defaults page=0, size=20, sort=startsAt,asc; maximum size=100 and signed-integer-safe offset. Optional q is at most 100 Unicode characters, matches code/title literally (wildcards escaped); optional status is DRAFT/OPEN/CLOSED/CANCELLED. Sort allowlist: code/title/startsAt/endsAt/capacity/status/createdAt/updatedAt, directions asc/desc, UUID ascending tie-breaker. Response content/page/size/totalElements/totalPages.

Safe codes: VALIDATION_FAILED/MALFORMED_REQUEST/INVALID_QUERY_PARAMETER (400), EVENT_NOT_FOUND (404), EVENT_CODE_ALREADY_EXISTS/CONCURRENT_MODIFICATION/INVALID_EVENT_STATE (409), AUDIT_WRITE_FAILED (500). Role denials use the existing security envelope. OpenAPI has distinct CampusEventCreate/Update/Page schemas with Bearer security.

V24 is delivered with reviewed Phase 5B; preserve it in subsequent migrations. Verification applied it only to disposable test databases. Catalog capacity reductions use the actual consumed-seat count under the Event lock; zero-count scaffolding has been removed.

## Registrations

| Operation | Behavior |
| --- | --- |
| POST /api/v1/admin/events/{eventId}/registrations | ADMIN body studentId → 201/Location, REGISTERED/version 0 |
| GET /api/v1/admin/event-registrations/{id} | ADMIN retained membership |
| GET /api/v1/admin/event-registrations | ADMIN filters eventId/studentId/status with bounded page/sort |
| PUT /api/v1/admin/event-registrations/{id} | ADMIN action CANCEL/RESTORE/ATTEND plus expectedVersion |
| POST /api/v1/events/{eventId}/registrations | Linked Student derived from JWT; client Student/actor ignored |
| GET /api/v1/event-registrations/{id} | Only current linked Student owner, foreign/missing 404 |
| GET /api/v1/event-registrations | Own rows only; optional eventId/status; Student filter cannot select ownership |
| PUT /api/v1/event-registrations/{id} | Owner CANCEL/RESTORE plus expectedVersion; ATTEND forbidden |

Admission/restoration requires OPEN, ACTIVE Student and available capacity. Dates do not introduce a hidden cutoff; an OPEN past event still permits admission. ADMIN can register a Student without an Identity account. Repeating POST for an existing Student/event returns conflict, including CANCELLED memberships; restoration uses PUT and that retained record's expectedVersion. REGISTERED/ATTENDED consume a seat; CANCELLED frees it. Restoration keeps UUID/student/event/createdAt, updates latest registeredAt and clears current cancellation time; retained audit records the full action/version sequence. ATTENDED is terminal; ADMIN may confirm attendance on OPEN/CLOSED. Cancellation remains permitted after event cancellation/closure or Student inactivity. Unknown or currently unlinked Student self-service returns 404; ADMIN can still manage retained rows.

Membership mutations acquire refreshed Event then membership locks, check version, count actual consumed seats and synchronously audit. Both capacity and expectedVersion reject fractional/string/overflow JSON rather than coercing it. Version limits are 0–9223372036854775807. Status/link eligibility is checked at the operation decision without cross-module locks; own mutation rechecks the link after waiting for Event. Responses use stored PostgreSQL precision.

Registration query defaults page=0, size=20, sort=registeredAt,desc, maximum size=100 and safe integer offset. Sorts registeredAt/status/createdAt/updatedAt with asc/desc and ascending UUID tie-breaker. Status REGISTERED/CANCELLED/ATTENDED. No cross-module name/contact search. Errors add EVENT_REGISTRATION_NOT_FOUND (404), EVENT_REGISTRATION_ALREADY_EXISTS/EVENT_STUDENT_UNAVAILABLE/EVENT_CAPACITY_EXCEEDED (409), ACCESS_DENIED (403). Invalid capacity reduction is VALIDATION_FAILED (400), with no writes/audit. EventRegistrationCreate/Change/Page OpenAPI schemas are distinct; every route declares Bearer.