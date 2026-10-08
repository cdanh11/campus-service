# Phase 6 — Reporting closure review

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Verdict: **PASS — Phase 6 backend complete**, 2026-10-04. Requirements below were audited against current code, tests, Maven/XML output and Git state; no unresolved blocker/major in the approved scope.

Approved scope: ADMIN eight-group dashboard, Student VND debt, current accommodation, section enrollment, Event membership, OPEN/overdue Library loans, bounded CSV and operational hardening. No paid integrations, frontend, deployment or runtime first-admin provisioning.

## Requirement audit

| Requirement | Authoritative evidence | Current result |
| --- | --- | --- |
| Eight dashboard groups | AdminDashboardController, ten owner contributors, DashboardServiceTest and DashboardIntegrationTest | Focused PASS |
| Five fixed safe detail projections | ReportRow, ReportKind, five owner adapters, DetailReportIntegrationTest | Focused PASS |
| Owner boundary | SQL constants read only owner tables; Reporting contains no SQL; ModuleBoundaryTest permits only SQL-agnostic shared mechanics | Reviewed PASS |
| Snapshot across contributors/count/page/export | Read-only REPEATABLE_READ services/ports; latch-controlled Dashboard/DetailReportSnapshot tests | Focused PASS |
| VND principal/effective receipts/outstanding | Numeric/BigDecimal, exact >long fixture, reversal/cancellation tests; LATERAL query hardened in 6C | Focused PASS |
| Retained memberships/current occupancy/overdue semantics | Explicit state predicates and typed projections; status/deactivation/date-boundary tests | Focused PASS |
| Bounds/filter/sort | ReportSearch/ReportKind validation, parameter binding, UUID unique order, invalid/time/page/filter tests | Focused PASS |
| CSV correctness/limits/security | Same owner queries and snapshot, CsvReportWriter, Unicode/multiline/quote/formula tests, exact 5000/5001 integration | Focused PASS |
| ADMIN auth/OpenAPI/error contract | All ten detail/export routes plus dashboard tested 401/403/200 and Bearer documentation; consistent 400/422 codes | Focused PASS |
| Correlation/log/metrics | HttpObservationFilter tests and HTTP correlation/security/health assertions; no public metrics endpoint | Focused PASS |
| Query/load evidence | Actual production SQL/binds, unforced EXPLAIN ANALYZE, 10000 rows/family, 20000 receipts, four workers/120 reads | Measured PASS in fixture |
| Released migrations/Hibernate | Git V1–V25 diff empty, no new migration/entity; every historical exact upgrade passes, including V24→V25 validation of 36 production entities on the same schema with Flyway disabled | PASS |
| Whole Phase 1–6 regression/package | Persistent ./mvnw.cmd clean verify, independently summed XML and packaged Boot jar | PASS |
| Docs/Git/roadmap | API/ADR/runbook/slice reviews updated, diff check clean; roadmap stays local, no deployment/history rewrite | PASS |

## Focused evidence

6A: BUILD SUCCESS, 9 tests/3 suites, 1m04s. 6B: BUILD SUCCESS, 20 tests/6 suites including 6A, 1m31s. 6C focused: BUILD SUCCESS, 12 tests/4 suites, 1m29s, 2026-10-04T18:06:23+07:00.

Final `./mvnw.cmd clean verify`: **BUILD SUCCESS**, exit 0, **416 tests/74 suites**, zero failures/errors/skips, **10m12s**, finished **2026-10-04T21:07:26+07:00**. Independently summing all 74 TEST-*.xml files yields the same totals. Boot jar packaged (68,268,874 bytes); all 48 Hikari pools close, no closed-connection/fork-shutdown warning found. The persistent session completed beyond 300 seconds; no timeout was classified as failure.

Final synthetic load repeats with correct counts: four workers/120 reads, wall 1,466.400 ms, p50 20.882 ms, p95 186.386 ms, max 220.769 ms; selective plans 0.108–0.203 ms. These supplement the focused measurements, not a production SLA.

Delivery uses feature/reporting and separates dashboard, observability, detail/CSV and documentation commits after PASS. User owns PR/merge into main. The local project-roadmap.md is excluded from staging/delivery; no deployment, database/volume deletion, metadata repair or history rewrite is performed.

See phase-6a-dashboard-review.md, phase-6b-detail-export-review.md, phase-6c-hardening-review.md and ../runbooks/reporting-observability.md for findings resolved, actual commands, measurements and limits.

## Practical limits

Reports are current-state, not a historical ledger/as-of reconstruction. Reference enrichment uses existing ADMIN owner APIs; report SQL never joins foreign people/contact tables. Financial clients must preserve exact integers. UUID ordering is deterministic per response; later requests can see commits. CSV limit bounds output, not scan cost. Measurements exclude network/JWT and production data distributions. Request correlation covers synchronous flows; no distributed/async tracing, paid exporter or public metrics endpoint is introduced. Phase 7 stack/use cases require separate approval.
