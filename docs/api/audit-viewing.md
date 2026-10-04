# ADMIN audit viewing — Phase 5D

Phase 5D reviewed PASS on 2026-10-04: final focused 17 tests (1m10s), full clean verify 393 tests/67 suites (7m36s), zero failures/errors/skips. Bearer authentication and ROLE_ADMIN are required for both read routes. No audit mutation/delete/export, automatic expiry or cross-source merged page.

| Route | Contract |
| --- | --- |
| GET /api/v1/admin/audits/{source} | One source; bounded count/page and filters |
| GET /api/v1/admin/audits/{source}/{id} | One recorded event from that same owner source |

source is uppercase IDENTITY, PEOPLE, ACADEMIC, DORMITORY, FINANCE, NOTIFICATION, EVENT or LIBRARY. Source-specific resources:

| Source | Resource filter |
| --- | --- |
| IDENTITY | USER (API label for the historical target_user_id, not a new schema field) |
| PEOPLE | ORGANIZATION_UNIT, STUDENT, FACULTY_STAFF |
| ACADEMIC | PROGRAM, COURSE, TERM, COURSE_OFFERING, CLASS_SECTION, ENROLLMENT |
| DORMITORY | BUILDING, ROOM, BED, ASSIGNMENT |
| FINANCE | FEE, CHARGE, PAYMENT |
| NOTIFICATION | TEMPLATE, NOTICE, DELIVERY |
| EVENT | EVENT, REGISTRATION |
| LIBRARY | TITLE, COPY, LOAN |

List filters: optional targetId/actorId UUID, resource, action, from/until ISO instants. Time interval is **[from, until)**; when both present from must be less than until. Action is an uppercase/underscore token bounded by the actual source's column (Identity 64, People/Academic 32, other sources 16); an unknown but valid action returns an empty result, without inventing database action constraints. Unsupported resource/type/source/time/sort/bounds produce 400 INVALID_QUERY_PARAMETER.

page defaults 0 (≥0), size defaults 20 (1–100), offset must fit an integer. sort accepts only occurredAt,asc or occurredAt,desc (default); UUID always ascending breaks timestamp ties. Each owner's REPEATABLE_READ read-only transaction makes count and page use one database snapshot. Response: content/page/size/totalElements/totalPages. This is an offset page in a selected source, not a cross-source timeline or cursor that freezes data across separate requests.

Events expose id/source/resource/targetId/actorId/action/resourceVersion/occurredAt/metadata. Identity and People never recorded resourceVersion, so it is null; metadata is always {} because the writers only recorded {} and no historic private text is approved for exposure. Other sources expose only status values actually recorded by that resource's owner. PostgreSQL projects the single string status key; unsupported/nested/null/unrecognized values become {}. Arbitrary body/reason/password/token/nested metadata is not returned; underlying history remains untouched. No JSONB length limit is added. Versions and times come from stored history, never current resource rows.

401 missing/invalid authentication; 403 non-ADMIN; 404 AUDIT_EVENT_NOT_FOUND for a missing event within the selected source. Errors use timestamp/status/code/message/path/traceId without raw SQL or metadata. Existing target-prefixed audit indexes serve selective target/resource queries; no global history latency or indexed unfiltered page guarantee is claimed.
