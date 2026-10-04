# Phase 5D Audit Viewing Review

Status: PASS on 2026-10-04. No unresolved blocker/major within approved 5D scope. Base 4779b50, branch feature/supporting-services. [API](../api/audit-viewing.md), [ADR 0014](../decisions/0014-owner-scoped-audit-viewing.md). Whole Phase 5 closure is recorded separately in phase-5-final-review.md.

| Requirement | Verified evidence |
| --- | --- |
| Eight owners | Eight owner application query contracts/adapters; shared bounded DTO/filter policy; aggregator routes contracts without SQL/foreign persistence imports. Actual table names/columns/metadata/version shapes inspected. No new entities or migrations; V1–V25 unchanged. |
| ADMIN/security | Both GET operations across all eight sources: anonymous 401, USER 403, ADMIN success, OpenAPI Bearer and AuditViewingPage. No mutation endpoint. Safe source/id errors and foreign-source event UUID 404. |
| Recorded history | Exact source/resource/target/actor/action/version/time, source-specific filters, real Library create→audit→read pipeline. Identity/People null version and {} metadata rather than reconstructed values. |
| Metadata | Approved status per resource only. Legacy malformed/plain/nested VARCHAR does not break pages; JSONB nested/null/unknown status and large private body are discarded. Safe string key projected inside PostgreSQL, no raw object hydration or invented JSONB limit; rows remain unchanged. |
| Bounds/pagination | 3 contract unit cases and 7 API/database cases: offset/page/size, action token/actual length, resource policy, UUID/instant/range/sort; [from,until) boundaries, both directions and real equal-time UUID pages/counts on every source. |
| Snapshot concurrency | Separate reader/writer transactions, latch pauses after actual count SELECT, writer commits another event, page still sees original snapshot; next request sees both. Verified all eight production ports with bounded latches/futures/finally cleanup, no sleep. |
| Index evidence | One selective-plan case seeds 10,000 events in each of eight owner tables, ANALYZE/unforced EXPLAIN verifies existing target-prefixed indexes and count/page agreement with actual production projections; no unnecessary migration or global-page performance claim. |
| Read-only/review | Complete audit rows and Flyway history unchanged after every port/list/get; source review finds only SELECT and own tables; ModuleBoundaryTest passes. git diff --check and final source/schema/API/docs review completed. |

## Executed checks

- First focused compile: BUILD FAILURE, 13.876s (2026-10-04T16:59:39+07:00), test generic inferred HashSet rather than Set. Fixed explicit Map<String,Set<String>>; no production defect or tool timeout.
- Focused retry: BUILD SUCCESS, 10 tests, 0 failures/errors/skips, 42.521s (2026-10-04T17:01:22+07:00).
- Query/exact V25 focused: BUILD SUCCESS, 16 tests, 0 failures/errors/skips, 1m06s (2026-10-04T17:03:38+07:00), before snapshot case.
- Final `.\mvnw.cmd "-Dtest=AuditViewingIntegrationTest,AuditViewingQueryPlanIntegrationTest,AuditQueryContractTest,ModuleBoundaryTest,FlywayV24ToV25LibraryUpgradeIntegrationTest" test`: BUILD SUCCESS, 17 tests, 0 failures/errors/skips, 1m10s, finished 2026-10-04T17:06:05+07:00. Includes snapshot case and exact V25/36-entity validation with Flyway disabled.
- Final full `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, **393 tests/67 suites**, 0 failures/errors/skips, **7m36s**, finished **2026-10-04T17:14:15+07:00**. Surefire XML independently summed agrees; jar packaged; all 44 pools close cleanly, no fork termination or closed-container warning. Includes all Phase 1–5 regression and exact historical/new migration validations. Audit viewing adds 11 tests across three suites (7 API/database, 3 contract, 1 selective-plan).

Scope is retained ADMIN reads for local project. No frontend/deployment/production provisioning, export/expiry, cross-source timeline, load benchmark, exhaustive-coverage percentage or invented historic version. Phase 5E requires a separate requirement-by-requirement closure audit after 5D PASS.
