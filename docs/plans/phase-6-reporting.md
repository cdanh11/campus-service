# Phase 6 — Reporting

Scope approved by the user on 2026-10-04: ADMIN dashboard, Student VND debt, current accommodation, section enrollment, Event membership and open/overdue Library loan reports; bounded CSV export. No external paid integration, frontend or deployment.

## 6A — Dashboard and owner query contracts

- Eight dashboard groups: Identity, People (Organization/Student/Personnel), Academic, Dormitory, Finance, Notification, Event and Library.
- Owner adapters query only owner tables. Reporting orchestrates application contracts; no foreign SQL/entities/repositories.
- One read-only REPEATABLE_READ transaction gives all contributors the same database snapshot. Counts are current-state counts, not historical reconstruction.
- Finance distinguishes OPEN charge principal, effective RECORDED payments and collectible outstanding; CANCELLED principal is excluded. Use exact VND decimal/integer arithmetic, no float.
- Library overdue means OPEN with due_at strictly before the common report instant. Occupied beds include every ASSIGNED assignment; available beds require ACTIVE building/room/bed and no ASSIGNED assignment.
- Gate: PostgreSQL tests for counts, empty owners, money/reversal/cancellation, overdue boundaries, snapshot consistency, authorization and OpenAPI; boundary review.

## 6B — Detail reports and bounded CSV

- Versioned ADMIN report endpoints for debt by Student UUID, current accommodation, section enrollment, Event membership and Library OPEN/overdue loans.
- Owner identifiers and recorded business data only; no cross-owner SQL or personal contact/password/token payloads. UUID filters enable frontend joins through existing authorized APIs.
- Stable sorting with UUID tie-breaker; page size 1–100 and safe offset bounds, explicit filter validation.
- CSV uses the same filters/projection/snapshot, fixed columns, RFC-style quoting and formula-injection protection. Reject exports over 5,000 rows rather than silently truncating.
- Document current-state semantics: date filters on timestamps do not reconstruct past state. No arbitrary report builder or audit export.
- Gate: complete valid/invalid filter, lifecycle, pagination, CSV boundary and security tests; PASS before 6C.

## 6C — Observability, query/performance evidence and closure

- Correlation IDs and safe structured request logs, no bodies/tokens/credentials; do not publicly expose metrics or health details.
- Query plans and bounded reproducible load evidence on representative report fixtures; report actual measurements without promising production latency.
- Verify no schema changes are needed; preserve V1–V25 and historical exact-upgrade validation. Add migrations only for measured, justified new indexes.
- Full clean verify, diff check, requirement-by-requirement Phase 6 review, API/docs/ADR and local roadmap updates.
- Commit by function only after PASS and existing authorization; keep roadmap out of staging. User creates PR/merge. No opportunistic Phase 7 implementation.

Status: **6A/6B/6C and whole Phase 6 backend reviewed PASS**. Focused gates: 9 tests/1m04s, 20 tests/1m31s and 12 tests/1m29s. Final clean verify: BUILD SUCCESS, 416 tests/74 suites, zero failures/errors/skips, 10m12s, finished 2026-10-04T21:07:26+07:00. See ../reviews/phase-6-final-review.md and the three slice reviews. No new entity/migration; V1–V25 remain unchanged. Frontend/deployment remain outside this completion scope.
