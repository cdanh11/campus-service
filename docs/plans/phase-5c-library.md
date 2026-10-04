# Phase 5C — Library Catalog and Circulation

Approved business scope on 2026-10-04: title catalog and physical copies; one open loan per copy; default due time 14 days; return retains history; no fines. ADMIN alone records loan/return. Implement only after Event 5B review PASS. This is a plan, not an implementation claim.

## Proposed technical contract

- Library owns titles, physical copies, retained loans and status-only mutation audit. Titles have UUID, unique normalized code, title, author and ACTIVE/INACTIVE status; copies have UUID, unique inventory code, titleId and ACTIVE/INACTIVE status. Standard text/version/timestamp conventions apply. Avoid claiming ISBN validation, reservations, renewals, lost-book charges or acquisitions.
- New loan uses an ACTIVE copy/title and ACTIVE Student via Student application contract. ADMIN actor comes from JWT. Store borrowedAt and dueAt=borrowedAt+14 days using server UTC time; return stores returnedAt without overwriting the original loan history. Returning a retained open loan remains allowed after reference deactivation. New loan after return is a new history record.
- Serialize admission through title then copy locks; loan updates use the same lock order before loan. Partial unique index on copyId for open loans provides database duplicate-loan protection. Reference UUIDs are scalar FKs, not foreign entity relationships. Copy/title deactivation does not erase existing loans; it blocks new admission.
- ADMIN bounded create/get/list/update catalogs and create/get/list/return loans; allowlisted filters/sorts with UUID tie-breakers and literal search. Distinct versioned OpenAPI schemas, safe existing error envelope, audit in the same business transaction. No client-controlled actor or automatic Finance/Notification integration.

## Gate

Domain boundary/Unicode/lifecycle/default-date tests; anonymous and USER denials on all operations; inactive/missing references, duplicates, stale updates and overdue return; rollback for each mutation. Separate-transaction same-copy competition, cached reference changes and production lock timeout 55P03 followed by success, bounded synchronization/cleanup. Pagination/tie pages and selective query evidence.

Use the next migration after Event, never edit delivered V1–V24. Freeze historical Event entity scanning; migrate representative populated prior schema to exactly the new Library migration, compare all old data/history, verify actual columns/defaults/CHECKs/PK/FKs/partial unique/indexes and SQLSTATE writes. Validate all actual production entities on that exact database/schema with Flyway disabled and ddl-auto=validate. Focused/full Maven verification and source/diff/docs review before PASS; no invented entity/test totals in advance.
