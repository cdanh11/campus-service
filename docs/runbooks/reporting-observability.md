# Reporting and local observability

Phase 6 provides read-only ADMIN dashboard/detail/CSV contracts; see [Reporting API](../api/reporting.md). Run the existing local application with configured PostgreSQL/JWT variables and a provisioned administrator as documented in [Development](../development.md) and [Initial administrator provisioning](initial-admin-provisioning.md). No public provisioning endpoint, deployment or external telemetry service is introduced.

## Correlation

Synchronous HTTP responses include `X-Request-ID`. A canonical UUID header from the caller is accepted; malformed/oversized input is replaced with a generated UUID. Report error responses also expose that ID in `traceId`; older module error contracts may still have a null traceId, so use the response header. MDC requestId is restored/removed at request completion, including failures.

The completion log message is JSON with event, requestId, method, matched route template, status and durationMs. The existing Spring console layout still prefixes the message. Requests rejected before MVC mapping use UNMATCHED. No raw URI, query string, body, account, authorization header, cookie or credential is recorded by this filter. Unexpected methods become OTHER. This is a local correlation strategy, not distributed tracing or an asynchronous-request instrumentation guarantee.

Micrometer timer `campus.http.requests` records duration/count using only method, route template and status tags. Request IDs and resource UUIDs are never metric tags. Existing JVM/framework meters remain available in-process. HTTP Actuator exposure remains health only; `/actuator/metrics` is not exposed and no Prometheus/paid exporter is added. Health probes retain their existing public minimal response. Configure a telemetry sink only through a separate approved operational requirement.

## Reproduce query/workload evidence

```powershell
.\mvnw.cmd "-Dtest=ReportingQueryLoadIntegrationTest" test
```

This starts an isolated PostgreSQL Testcontainer, populates 10,000 Students and 10,000 records in each of the five report families, with 20,000 RECORDED receipts. It captures the actual owner page SQL and bind parameters from production calls and executes unforced `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. It does not modify a developer database or released migration.

Then four workers perform 120 application/database reads: 60 Student-filtered detail pages, 40 unfiltered detail pages and 20 dashboards. Counts/content are asserted under concurrent reads. JSON/CSV authorization and count/page/export races are separate integration tests. This workload excludes HTTP/network/JWT cost, cache layers, external services, concurrent business mutations and production-scale distributions; it is evidence for this local fixture, not a production SLA.

Focused run 2026-10-04T18:06:23+07:00: BUILD SUCCESS, 12 tests/4 suites, 1m29s. Representative measurements:

| Selective report | PostgreSQL execution ms | Existing index evidence |
| --- | ---: | --- |
| Student debt | 0.244 | finance charge student/status and payment charge/status |
| Current accommodation | 0.209 | current Student assignment and parent primary keys |
| Section enrollment | 0.417 | unique Student/section membership |
| Event membership | 0.139 | Event registration Student/status |
| OPEN Library loans | 0.508 | loan Student/status and catalog primary keys |

Workload: wall 1,689.684 ms, nearest-rank p50 30.545 ms, p95 182.855 ms, max 216.979 ms. Timing varies with hardware/container load. The first selective Finance plan spent 43.182 ms aggregating all receipts; owner-local LATERAL aggregation now uses existing charge/status lookup. Dashboard keeps whole-table receipt preaggregation for its whole-campus sum. No new index/migration was justified by this fixture.

Unfiltered dashboards/reports still scan/aggregate current owner data; CSV rejects more than 5,000 records rather than silently truncating. For much larger data, measure actual query cardinality, connection pressure and execution plans before approving additional indexes, caching or a new read model. Pagination and CSV limits bound response size, not total scanned data.
