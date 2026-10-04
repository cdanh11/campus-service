# Phase 6C — Hardening review

Status: **PASS**, following full regression and requirement audit on 2026-10-04. Focused and full evidence are distinguished below.

## Verified scope

- Synchronous request UUID correlation before security/MVC, safe JSON completion messages, MDC cleanup and in-process duration/count metrics. Route templates, method allowlist and status are the only metric tags; no URI/query/body/credentials/request-ID tags.
- Unit tests capture logs to verify valid JSON and absence of private fixture values; malformed/oversized/log-injection header is replaced. Exception paths restore prior MDC and record 500 without swallowing the original exception.
- PostgreSQL HTTP integration verifies the report error traceId matches the response header; 401/403 also retain the header. Metrics is not publicly exposed (401 anonymously, 404 for ADMIN); health has no details.
- Production owner SQL/binds captured from real report calls; unforced EXPLAIN ANALYZE on 10,000 rows/family and 20,000 receipts uses existing selective indexes in all five owners. Academic can use its existing Student/section unique index instead of Student/status, both valid access paths.
- Finance selective debt projection uses owner-local LATERAL receipt aggregation with the existing charge/status index, avoiding aggregation of the entire receipt table for a single Student. Full-dashboard preaggregation remains appropriate for its whole-campus sum.
- Four workers complete 120 application reads with correct count/content, including selective/unfiltered detail pages and dashboards. Measurement and limitations are in ../runbooks/reporting-observability.md. No forced planner flags, new index, broker/cache/read-model schema or production SLA.
- V1–V25 unchanged; no new entities or Flyway metadata repair. Exact historical upgrades remain in the full regression suite.

## Focused result

`./mvnw.cmd "-Dtest=ReportingQueryLoadIntegrationTest,HttpObservationFilterTest,DetailReportIntegrationTest,ModuleBoundaryTest" test`

**BUILD SUCCESS**, exit 0, **12 tests/4 suites**, zero failures/errors/skips, **1m29s**, finished 2026-10-04T18:06:23+07:00. Selective query execution 0.139–0.508 ms in this fixture; workload wall 1,689.684 ms, p50 30.545 ms, p95 182.855 ms, max 216.979 ms. These exclude network/JWT and concurrent business writes.

## Closure verification

`./mvnw.cmd clean verify` completed BUILD SUCCESS, exit 0, 416 tests/74 suites, zero failures/errors/skips, 10m12s, finished 2026-10-04T21:07:26+07:00. XML totals independently match; Boot jar packaged and 48 pools close cleanly. V1–V25 and historical exact schema validation remain intact; no new entity/migration. Diff check passes. Final requirement audit and limits are in phase-6-final-review.md. No deployment or Phase 7 feature is part of this gate.
