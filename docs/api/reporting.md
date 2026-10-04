# Reporting API — Phase 6 in progress

All routes require a current JWT with ADMIN authority. Missing authentication returns 401; a regular account returns 403. No report writes or domain mutations are exposed.

## Dashboard

`GET /api/v1/admin/reports/dashboard`

Response: `asOf` (common observation instant), `currency: VND`, `groups` (eight groups of numeric metrics). Values are exact counts or integral VND sums, not floating-point approximations. Clients handling finance must preserve arbitrary-precision decimal values rather than JavaScript Number rounding.

| Group | Metrics |
| --- | --- |
| IDENTITY | users, active_users |
| PEOPLE | organization_units, active_organization_units, students, active_students, personnel, active_faculty, active_staff |
| ACADEMIC | programs, courses, active_terms, open_sections, enrolled_memberships |
| DORMITORY | beds, occupied_beds, available_beds |
| FINANCE | charges, open_charges, open_principal_vnd, effective_paid_vnd, outstanding_vnd |
| NOTIFICATION | published_notices, deliveries, unread_deliveries |
| EVENT | open_events, registered_memberships, attended_memberships |
| LIBRARY | titles, copies, open_loans, overdue_loans |

All contributors share one read-only REPEATABLE_READ database snapshot. `asOf` is used for overdue boundaries and is not a historical reconstruction parameter. There are no dashboard filters yet.

`effective_paid_vnd` sums only RECORDED receipts, after preaggregation per charge to avoid multiplying principal. `outstanding_vnd` sums OPEN charge amount minus effective receipts; CANCELLED charges have zero collectible outstanding. Reversed receipts remain history and contribute zero. Counts include retained records unless the metric explicitly names a state.

`occupied_beds` counts ASSIGNED assignments even if inventory is later inactive. `available_beds` requires an ACTIVE building, room and bed with no ASSIGNED assignment. `overdue_loans` counts only OPEN loans with `due_at < asOf`; due exactly at the boundary and returned loans are excluded.

The response contains no passwords, tokens, contact fields or arbitrary audit metadata. OpenAPI declares Bearer authentication. Query/load evidence and final full regression remain 6C gates.

## Detail reports

`GET /api/v1/admin/reports/{report}` and `GET /api/v1/admin/reports/{report}/export`.

| report enum (case-sensitive) | Content | resourceId | status | Time interval filters |
| --- | --- | --- | --- | --- |
| STUDENT_DEBT | OPEN charge count/principal/effective paid/outstanding grouped by Student UUID | Unsupported | Unsupported | Charge created_at, before aggregation |
| CURRENT_ACCOMMODATION | ASSIGNED stays, bed/room/building UUID and assignedAt; includes inactive inventory parents | Bed UUID | Unsupported | assigned_at |
| SECTION_ENROLLMENT | Retained membership, Student/section/offering/term/course UUID, state and updatedAt | Section UUID | ENROLLED or WITHDRAWN | updated_at |
| EVENT_MEMBERSHIP | Retained membership, Event code/title, Student UUID, state and registration/cancel/attendance times | Event UUID | REGISTERED, CANCELLED or ATTENDED | registered_at |
| LIBRARY_LOANS | OPEN loan, Student/copy/title UUID, copy code/title, borrowedAt/dueAt and overdue flag | Copy UUID | Unsupported | borrowed_at |

All reports accept `studentId`. Time filters `from`/`until` use ISO UTC instants and inclusive/exclusive bounds `[from,until)`; an empty/inverted interval returns 400. Omitted state filter includes all retained enrollment/Event states. `overdueOnly=true` is supported only for LIBRARY_LOANS. Unsupported filters return 400 instead of being ignored. Unknown UUIDs yield an empty result; no claim is made that these owner references still have ACTIVE foreign records.

Pagination: `page=0`, `size=20`, size 1–100; offset must fit a signed 32-bit integer. Sort: `id,asc` (default) or `id,desc`. STUDENT_DEBT orders by Student UUID; other reports order by record UUID. UUID is unique, giving a deterministic order even for tied timestamps. Pages use a consistent count/row snapshot, but separate HTTP requests can observe subsequent commits.

Response fields: `report`, `asOf`, `columns`, `content`, `page`, `size`, `totalElements`, `totalPages`. Content is a fixed typed projection; no arbitrary column selection. Debt totals consider only OPEN charges selected by the creation-time interval and every currently RECORDED receipt for those charges, regardless of receipt date. A fully paid OPEN charge remains in the group with zero outstanding. CANCELLED charges are omitted; this is not a historical balance as of `until`.

CSV accepts the same non-pagination filters and sort, exports every selected row (not just the current page), and caps results at **5,000 rows**. Over the cap returns 422 `REPORT_EXPORT_LIMIT_EXCEEDED`, without a partial file. Headers follow `columns` order; every UTF-8 cell is quoted, embedded quotes doubled and records use CRLF. Spreadsheet formula prefixes, including after leading whitespace/control/format characters, receive an apostrophe in CSV only. JSON/stored values remain unchanged. Response uses `text/csv;charset=UTF-8`, fixed attachment filename and `Cache-Control: no-store`.

Invalid report enum, UUID, state, sort, bounds or unsupported filters return 400 `INVALID_QUERY_PARAMETER`. Read-only reporting creates no audit mutation, schema change or business data modification.

Synchronous responses include `X-Request-ID`; a canonical UUID supplied by the caller is accepted, otherwise a new UUID is generated. Report error `traceId` matches this header. Correlation, safe completion events, in-process metrics and measured query/load limitations are documented in [Reporting observability](../runbooks/reporting-observability.md). CORS/frontend integration is a Phase 7 contract decision; this phase does not loosen existing security policy.
