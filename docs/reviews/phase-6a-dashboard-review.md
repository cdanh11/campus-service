# Phase 6A — Dashboard review

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Result: **PASS for the 6A dashboard slice**, 2026-10-04. This is not Phase 6 closure or full-regression evidence.

## Requirements and evidence

- ADMIN dashboard: one GET operation, eight groups from ten owner contributors. PostgreSQL integration verifies 401/403/200 and generated Bearer OpenAPI.
- Boundaries: reviewed all ten OwnerDashboardAdapter SQL constants; each reads only owner tables. Reporting contains no SQL/foreign entities. ModuleBoundaryTest passes.
- Snapshot: read-only REPEATABLE_READ. A latch-controlled independent Organization insert after the Identity query is excluded from the running response, then appears in the next. Cleanup joins the worker and removes only its disposable test fixture.
- Counts: fixtures verify People, Academic, Notification and Event states. Retained enrollment remains counted after Student deactivation. Empty groups return zero.
- Finance: receipt preaggregation prevents multiplied principal; only RECORDED payments count; cancellation removes collectible outstanding. JDBC BigDecimal/PostgreSQL NUMERIC avoid float arithmetic. Tests verify successive reversal/cancellation.
- Dormitory: occupied includes ASSIGNED after parent deactivation; available requires ACTIVE parents and no assignment; release clears occupancy.
- Library: fixed instant verifies strictly-before due boundary, due-at-boundary exclusion and returned-history exclusion. Inactive titles do not erase loans.
- Unit tests reject missing/duplicate contributors and negative/duplicate metrics; all contributors receive the same instant. No private account/contact/audit payload is projected.
- V1–V25 diff empty; no entity/schema/history change or historical reconstruction.

## Verification

`./mvnw.cmd "-Dtest=DashboardServiceTest,DashboardIntegrationTest,ModuleBoundaryTest" test`

**BUILD SUCCESS**, exit 0, **9 tests/3 suites**, zero failures/errors/skips, **1m04s**, finished 2026-10-04T17:42:34+07:00. Pool/context close cleanly. Earlier attempts resolved a test generic type error and Library fixture column typo.

`git diff --check` passes. No commit/push/deployment performed. Remaining: 6B detail/filter/pagination/CSV and 6C observability/query/load/full regression. No production throughput claim. Frontend must preserve exact VND values.
