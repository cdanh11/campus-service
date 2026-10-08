# Testing Strategy

## Current completion gates

Phase 8A2 functional permission checks and exact V25→V26 upgrade evidence are recorded in the [permissions review](reviews/phase-8a2-permissions-review.md). Whole Phase 8 is incomplete. Latest backend clean verification on 2026-10-05: BUILD SUCCESS, 440 tests / 78 suites, zero failures/errors/skips, 25m27s, finished 22:56:53 +07; independently checked against Surefire XML. Includes Phase 1–6 regression, administrator bootstrap and functional permission/V25→V26 upgrade coverage. Frontend and demo acceptance gates remain unresolved, so this is not whole Phase 8 PASS. Older totals below retain their historical checkpoints.

Frontend Phase 7 is complete in campus-client. The remaining [Phases 8–9](plans/phase-8-9-local-demo.md) require a traceable API/UI acceptance matrix, real-backend regression and demo rehearsal. Startup/health checks alone do not establish business acceptance. Keep mocked, real-browser and full Maven results distinct; retain actual test counts only after the corresponding command finishes. Historical evidence below remains tied to its checkpoint.

[8A3 Docker/native demo review](reviews/phase-8a3-demo-review.md) records the verified substantial inventory, zero-owner-write repeats, clean-source image builds, 10 loader safety tests, real audited recovery, exact persistence and representative Chromium proxy/mobile checks. This adds setup/data evidence; it does not replace 8B acceptance or the latest 8C clean regression. No new Maven total is claimed for Docker's deliberately test-skipped packaging build.

## Phase 7 OpenAPI contract prerequisite

Full `./mvnw.cmd clean verify` on 2026-10-04: **BUILD SUCCESS, 417 tests/75 suites, zero failures/errors/skips, 10m35s**, finished 23:12:25+07:00. XML totals independently counted; Boot jar packaged and 49 pools close normally. The new production-document regression checks 104 DTO schema references against owner record signatures, including nested collections and generic pages. Base springdoc.use-fqn corrects duplicate nested DTO names; V1–V25, JSON fields and authorization remain unchanged. See [contract review](reviews/phase-7-api-contract-review.md). Earlier totals are historical checkpoints.

## Phase 6 verified closure

Final `./mvnw.cmd clean verify` on 2026-10-04: **BUILD SUCCESS, 416 tests/74 suites, zero failures/errors/skips, 10m12s**, finished 21:07:26+07:00. XML totals independently match; packaged Boot jar and 48 clean pool shutdowns verified. Includes regression Phase 1–5 and 23 new tests covering owner dashboards/reports, ADMIN/OpenAPI, exact VND, lifecycle/date/page/filter bounds, CSV Unicode/formula/5000-row limits, count/page/export snapshot races, safe correlation/logging/metrics and 10,000-row/family query/workload evidence. No new schema/entity; exact historical upgrades through V25 remain validated with Flyway disabled in their minimal validation contexts. See [Phase 6 final review](reviews/phase-6-final-review.md) and [measured workload limits](runbooks/reporting-observability.md). Counts later in this file refer to historical phase checkpoints.

## Principles

Tests provide evidence that behavior, security boundaries, and schema changes work as intended. Campus Service will favor fast tests close to the business rule, with fewer broader tests for framework and infrastructure behavior. No coverage percentage is claimed or required before a measurement tool and threshold are deliberately adopted.

## Testing Pyramid

```mermaid
flowchart BT
    Unit[Unit tests: business rules and use cases]
    Slice[Slice tests: web, persistence, security adapters]
    Integration[Integration tests: application with PostgreSQL]
    E2E[End-to-end tests: critical user journeys]
    Unit --> Slice --> Integration --> E2E
```

## Test Responsibilities

- **Unit tests:** validate domain rules and application use cases without Spring or a database where practical.
- **Slice tests:** validate focused framework integrations, such as MVC request handling, validation, JPA mapping, or security configuration.
- **Integration tests:** validate modules working with real infrastructure behavior, especially PostgreSQL, migrations, persistence, and authorization boundaries.
- **End-to-end tests:** cover a small set of critical externally visible journeys once the application and stable API exist; they are not a Phase 0 deliverable.

## Current Tooling

Phase 1, the Phase 2 registries and Phase 3 Academic foundations use JUnit 5, Spring Boot Test, MockMvc, Testcontainers PostgreSQL, and Flyway. The verified Windows command is `./mvnw.cmd clean verify` when run from PowerShell as `.\mvnw.cmd clean verify`.

The Phase 3 baseline of 208 tests verified by `./mvnw.cmd clean verify` on 2026-10-04 cover health and readiness probes, production-profile configuration, Flyway migrations through V18 including V4-to-V5, V8-to-V10, V10-to-V11, V11-to-V13, V13-to-V16, V16-to-V17 and V17-to-V18 upgrades, authentication and authorization boundaries, administrator user-management flows, multi-session HTTP revocation, audit-failure rollback, the organization, student, and faculty/staff registries, Program/Course catalog validation, authorization, concurrent updates and paginated queries, term/offering/section lifecycle and faculty-assignment rules, administrative enrollment with capacity, withdrawal/re-enrollment and competing transaction protection, transactional Academic mutation audit, OpenAPI security, selective PostgreSQL query plans and cross-module Java dependency guards. Integration tests run against PostgreSQL Testcontainers and do not connect to a developer's local database.

## Authentication and Authorization

Identity and Access tests must cover successful and failed authentication, invalid or expired token behavior once token handling exists, anonymous access restrictions, and role-based allow/deny cases for protected capabilities. Tests must assert that unauthorized requests are rejected, not merely that authorized requests succeed.

## Database Migration Verification

Every persistence change must include migration verification. Integration tests should start a clean PostgreSQL instance through Testcontainers, apply Flyway migrations, and exercise affected persistence behavior. A migration failure is a release blocker.

`FlywayV4ToV5UpgradeIntegrationTest` first migrates to V4, inserts legacy users and role assignments, and then applies only V5. Its six tests verify explicit space/tab/newline/carriage-return/vertical-tab/form-feed normalization, short/blank fallbacks, truncation and Unicode boundaries, preservation of identity fields and roles, display-name constraints, row-version defaults and nullability, guard seed/constraints and the production lock query across commit and rollback, and audit columns/defaults/foreign keys/index and valid/invalid inserts. A minimal JPA context validates all production entities against that same upgraded schema with Flyway disabled and confirms migration history is unchanged.

Verified on 2026-09-24:

- `.\mvnw.cmd "-Dtest=FlywayV4ToV5UpgradeIntegrationTest,IdentityPersistenceIntegrationTest" test`: BUILD SUCCESS; 15 tests, no failures/errors/skips; 36.025 seconds.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS; 70 tests, no failures/errors/skips; 1 minute 43 seconds.

Verified for Phase 2D on 2026-09-24:

- `.\mvnw.cmd "-Dtest=FlywayV8ToV10PeopleRegistryUpgradeIntegrationTest" test`: BUILD SUCCESS; 2 tests, no failures/errors/skips; 21.507 seconds.
- `.\mvnw.cmd "-Dtest=PeopleRegistrySearchIntegrationTest,PeopleRegistryAuditIntegrationTest,FlywayV10ToV11PeopleAuditUpgradeIntegrationTest" test`: BUILD SUCCESS; 5 tests, no failures/errors/skips.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS; 90 tests, no failures/errors/skips; 3 minutes 52 seconds.

`FlywayV8ToV10PeopleRegistryUpgradeIntegrationTest` migrates a disposable PostgreSQL schema to V8, inserts legacy registry and identity records, then applies V9 and V10. It verifies preserved records, case-insensitive identifier uniqueness, nullable optional Identity links, foreign keys and unique link indexes, and a Hibernate `ddl-auto=validate` context that uses the upgraded schema with Flyway disabled.

`FlywayV10ToV11PeopleAuditUpgradeIntegrationTest` migrates a disposable schema to V10 and then applies only V11. It verifies the approved audit columns, types, nullability, default, constraints, foreign key, index, valid and invalid writes, and Hibernate `ddl-auto=validate` with Flyway disabled against the exact upgraded schema. `PeopleRegistryAuditIntegrationTest` verifies audited Student and Faculty/Staff create/update operations and transactional rollback when audit persistence fails.

These successful runs required Docker named-pipe access outside the agent sandbox; the initial sandboxed focused run failed during Docker discovery before migration assertions ran.

## Phase 3A Verification

Verified on 2026-10-03 with Docker Desktop running:

- `.\mvnw.cmd "-Dtest=AcademicCatalogTest,AdminAcademicCatalogControllerIntegrationTest,FlywayV11ToV13AcademicCatalogUpgradeIntegrationTest" test`: BUILD SUCCESS; 31 tests, no failures/errors/skips; 3 minutes 46 seconds.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS; 121 tests, no failures/errors/skips; 5 minutes 29 seconds. This run covers the final source, including SQLSTATE classification and the JPA pagination offset bound added during review.

The 31 Academic tests comprise 9 domain cases, 16 API cases and 6 upgrade/schema cases. They cover six-character boundary whitespace and Unicode length, credit boundaries, ADMIN-only operations, create/read/PUT, stale and duplicate update rollback, two competing updates (one succeeds, one returns 409), missing/inactive ownership, error contracts, database pagination/search/status/sort and invalid query parameters.

`FlywayV11ToV13AcademicCatalogUpgradeIntegrationTest` inserts representative V11 identity/role/organization/student/personnel/audit fixtures, captures every field and original migration history, then applies exactly V12/V13. It verifies column types, approved lengths, nullability, defaults, primary/unique/foreign keys, indexes, valid boundary writes and SQLSTATE rejection for invalid writes. A minimal JPA context scans production entities present at V13 and runs `ddl-auto=validate` against that same upgraded public schema with Flyway disabled. Migration history and existing records remain unchanged. V1–V11 were not edited.

Final review covers error/transaction/version behavior, bounded database queries, module imports, migrations, tests and staged whitespace checks. Academic mutation audit, terms, sections and enrollment remain outside Phase 3A.

## Phase 3B Verification

Verified on 2026-10-03:

- `.\mvnw.cmd "-Dtest=AcademicDeliveryTest,AcademicDeliveryIntegrationTest,FlywayV13ToV16AcademicDeliveryUpgradeIntegrationTest,FlywayV11ToV13AcademicCatalogUpgradeIntegrationTest" test`: BUILD SUCCESS; 39 tests, no failures/errors/skips; 1 minute 04 seconds.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS; 154 tests, no failures/errors/skips; 3 minutes 53 seconds.

Phase 3B adds 33 tests: 8 domain cases, 17 API cases and 8 V13→V16 upgrade/schema cases. Coverage includes term date/lifecycle boundaries, optional faculty in DRAFT and mandatory active FACULTY in OPEN, STAFF/inactive/missing/cross-organization faculty, live organization and catalog availability, historical closure after deactivation, parent closure versus child opening across concurrent HTTP transactions using latches, cancellation, ADMIN authorization, stale/duplicate mutation rollback, bounded query filters/sort and positive section capacity.

The genuine upgrade inserts representative identity/role/organization/student/personnel/catalog/audit fixtures at V13, captures complete records/history, applies exactly V14/V15/V16, and validates types/lengths/nullability/defaults/PK/FK/uniqueness/indexes/approved CHECK constraints through SQLSTATE assertions and boundary writes. A minimal JPA context validates all 14 currently implemented production entities against that exact upgraded public schema with Flyway disabled, and verifies unchanged migration history and legacy records. V1–V13 remain unchanged. The V11→V13 test freezes its entity scan at V13 packages so later delivery entities cannot silently change its validation target.

Review PASS covers the domain/API contract, reference ownership, lock order (term → offering → section), error advice scope, database query bounds and import boundaries. Search and open-child checks use parent/status indexes; no load-test or benchmark result is claimed. Enrollment and broader Academic mutation audit remain Phase 3C/3D work.

## Phase 3C Verification

Verified on 2026-10-04:

- `.\mvnw.cmd "-Dtest=EnrollmentTest,EnrollmentIntegrationTest,AcademicDeliveryIntegrationTest,FlywayV16ToV17EnrollmentUpgradeIntegrationTest,FlywayV13ToV16AcademicDeliveryUpgradeIntegrationTest" test`: BUILD SUCCESS; 47 tests, no failures/errors/skips; 1 minute 32 seconds. Subsequent cleanup removes the unused locking JPQL queries; full verification below covers the final source.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS; 176 tests, no failures/errors/skips; 4 minutes 18 seconds.

Phase 3C adds 22 tests: 2 domain, 17 application/HTTP/transaction integration and 3 genuine V16→V17 upgrade/schema cases. Cases cover ADMIN-only routes, invalid request/query bounds, stale and duplicate rollback, immutable membership identity, available capacity, historical withdrawal, re-enrollment eligibility, inactive/missing Students, draft/closed sections, filters and actual timestamp-tie pagination. Separate transactions race for the last seat, duplicate membership, stale withdrawal, restored membership versus a new student, and section closure versus admission. A bounded PostgreSQL lock_timeout proves the production section lock blocks another transaction with SQLSTATE 55P03 and that acquisition succeeds after release.

The first full run exposed a cached-entity version conflict before refresh in the old delivery locking query. Delivery adapters now acquire PESSIMISTIC_WRITE through refresh directly. A deterministic regression caches a section, commits closure on another connection, then asserts admission sees CLOSED and rolls back without creating membership. Existing 3B lifecycle/version/concurrency tests pass on the final source.

The exact upgrade migrates to V16, inserts representative Identity/role/organization/Student/personnel/catalog/term/offering/section/audit records, snapshots every existing table and migration history, then applies only V17. It checks the new enrollment columns/defaults/nullability/PK/unique/FKs/CHECK/indexes, valid lifecycle SQL writes, and rejected writes with specific SQLSTATEs. A minimal context validates all 15 production entities against that same upgraded public schema with Flyway disabled; history and baseline data remain unchanged. The historical V16 context scans only its original 14 production entities. V1–V16 remain unchanged.

Final review PASS requires the evidence matrix in the Phase 3C plan, inspected production transaction/query/module boundaries and clean diff checks. No benchmark, self-service, waitlist, registration window or broader Academic mutation audit is claimed; see ADR 0005 and Phase 3D.

## Phase 3D Verification

Verified on 2026-10-04:

- `.\mvnw.cmd "-Dtest=AdminAcademicCatalogControllerIntegrationTest,AcademicDeliveryIntegrationTest,EnrollmentIntegrationTest" test`: BUILD SUCCESS; 50 tests, no failures/errors/skips; 4 minutes 16 seconds.
- `.\mvnw.cmd "-Dtest=AcademicAuditIntegrationTest,FlywayV17ToV18AcademicAuditUpgradeIntegrationTest,FlywayV16ToV17EnrollmentUpgradeIntegrationTest,ModuleBoundaryTest,PeopleRegistrySearchIntegrationTest" test`: BUILD SUCCESS; 28 tests, no failures/errors/skips; 1 minute 18 seconds. The full run includes the subsequent assertion that columns without approved defaults have none.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS; 200 tests, no failures/errors/skips; 4 minutes 56 seconds.

Phase 3D adds 24 cases: 17 audit/API/query/concurrency cases, 3 V17→V18 upgrade/schema cases, 1 module-boundary guard and 3 Phase 2 pagination boundary cases. Every resource create/update verifies the trusted actor and resulting event type/action/version/status; real PostgreSQL trigger failures prove business rows and versions roll back with audit failure. Enrollment restoration rollback and last-seat competition verify occupancy and event consistency. Unauthorized/malformed/stale/duplicate requests and reads add no success events. Generated OpenAPI declares Bearer security on all 24 Academic operations. Synthetic 10,000-pair fixtures, ANALYZE and unforced PostgreSQL EXPLAIN show the existing section/status and student/status enrollment indexes serve selective queries; this is not a latency/load benchmark.

The actual upgrade first migrates to V17, inserts complete representative legacy records including withdrawn enrollment and People Registry audit, snapshots all existing tables/history, then applies only V18. It verifies all audit columns/types/lengths/nullability/defaults, PK/actor FK, resource/action/version/object-metadata constraints, indexes and specific SQLSTATE rejections. Large Unicode JSON objects are accepted without an invented metadata length cap. A minimal context validates all 16 production entities on the exact upgraded public schema with Flyway disabled; migration history and baseline records remain unchanged. Historical V17 validation keeps its original 15 entity set. V1–V17 are unchanged.

The final review records resolved findings, evidence and boundaries in [Phase 3D Final Review](reviews/phase-3d-final-review.md). Java dependency guards supplement manual SQL/module ownership review; they are not a substitute for architecture or security review. Academic HTTP writes are audited, while trusted internal fixture/provisioning services remain distinct. No audit query endpoint, backfill, DB-enforced append-only policy, production-load certification or deployment is claimed.

## Phase 3 Closure Re-review

Closure re-review on 2026-10-04 added eight cases and strengthened existing audit/lifecycle assertions. Focused audit/V18/boundary verification passed 29 tests in 1m14s; subsequent delivery assertions passed in full `.\mvnw.cmd clean verify`: BUILD SUCCESS, 208 tests, zero failures/errors/skips, 5m01s. Surefire totals: 37 suites, 114 Academic cases, 66 Identity cases and 28 platform/registry cases. See the final review's closure section for specific evidence gaps and non-blocking follow-ups; these totals do not imply measured code coverage.

## Phase 4A1 Dormitory Inventory Verification

Verified on 2026-10-04:

- `.\mvnw.cmd "-Dtest=InventoryItemTest,DormitoryInventoryIntegrationTest,FlywayV18ToV19DormitoryUpgradeIntegrationTest,FlywayV17ToV18AcademicAuditUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 40 cases, no failures/errors/skips, 1m30s. Subsequent explicit OpenAPI names/field assertions are covered by the full run.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS, 244 tests in 40 suites, no failures/errors/skips, 5m28s; packaged jar. Surefire XML independently agrees.

Dormitory adds 36 cases: 6 domain, 24 API/transaction/lock/concurrency and 6 genuine V18→V19 upgrade/schema cases. Coverage includes all operation role denials, Unicode/whitespace/length and scoped uniqueness, immutable parents, bounded literal queries and actual timestamp-tie pagination, stale/duplicate rollback, real database audit failure for all inventory create/update routes, update and parent/child races, bounded production parent locks (55P03 and success after release), exact schema types/defaults/constraints/indexes and SQLSTATE rejection, and status-only audit/OpenAPI contracts.

The actual upgraded PostgreSQL/public schema validates all 20 production entities using ddl-auto=validate with Flyway disabled, and checks baseline records/history unchanged. Historical V18 validation retains its original 16 entities. V1–V18 remain unchanged. First upgrade verification exposed a bad legacy fixture missing required Identity status; it was corrected. A later Docker HTTP 503 discovery failure was environmental; final successful runs occurred after daemon recovery. See [4A1 final review](reviews/phase-4a1-final-review.md).

Allocation/occupancy and Finance are not part of this slice. No load benchmark, measured coverage percentage or frontend E2E result is claimed.

## Phase 4A2 Accommodation Verification

Verified on 2026-10-04 with Docker Desktop running:

- `.\mvnw.cmd "-Dtest=AccommodationAssignmentTest,AccommodationAssignmentIntegrationTest,FlywayV19ToV20AssignmentUpgradeIntegrationTest,DormitoryInventoryIntegrationTest,FlywayV18ToV19DormitoryUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 47 tests, no failures/errors/skips, 1m34s. Later strict POST/PUT-to-GET response equality assertions are covered by the full run.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 260 tests in 43 suites, no failures/errors/skips, 6m29s; packaged jar and independently summed Surefire XML agree.

The 16 new cases include 2 domain, 11 API/transaction/lock/concurrency and 3 genuine V19→V20 upgrade/schema cases. They verify immutable history and terminal release, later stay with a new UUID, active eligibility and occupied-bed deactivation, all route authorization, query bounds/stable ties, real PostgreSQL audit-failure rollback, same-bed and same-Student/different-building races, stale releases, release/admission and close/admission consistency, and production locks across separate transactions with bounded lock_timeout and SQLSTATE 55P03 followed by successful acquisition.

The exact upgrade applies only V20 after representative legacy fixtures and all existing table/history snapshots. Assertions cover assignment types/defaults/nullability/PK/FKs/CHECKs/partial unique indexes, valid history and rejected SQL writes with specific SQLSTATEs, and audit resource/action compatibility. Hibernate validates all 21 production entities on that same upgraded public schema with Flyway disabled; legacy data/history remain unchanged. Historical V19 validation retains 20 entities; V1–V19 are unchanged.

The first focused run failed one timestamp-preservation assertion because POST used nanoseconds while PostgreSQL stored microseconds. Persistence now flushes/refreshes before returning, and strict comparisons pass. The final full run logged closed-container Hikari warnings and a slow test-JVM exit: Surefire terminated the fork after its 30-second exit timeout, after complete successful test results. Maven returned BUILD SUCCESS and packaged the jar. Track this suite lifecycle observation in Phase 4C; no load benchmark or measured coverage percentage is claimed. See [4A2 final review](reviews/phase-4a2-final-review.md).

## Phase 4B1 Finance Obligation Verification

Verified on 2026-10-04 with Docker Desktop running:

- `.\mvnw.cmd "-Dtest=FinanceObligationTest,FinanceObligationIntegrationTest,FlywayV20ToV21FinanceUpgradeIntegrationTest,FlywayV19ToV20AssignmentUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 23 cases, no failures/errors/skips, 1m17s.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 280 tests in 46 suites, no failures/errors/skips, 6m37s; packaged jar and independently summed Surefire XML agree. Includes subsequent cached-fee and maximum-charge-snapshot assertions.

Finance adds 20 cases: 4 domain, 12 API/transaction/lock/concurrency and 4 genuine V20→V21 upgrade/schema cases. They cover exact VND integer boundaries and fractional/overflow rejection, six-character whitespace/Unicode/uppercase expansion, immutable financial snapshots after fee/reference changes, terminal cancellation, all eight operation authorization denials, trusted audit actor, complete-row rollback for all four mutations when PostgreSQL audit insert fails, duplicate/stale/reference/query failures, bounded filters/allowlisted sorts/literal matching/actual due-date ties, competing updates/cancellations/duplicate charges, fee closure versus admission, cached fee refresh after another transaction commits deactivation, and bounded production fee lock (55P03 then acquisition after release).

The actual upgrade migrates to V20 with representative legacy records including released accommodation and audit, snapshots all old tables/history, and applies only V21. New fee/charge/audit columns/types/approved lengths/defaults/nullability/PK/FKs/uniqueness/CHECKs/indexes are verified with boundary and specific SQLSTATE writes, including tiny fractions, NaN/infinity and large Unicode JSON without an invented length cap. Hibernate validates all 24 production entities on the exact upgraded public schema with Flyway disabled; history/legacy tables stay unchanged. Historical V20 retains 21 entities. V1–V20 are unchanged.

The full run again logged closed-container Hikari warnings and Surefire's forced fork termination after the 30-second exit timeout, after all tests completed successfully. Maven exited 0 and packaged the jar. This teardown issue remains a 4C follow-up; no benchmark, coverage percentage or payment behavior is claimed. See [4B1 final review](reviews/phase-4b1-final-review.md).

## Phase 4B2 Manual Payment Verification

Verified on 2026-10-04 with Docker Desktop running:

- `.\mvnw.cmd "-Dtest=ManualPaymentTest,ManualPaymentIntegrationTest,FinanceObligationIntegrationTest,FlywayV21ToV22PaymentUpgradeIntegrationTest,FlywayV20ToV21FinanceUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 38 tests, no failures/errors/skips, 1m45s.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 301 tests in 49 suites, no failures/errors/skips, 7m05s; jar packaged and Surefire XML summed independently. Includes subsequent domain/HTTP cancelled-charge zero-outstanding assertions.

The 21 new cases comprise 3 domain, 15 API/transaction/lock/concurrency and 3 genuine V21→V22 upgrade/schema cases. They cover exact VND maximum/minimum/fraction/overflow behavior, partial/full settlement and terminal full reversal with immutable history/reason, retained receipt uniqueness, zero-paid cancellation, all five new operation role denials, trusted audit actor, malformed/query/body/reason/version bounds, existing inactive reference settlement, complete payment/charge/version/balance/audit rollback, final-balance competition, cross-charge duplicate receipts, competing reversals, cancel/payment and reversal/new payment consistency, cached charge/receipt refresh, coincident-clock version advancement and production charge lock with bounded SQLSTATE 55P03 followed by success. Queries cover filters/sorts/literal matching and actual timestamp-tie pagination; OpenAPI declares Bearer and unique schemas.

The upgrade inserts representative V21 Identity/registries/Academic/Dormitory/Finance/audit records and snapshots all old tables/history, then applies only V22. It verifies payment columns/types/lengths/defaults/nullability/PK/charge FK/unique/indexes, integer/range/currency/status/version/reversal-shape CHECKs and specific SQLSTATE rejection, plus audit policy compatibility. Hibernate validates all 25 production entities on the same upgraded public schema with Flyway disabled; old data/history remain unchanged. Historical V21 validation retains 24 entities. V1–V21 unchanged.

An initial testCompile failure came from JSON escaping in the upgrade fixture; jsonb_build_object fixed it. The full suite again had closed-container Hikari warnings and slow JVM shutdown; Surefire terminated its fork after the 30-second exit timeout after complete successful test results. Maven exited 0 and packaged the jar. Teardown remains Phase 4C work. Aggregate overpayment/cancellation protection belongs to supported application transactions, not direct SQL; no gateway/refund-transfer, load benchmark or measured coverage claim. See [4B2 final review](reviews/phase-4b2-final-review.md).

## Phase 4C and Phase 4 Closure Verification

Verified on 2026-10-04 after resolving application-context/container teardown and inventory timestamp response consistency:

- `.\mvnw.cmd "-Dtest=ManualPaymentIntegrationTest,AccommodationAssignmentIntegrationTest,HealthEndpointIntegrationTest,AdminFacultyStaffControllerIntegrationTest" test`: BUILD SUCCESS; 30 tests, zero failures/errors/skips; 1m31s.
- `.\mvnw.cmd "-Dtest=DormitoryInventoryIntegrationTest,OperationsQueryPlanIntegrationTest,FlywayV18ToV19DormitoryUpgradeIntegrationTest,FlywayV19ToV20AssignmentUpgradeIntegrationTest,FlywayV20ToV21FinanceUpgradeIntegrationTest,FlywayV21ToV22PaymentUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS; 44 tests, zero failures/errors/skips; 2m15s.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0; 304 tests in 50 suites, zero failures/errors/skips; 6m59s, finished 2026-10-04T03:52:31+07:00. Jar packaged; independently summed Surefire XML agrees. All 32 Hikari pools started and shut down completely; no closed-connection validation, fork-kill or terminated-VM warning.

Application integration classes now import a test-only Spring-managed PostgreSQL container through `@PostgresApplicationTest`, with AFTER_CLASS context cleanup and one isolated database per class. This preserves actual PostgreSQL/Flyway and all existing assertions, without skips or inflated fork timeouts. Historical upgrade tests retain their own container/minimal-context lifecycles. The inventory regression first failed all three resource kinds, then passed with flush/refresh returning the persisted precision; POST/PUT equals subsequent GET and createdAt is retained.

Three Operations query-plan cases use 10,000 rooms/beds/charges, 10,100 assignments and 50,000 receipts, ANALYZE and unforced EXPLAIN. They verify existing selective parent/current-place/Student-status/fee-status/charge-status indexes, plus the production balance projection. Parent inventory may legitimately choose a parent-prefixed unique code index instead of its parent/status index. No new index, measured coverage percentage or production load/latency claim. Full verification covers all Phase 1–4 behavior/security/audit/concurrency and genuine historical upgrades; exact V22 validates 25 entities with Flyway disabled. V1–V22 unchanged in 4C. [Closure review](reviews/phase-4-final-review.md) records the evidence matrix, corrected failures and limits. Historical earlier slice results above remain records of their execution, including the teardown observation now resolved.

## Naming Convention

Use behavior-oriented names that state the condition and expected result, such as `createsUserWhenRequestIsValid` or `deniesEnrollmentReadWhenCallerLacksRole`. Follow the project test style once it is established rather than introducing competing conventions.

## Acceptable Evidence

Evidence includes the exact command run, its verified result, the test scope, and any known gaps. If tests cannot run because the Maven project or required infrastructure does not yet exist, state that plainly. Never fabricate execution results, test counts, coverage, or environment status.

## Phase 5A Notification Verification

Verified on 2026-10-04 with Docker running:

- `.\mvnw.cmd "-Dtest=NotificationIntegrationTest,NotificationDomainTest,FlywayV22ToV23NotificationUpgradeIntegrationTest,FlywayV21ToV22PaymentUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 23 tests, zero failures/errors/skips, 1m05s.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 323 tests in 53 suites, zero failures/errors/skips, 5m58s, finished 2026-10-04T15:03:14+07:00. Independently summed Surefire XML agrees; jar packaged; all 34 Hikari pools closed without fork termination or closed-container warnings.

The 19 new cases cover four domain tests, eleven API/transaction/security/concurrency cases and four genuine V22→V23 upgrade cases. Evidence includes authorization across all twelve operations, own/foreign inbox isolation, immutable snapshots, Unicode/six-character trimming, recipient batch validation and complete rollback for every mutation, trusted actors, stale updates, idempotent/concurrent reads, duplicate publication, cached Identity deactivation and a real production lock timeout (55P03) followed by success. Queries include literal matching, every allowlisted sort and actual UUID-tied inbox pages; generated OpenAPI schemas are checked.

The upgrade preserves all representative legacy tables/history and applies only V23, verifying actual four-table columns/defaults/constraints/indexes through specific SQLSTATE writes, including large JSONB without an invented limit. Hibernate validates all 29 production entities against the exact upgraded public schema with Flyway disabled; history remains unchanged. Historical V22 retains 25 entities. V1–V22 are unchanged. No load benchmark or coverage percentage is claimed; Event, Library, audit viewing and whole Phase 5 remain incomplete. See [5A review](reviews/phase-5a-notification-review.md).
## Phase 5B Catalog Checkpoint Verification — incomplete slice

Verified on 2026-10-04; this is working-tree progress, not Event 5B PASS:

- `.\mvnw.cmd "-Dtest=CampusEventTest,EventCatalogIntegrationTest,StudentAccountDirectoryIntegrationTest,FlywayV22ToV23NotificationUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 18 tests, zero failures/errors/skips, 1m06s, finished 15:25:05+07:00.
- `.\mvnw.cmd "-Dtest=FlywayV23ToV24EventUpgradeIntegrationTest" test`: BUILD SUCCESS, three tests, zero failures/errors/skips, 26.617s, finished 15:27:08+07:00.
- `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 339 tests in 57 suites, zero failures/errors/skips, 6m20s, finished 2026-10-04T15:34:04+07:00. Surefire XML independently summed; jar packaged; all 37 Hikari pools closed without fork termination or closed-container warnings.

New evidence covers catalog normalization/date/lifecycle/capacity/query bounds, all six operation authorization, stored precision and trusted actor, strict JSON capacity rejection, full audit rollback, duplicate/stale/concurrent edits, cached-state refresh, production lock timeout 55P03 followed by success, literal query matching/allowlisted sorts/actual tied UUID pages and generated Bearer schemas. Student lookup sees independently committed status/unlink despite cached profile. Genuine V23→V24 preserves all representative prior data/history, verifies actual catalog/audit schema and SQLSTATE boundaries, and validates 31 entities on the exact upgraded public schema with Flyway disabled. Historical V23 retains 29; delivered V1–V23 unchanged.

Initial catalog run failed because fractional capacity JSON was coerced to integer and a stale-update test transaction lacked rollback cleanup; both corrected before successful focused/full runs. Registration/attendance, owner admission/cancellation/restoration, real consumed-seat count and races are still missing. V24 is unreleased/uncommitted; schema and tests must expand before Event PASS and before Library implementation. No load benchmark or coverage percentage. See [5B checkpoint review](reviews/phase-5b-event-review.md).
## Phase 5B Final Verification

Verified on 2026-10-04 after both registration decisions were approved:

- Complete focused Event/catalog/domain/schema/Student/Notification-history/boundary command in the [5B review](reviews/phase-5b-event-review.md): BUILD SUCCESS, 40 tests, zero failures/errors/skips, 1m29s. Subsequent final-seat/lifecycle cases increased registration API coverage to 13, all passed; final expanded query-plan case passed in 32.276s.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, **360 tests in 60 suites**, zero failures/errors/skips, **6m29s**, finished **2026-10-04T16:10:34+07:00**. Jar packaged; Surefire XML independently summed; all 39 pools closed without fork termination or closed-container warnings. Includes every subsequent assertion and Phase 1–5B regression.

Event adds 37 cases: four catalog-domain, five membership-domain, eight catalog API, thirteen registration API, four genuine V23→V24 upgrade, two Student directory and one expanded query-plan case. Coverage includes all fourteen operation authorization, owner/spoof isolation, OPEN-only past-event admission, retained restore/attendance/cancellation, strict JSON integers, all six mutation audit rollbacks, final-seat/duplicate/restore/cancel/attendance/closure/capacity races, cached Event/member/Student changes and actual production locks (55P03 then recovery), bounded filters/literal matches/actual tied pages and generated OpenAPI. Plan evidence uses 10,000 Events/Students/memberships, ANALYZE and unforced EXPLAIN; Event count may validly use retained membership's Event-prefixed unique index instead of the dedicated status index. No new forced setting, benchmark or coverage percentage.

The populated V23 baseline includes every representative prior domain and Notification. Apply only V24, preserve all old tables/history, verify all three actual tables/defaults/constraints/indexes and specific SQLSTATE writes, including temporal/Unicode/large JSONB boundaries. Hibernate validates all 32 entities on that same upgraded public database/schema with Flyway disabled, no create/update or Flyway bean. Historical V23 scan freezes 29; V1–V23 unchanged. Corrected failures (fractional capacity/version coercion, expected-error test cleanup, overly restrictive plan assertion) are recorded in the review. Earlier catalog checkpoint results remain historical, superseded by this final membership-code regression. Library/audit viewing/whole Phase 5 remain incomplete.
## Phase 5C Final Verification

Verified 2026-10-04; [Library review](reviews/phase-5c-library-review.md) records full commands/evidence. Foundation 10 tests (34.019s), API checkpoint 10 tests (44.149s), focused 26 tests (1m10s), all BUILD SUCCESS with zero failures/errors/skips. Final `.\mvnw.cmd clean verify`: **BUILD SUCCESS, exit 0, 382 tests/64 suites, 0 failures/errors/skips, 7m32s**, finished **2026-10-04T16:47:05+07:00**; XML independently summed, jar packaged, all 42 pools closed cleanly. Includes the final added deactivation/admission, return/borrow and copy tie-page assertions.

Library adds 22 cases: 5 domain, 11 API/authorization/rollback/concurrency/cache/lock/query/OpenAPI, 5 populated V24→V25 upgrade and 1 selective-plan case. All 12 HTTP operations enforce ADMIN; all 6 mutation kinds have actual audit-failure rollback. Production title/copy/loan locks return PostgreSQL 55P03 with bounded timeouts on separate transactions, then recover. One OPEN loan per copy, retained new-loan history, default 14 days, inactive/overdue return, strict versions, literal search and real UUID tie pages are covered. Unforced selective plans use 10,000 titles/copies/loans and agree with production queries, without latency/load claims.

Exact V25 database/public schema validates all 36 entities with Flyway disabled and ddl-auto=validate, unchanged prior rows/history and no Flyway bean. Historical V24 freezes 32 entities. Actual columns/defaults/approved lengths/nullability/PK/FKs/CHECK/partial unique/indexes and rejected SQLSTATE writes checked; large JSONB allowed without an invented cap. V1–V24 unchanged. 5C PASS; 5D audit viewing and 5E whole Phase 5 closure remain incomplete.

## Phase 5D Final Verification

Final `.\mvnw.cmd "-Dtest=AuditViewingIntegrationTest,AuditViewingQueryPlanIntegrationTest,AuditQueryContractTest,ModuleBoundaryTest,FlywayV24ToV25LibraryUpgradeIntegrationTest" test`: BUILD SUCCESS, 17 tests, zero failures/errors/skips, 1m10s, finished 2026-10-04T17:06:05+07:00. Includes separate-transaction count/page snapshot concurrency for all eight sources and exact V25/36-entity Hibernate validation with Flyway disabled.

Final `.\mvnw.cmd clean verify`: **BUILD SUCCESS, exit 0, 393 tests/67 suites, 0 failures/errors/skips, 7m36s**, finished **2026-10-04T17:14:15+07:00**. Independently summed XML agrees, jar packaged, all 44 pools closed cleanly without fork termination or closed-container warnings. Audit viewing adds 11 cases (7 API/database, 3 contract, 1 selective-plan); all prior Phase 1–5 regression/upgrade suites ran. No 5D migration/entity or V1–V25 change.

Both ADMIN read operations have anonymous/USER denials on all eight sources. Recorded fields, null historical versions, malformed VARCHAR/safe JSONB metadata, filters/bounds/source ownership, exact [from,until) boundaries, equal-time UUID pages/counts, no read-side audit mutation, and a real business-write→audit-read path are checked. Selective unforced plans use existing target indexes on 10,000 events per owner; no global-page performance claim. First testCompile generic error and correction are recorded in [5D review](reviews/phase-5d-audit-viewing-review.md). Whole-phase completion evidence is in [Phase 5 closure](reviews/phase-5-final-review.md).

## Phase 5E Closure

All approved Phase 5 backend slices and closure reviewed PASS; final source/test code is the same code verified at 2026-10-04T17:14:15+07:00 (393 tests/67 suites, 0 failures/errors/skips, 7m36s). Subsequent closure edits are documentation only. The [final requirement matrix](reviews/phase-5-final-review.md) records actual package totals, legacy/upgrade/security/ownership/audit/query coverage, immutable V1–V22 and retained practical limits. This is a scoped source/schema/test/diff review, not an exhaustive-coverage percentage or production/frontend release certification. No additional run is warranted without source/test changes or a new unresolved concern.
