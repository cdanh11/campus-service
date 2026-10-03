# Phase 4A1 — Dormitory Inventory Review

Status: PASS on 2026-10-04 for the approved inventory slice. No unresolved blocker/major finding remains in this review. Phase 4 as a whole is still in progress.

Base: merged Phase 3D at e64cfc7, branch feature/dormitory-foundation. Scope is inventory only; Phase 4A2 allocation and Phase 4B finance are not implemented here.

## Evidence matrix

| Gate | Evidence |
| --- | --- |
| Domain/text/query limits | InventoryItemTest: six-character trim, Unicode bounds/uppercase expansion, valid parent shape, nonnegative version, query/offset boundary. |
| HTTP authorization/contracts | DormitoryInventoryIntegrationTest: anonymous 401/USER 403 for all four operations on three resources; ADMIN mutations/Location/read/list; malformed/validation/reference/query errors; immutable parent and scoped codes. |
| Transaction/audit | Real PostgreSQL trigger failures roll back complete table snapshots for all three create/update paths. Events verify actor, resource, action, resulting version, status-only metadata and time; rejects/reads add no success event. |
| Concurrency/lifecycle | Three competing-update cases commit one version/event; two parent-close/child-create races permit exactly one transaction; two separate-transaction production parent locks reject contender with bounded lock_timeout and SQLSTATE 55P03, then allow acquisition after release. No arbitrary sleep synchronization. |
| Queries | Database criteria filters/count/page, literal %/_/! with Unicode fixtures, every approved sort direction, actual timestamp tie/UUID pagination, status and parent filters, maximum integer offset. No load benchmark claim. |
| Upgrade/schema | Genuine V18→only V19, all pre-existing public tables/history snapshot preserved; inventory/audit types, lengths, nullability/defaults, PK/FK/scoped uniqueness/indexes, approved CHECK boundaries and specific SQLSTATE rejection. Large JSON objects accepted. |
| Exact Hibernate validation | Same PostgreSQL/public schema upgraded from V18 to V19, Flyway disabled, ddl-auto=validate, 20 production entities. Historical V18 scan frozen to its original 16 entities. |
| Module/source review | Dormitory owns inventory/audit persistence; no cross-module persistence access or allocation/billing integration. SQL has only the approved Identity actor FK. Dynamic entity selection is an internal enum allowlist, not client SQL. |
| OpenAPI | Bearer on every inventory operation; explicit Dormitory schema names avoid global record-name collisions. Test verifies actual versioned update schema and immutable-parent contract. |

## Review disposition and execution history

- Initial domain/API run: BUILD SUCCESS, 24 cases, no failures/errors/skips, 46.997s.
- First upgrade run failed on an invalid test fixture: legacy identity_users.status has no default. Fixture now explicitly supplies ACTIVE; no existing migration was changed.
- Next focused attempt failed during Docker discovery with daemon HTTP 503, before integration assertions. Docker later returned running with server version 29.2.0.
- Without-Docker domain/module guard: BUILD SUCCESS, 7 cases, no failures/errors/skips, 9.513s.
- Focused re-run: BUILD SUCCESS, 40 cases, no failures/errors/skips, 1m30s. Full clean verify covers subsequent explicit OpenAPI schema names/field assertions.
- Final .\mvnw.cmd clean verify: BUILD SUCCESS, 244 tests in 40 suites, zero failures/errors/skips, 5m28s; packaged Spring Boot jar. Surefire XML agrees with Maven. Dormitory adds 36 cases (6 domain, 24 API/transaction/concurrency, 6 upgrade/schema).
- Final diff check clean after removing generated trailing whitespace/extra EOF blank lines. V19 is the only new migration; V1–V18 unchanged. No deployment, Flyway repair or database/volume deletion. The local roadmap remains outside staging.

## Limits and next gate

Inventory ACTIVE is lifecycle availability, not occupancy. No assignment, Student eligibility, booking intervals, vacancy counter, Finance/payment, delete/cascade, audit query/backfill/retention, database-enforced append-only or production deployment. The fixed resource route shares structure for only three inventory types. Mutation locks serialize within a building; no production-scale latency claim. Phase 4A2 must implement occupied-bed protection and current Student/bed uniqueness before exposing allocation APIs.
