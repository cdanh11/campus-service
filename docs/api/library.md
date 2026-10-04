# Library API — Phase 5C

Phase 5C reviewed PASS on 2026-10-04: genuine V24→V25 exact validation and full clean verify 382 tests/64 suites, zero failures/errors/skips, 7m32s. All routes below require Bearer authentication and ROLE_ADMIN. No Student self-borrow, renewal, reservation, fines or automatic Finance/Notification writes.

Base: `/api/v1/admin/library`.

| Method/path | Contract |
| --- | --- |
| POST /titles | code, title, author; creates ACTIVE title, 201 + Location |
| GET /titles/{id} | retained title |
| PUT /titles/{id} | complete code/title/author/status plus expectedVersion |
| GET /titles | page/size/q/status/sort |
| POST /copies | immutable titleId, inventory code; ACTIVE parent required |
| GET /copies/{id} | retained physical copy |
| PUT /copies/{id} | code/status plus expectedVersion; cannot move copy to another title |
| GET /copies | page/size/q/titleId/status/sort |
| POST /loans | copyId/studentId; ACTIVE title/copy/Student, one OPEN loan per copy |
| GET /loans/{id} | retained loan history |
| PUT /loans/{id}/return | expectedVersion; OPEN→RETURNED, retains original dates and identifiers |
| GET /loans | page/size/copyId/studentId/status/sort |

Server chooses actor, UUID, borrowedAt and dueAt exactly 14 elapsed days later. Extra actor/dueAt fields do not override them. Returned loans remain retained; a subsequent borrow creates a new loan UUID. Return remains permitted after references become inactive and after the due date. No client date, partial return or fine amount is accepted.

Title/copy statuses ACTIVE/INACTIVE are reversible without deleting history. Codes are ROOT-uppercase, normalized unique case-insensitively within each catalog, 2–32 Unicode characters after expansion. Title and author are 2–160 Unicode code points. Trim exactly space, tab, newline, carriage return, vertical tab and form feed at boundaries. No ISBN validation is claimed.

Queries are bounded (page ≥0, size 1–100, integer-safe offset); title/copy q ≤100 code points, literal case-insensitive code/title/author matching as applicable. Defaults: code,asc for catalogs; borrowedAt,desc for loans. Always UUID ascending tie-breaker. Title sorts code/title/author/status/createdAt/updatedAt; copy sorts code/status/createdAt/updatedAt; loan sorts borrowedAt/dueAt/returnedAt/status/createdAt/updatedAt. ReturnedAt nulls follow PostgreSQL native ordering. Pages contain content/page/size/totalElements/totalPages.

expectedVersion must be a nonnegative integer JSON token; fractions, strings, overflow, null and omitted values are invalid. Conflicts do not change rows, versions or audit. Errors use the existing safe timestamp/status/code/message/path/traceId envelope: 400 validation/malformed/query, 401 missing credentials, 403 non-ADMIN, 404 LIBRARY_RESOURCE_NOT_FOUND, 409 CONCURRENT_MODIFICATION/INVALID_LIBRARY_STATE/LIBRARY_REFERENCE_UNAVAILABLE/LIBRARY_COPY_ALREADY_LOANED/LIBRARY_UNIQUE_CONFLICT, 500 AUDIT_WRITE_FAILED. Each of the six mutation types records status-only audit in the same transaction.
