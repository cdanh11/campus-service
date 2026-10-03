# Phase 4A2 — Accommodation Review

Status: PASS on 2026-10-04 for Phase 4A2. Phase 4 remains active; Finance and operations hardening have separate gates.

Base: 4A1 ba76a28 on feature/dormitory-foundation. Main still contains Phase 3D; no user PR was merged by the agent. V1–V19 unchanged, V20 only.

| Gate | Evidence |
| --- | --- |
| Lifecycle/history | AccommodationAssignmentTest and HTTP lifecycle case: ASSIGNED→RELEASED, terminal released row, immutable Student/bed, new UUID for later stay, preserved timestamps/version. |
| Security/API | 401/403 for all four operations, ADMIN create/get/query/PUT, invalid query/body/UUID, safe errors and trusted actor; OpenAPI Bearer and unique versioned release schema. |
| Eligibility/deactivation | Active Student through application contract, active ancestors/bed, current assignment blocks bed deactivation; release after Student deactivation, no silent cascade. |
| Atomic audit | Real PostgreSQL audit failure snapshots complete assignment rows, versions/occupancy and event counts for admission/release; successful event actor/action/status/version. |
| Concurrency | Same-bed competitors, same-Student/different-building beds, stale releases, release/admission and bed-deactivate/admission: bounded latch-synchronized transactions and persisted outcomes/event counts. Real lock timeout asserts 55P03 and success after release. |
| Schema evolution | Genuine V19→only V20; representative legacy identity/registries/academic/inventory/audit plus all table/history snapshots preserved. Types/defaults/nullability/PK/FKs/status/version/release shape/partial uniqueness/indexes, approved audit compatibility and SQLSTATE tests. |
| Exact Hibernate | Flyway disabled, ddl-auto=validate on same upgraded public schema, all 21 production entities. Historical V19 retains 20; V18 retains 16. |
| Ownership/query | Only Student application contract, no external persistence imports. Bed/current-student partial uniqueness plus status indexes; bounded filters/sort and actual tie pagination. |

## Findings and disposition

- First 4A2 focused run: BUILD FAILURE, 47 cases, 1 failure. POST returned nanosecond timestamps while PostgreSQL stored microsecond precision, causing preserved history to differ on later read/release.
- Fixed in assignment persistence: flush and refresh before returning created/updated domain record. Tests retain strict timestamp equality; further GET equality assertions cover complete response records. No existing migration was edited to hide this behavior.
- Initial 4A1 regression after assignment integration: BUILD SUCCESS, 36 cases, no failures/errors/skips, 1m18s.
- Final focused command: `.\mvnw.cmd "-Dtest=AccommodationAssignmentTest,AccommodationAssignmentIntegrationTest,FlywayV19ToV20AssignmentUpgradeIntegrationTest,DormitoryInventoryIntegrationTest,FlywayV18ToV19DormitoryUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 47 cases, no failures/errors/skips, 1m34s. Subsequent full-response GET equality assertions are covered by clean verify.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 260 tests in 43 suites, no failures/errors/skips, 6m29s, finished 2026-10-04T02:33:54+07:00; packaged jar and independently summed Surefire XML agree. Includes Phase 1–3 and 4A1 regression plus 16 new 4A2 cases.
- Non-blocking execution observation: closed-container Hikari connection warnings and a slow test-JVM shutdown caused Surefire to terminate its fork after the 30-second exit timeout. All test results were complete and Maven packaging succeeded. This is recorded for suite lifecycle hardening; it is not a test failure or an external execution timeout.
- Diff review: supported transaction/lock paths, partial uniqueness, authorization/error/audit contracts, query bounds, module ownership and exact schema/history assertions inspected. `git diff --check` passed; only V20 is added, V1–V19 unchanged. No outstanding blocker/major finding in this slice.

## Limits

No booking dates, transfer/reactivation of released stay, billing, self-service, deletion/cascade or audit read API. Student status is checked at the operation decision rather than atomically frozen across modules. Building locks are conservative; no load benchmark. Direct SQL is not a supported lifecycle/audit interface. Finance and Phase 4C remain separate review gates.
