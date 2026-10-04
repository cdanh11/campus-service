# Phase 6B — Detail and CSV review

Result: **PASS**, 2026-10-04, for 6B only. Whole Phase 6 closure remains pending.

- Five typed owner projections: Student OPEN-charge debt, current accommodation, retained section enrollment, retained Event membership, OPEN Library loans. Each adapter's SQL joins only owner tables; foreign references stay UUIDs.
- UUID filters, owner-specific states, strict [from,until) intervals, size 1–100, safe offsets and unique UUID sorting. Finance applies the charge creation interval before aggregation and counts current receipts for those charges; no historical balance is invented.
- CSV shares the exact filters/projection/snapshot, fixed headers, UTF-8, quoted/doubled cells and CRLF. Formula mitigation includes leading whitespace/control/format characters and changes CSV only. Exactly 5,000 records succeeds; 5,001 returns 422 with no partial file; selective exports still succeed.
- Authorization covers all five JSON and five CSV routes (401/403/200), generated OpenAPI Bearer declarations and consistent invalid-filter errors. Account contact/credential/audit fields are absent.
- Tests verify deterministic two-page order and reverse order, empty UUID selection, date boundaries, each resource/state filter, exact aggregate VND beyond long, reversal/cancellation and Unicode/quoted/multiline cells.
- A separate latch-controlled PostgreSQL test commits an additional loan after the count query. Neither the current page nor the current CSV sees it; the next request does. No arbitrary sleep; finally releases the latch/worker and cleans only disposable fixtures.
- Shared ReportJdbc is SQL-agnostic mechanics only, with no table knowledge; the explicit narrow boundary exception allows that class only. Export-limit error belongs to the shared application query contract, not persistence.

Final focused command:

`./mvnw.cmd "-Dtest=ReportContractTest,DetailReportIntegrationTest,DetailReportSnapshotIntegrationTest,DashboardIntegrationTest,DashboardServiceTest,ModuleBoundaryTest" test`

**BUILD SUCCESS**, exit 0, **20 tests/6 suites**, zero failures/errors/skips, **1m31s**, finished 2026-10-04T17:57:43+07:00. All three pools/context close cleanly. `git diff --check` passes; V1–V25 unchanged, no new entity/migration. No commit/push/deployment.

Next gate: 6C safe request observability, exact production-query plan/load evidence, full clean verify and whole-phase requirement review. No production SLA or historical reporting claim.
