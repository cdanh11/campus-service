# Phase 4 — Campus Operations Closure Review

Status: PASS on 2026-10-04 for 4C and the approved Phase 4 backend scope. No unresolved blocker/major remains in this review. User PR/merge is still pending.

Branch `feature/campus-operations-hardening` builds on `95e4efe`, reviewed Phase 4B2. Phase 1–3 are merged at `origin/main` `e64cfc7`; Phase 4 is delivered on dependent feature branches. PR/merge remains the user's responsibility. This review certifies the approved backend scope, not production deployment or frontend completion.

## Requirement and evidence matrix

| Scope | Evidence reviewed |
| --- | --- |
| 4A1 inventory | InventoryItemTest; DormitoryInventoryIntegrationTest: normalized Unicode boundaries, scoped uniqueness, immutable parents, ACTIVE ancestors, child-first deactivation, stale/duplicate rollback, competing updates and parent/child races, bounded production lock timeout with 55P03. |
| 4A2 current place | AccommodationAssignmentTest/IntegrationTest: one ASSIGNED per Student/bed including cross-building competition, retained terminal release, new stay UUID, inactive references, occupied-bed refusal, release/admission race and audit rollback. |
| 4B1 obligations | FinanceObligationTest/IntegrationTest: exact positive integer VND bounds, rejected fractions/overflow, locked fee snapshots, immutable charge fields, retained history after reference deactivation, version/cancellation and fee snapshot races. |
| 4B2 receipts | ManualPaymentTest/IntegrationTest: partial/full bounded payments, globally unique retained receipt numbers, full terminal reversal/reason, zero-effective-paid cancellation, single-query balance/version, concurrent payment/reversal/cancel, cached entity refresh and 55P03 lock timeout followed by success. |
| Security/API/errors | Anonymous and USER rejection for every Dormitory/Finance operation (16 Dormitory and 13 Finance routes); ADMIN success, trusted JWT actor, malformed/invalid/query/stale/reference/duplicate cases; generated OpenAPI Bearer security and distinct request/page schemas. Controllers cannot choose arbitrary tables/entity classes. |
| Atomic audit | Existing HTTP trigger-failure tests reject actual PostgreSQL audit writes and compare complete business rows, versions, occupancy/balances and event counts. Metadata is status-only, actor is server principal, reads and rejected mutations add no success events. No invented JSONB length cap. |
| Query bounds/plans | Database predicates/count/page, size 1–100, safe offset, allowlisted sort plus UUID tie-breaker, literal wildcard escaping. OperationsQueryPlanIntegrationTest uses synthetic 10,000 rooms/beds/charges, 10,100 assignments and 50,000 receipts, ANALYZE and unforced EXPLAIN. Tests require applicable existing parent/current-place/Student-status/charge-status indexes and compare the production balance projection. |
| Exact upgrades | V18→V19, V19→V20, V20→V21 and V21→V22 tests migrate the baseline first, capture legacy tables/history, apply exactly the new version, inspect approved columns/defaults/PK/FK/CHECK/indexes and SQLSTATE writes. Minimal Hibernate validate contexts point to the exact upgraded public schema with Flyway disabled: 20/21/24/25 production entities respectively. Historical entity scans remain fixed. |
| Phase 1–3 regression | Full clean verification reruns Identity/auth/token/admin/audit, organization/people registries, Academic catalog/delivery/enrollment/audit, existing upgrade validation and ModuleBoundaryTest. Source review checks the common ADMIN security matcher and transaction/reference boundaries. |
| Ownership/migrations | Dormitory and Finance use Student application contracts, own their repositories/tables/audit, and do not import foreign infrastructure. ModuleBoundaryTest guards Java references; production query source is also inspected manually. V1–V22 are unchanged from 95e4efe; no migration is needed for 4C. |

## Findings resolved in 4C

1. Application test contexts outlived JUnit-owned PostgreSQL containers. Replaced static JUnit container fields on 20 application integration classes with a test-only Spring-managed container bean plus AFTER_CLASS context cleanup. Each class retains an isolated database; dependent EntityManagerFactory/Hikari beans close with the context. Historical upgrade containers and minimal validation contexts retain their explicit lifecycles. No skip or enlarged Surefire timeout hides teardown.
2. Inventory POST/PUT responses used Java nanosecond timestamps before PostgreSQL rounded to microseconds. The strict POST/PUT versus GET regression failed for all three inventory kinds on the original adapter. Flush then refresh returns actual persisted fields/version/times; tests also preserve createdAt through PUT. No schema/public contract change.
3. First query-plan test over-specified the status index. PostgreSQL selected the equally applicable parent-prefixed unique code index. Assertion now accepts either existing parent-prefixed index, without planner overrides or schema changes.

## Execution record

- Lifecycle cross-class command: `.\mvnw.cmd "-Dtest=ManualPaymentIntegrationTest,AccommodationAssignmentIntegrationTest,HealthEndpointIntegrationTest,AdminFacultyStaffControllerIntegrationTest" test`: BUILD SUCCESS, 30 tests, no failures/errors/skips, 1m31s. Pools close per class without closed-connection validation or fork-kill warnings. Initial testCompile import error was corrected before this successful run.
- First query/upgrade focused gate: BUILD FAILURE, 20 tests, one over-specific index assertion, 1m28s; corrected as described above.
- Inventory regression before fix: BUILD FAILURE, three timestamp comparison failures, 49.640s; a real production response consistency defect, not an execution timeout.
- Final focused command: `.\mvnw.cmd "-Dtest=DormitoryInventoryIntegrationTest,OperationsQueryPlanIntegrationTest,FlywayV18ToV19DormitoryUpgradeIntegrationTest,FlywayV19ToV20AssignmentUpgradeIntegrationTest,FlywayV20ToV21FinanceUpgradeIntegrationTest,FlywayV21ToV22PaymentUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 44 tests, no failures/errors/skips, 2m15s, finished 2026-10-04T03:44:20+07:00. Includes the final production timestamp fix.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 304 tests in 50 suites, zero failures/errors/skips, 6m59s, finished 2026-10-04T03:52:31+07:00; Spring Boot jar packaged. Surefire XML independently sums to the same totals. All 32 Hikari pools started and shut down completely; no closed-connection validation, fork-kill or terminated-VM warning. All Phase 1–4 regression and exact upgrade tests ran without skips.

Source/diff review covers application transaction and refresh-lock order, adapter queries, immutable field mappings, safe errors, security matcher and trusted actors, synchronous audit rollback, migrations and changed test lifecycle. `git diff --check` passes; V1–V22 unchanged from 95e4efe. Historical 4A1/4A2/4B1/4B2 PASS evidence is retained in the slice reviews. The new query suite adds three cases; 304 total includes the 208-case Phase 3 baseline and 96 Phase 4 cases. This closes all five approved Phase 4 gates; deployment is not required for the local project.

## Practical limits

This is a scoped source/test/diff review, not measured exhaustive coverage. Aggregate balances and active-parent/capacity eligibility are supported application transaction invariants; direct SQL is not an audited interface. Student status is checked at the operation decision through its application contract, not locked across modules. Inventory mutations serialize conservatively at the building; leading-wildcard search may scan. EXPLAIN on synthetic fixtures does not certify latency or load. No gateway, automatic tuition/accommodation billing, future booking, self-service, frontend, production secrets/provisioning or deployment is included. Phase 5 needs its own approved use cases before coding.
