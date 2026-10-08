# Phase 5C Library Review

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Status: PASS on 2026-10-04. No unresolved blocker/major finding within approved 5C scope. Source/schema/security/query/docs and working diff reviewed; git diff --check passed. Audit viewing 5D and closure 5E remain incomplete; this is not a whole Phase 5 PASS.

Approved scope: titles/physical copies, one OPEN loan per copy, default 14 days, retained returns, ADMIN circulation, no fines. Base for this slice: 51c744f on feature/supporting-services. [ADR 0013](../decisions/0013-library-circulation-and-history.md) and [API contract](../api/library.md).

| Requirement | Evidence |
| --- | --- |
| Domain | Five cases: exact six-character boundary trim, Unicode codepoint limits, ROOT code expansion, immutable identifiers/associations/creation dates, version bounds, OPEN/RETURNED history shapes, exact elapsed 14-day due date, overdue return, resource-specific bounded queries. |
| Security/API | ADMIN-only on all 12 operations, anonymous 401 and USER 403; Bearer on each OpenAPI operation, nine distinct request/page schemas. Stored timestamp precision, actor/date spoof ignored, Unicode boundaries, missing/duplicate/inactive references, terminal/stale updates and strict integer JSON versions. |
| Audit | PostgreSQL trigger failure rolls back each of six mutation types, including complete rows/versions/timestamps and audit count. Retained actions and actor UUID/status-only object metadata checked. |
| Concurrency | Separate transactions: same-copy borrow race and duplicate return; fresh cached title/copy/Student/loan state. Each production title/copy/loan lock produces bounded lock_timeout SQLSTATE 55P03 while held, followed by successful return after release; reliable transaction/executor cleanup, no arbitrary sleeps. Final added races cover title/copy deactivation versus admission and return versus next borrowing. |
| Queries | Actual equal-time UUID tie pages for titles/copies/loans, literal wildcard search, filters/counts/all allowlisted sorts. Selective plans after ANALYZE with 10,000 titles/copies/loans; justified existing indexes and agreement with production catalog/availability/Student loan pages; no forced planner or benchmark claim. |
| Migration | Five genuine V24→V25 upgrade cases: populated representative Identity/People/Academic/Dormitory/Finance/Notification/Event data and all old tables/history; applies exactly V25. All four tables' columns/types/approved lengths/nullability/defaults/PK/FKs/unique/partial-index/CHECK/indexes, specific SQLSTATE writes, Unicode/history/default-date boundaries and large JSONB without invented limit. V1–V24 unchanged. |
| Hibernate/boundary | Exact upgraded public database, Flyway disabled, ddl-auto=validate, no Flyway bean/history changes, all 36 production entities. Historical V24 scan fixed to original 32. ModuleBoundaryTest plus source review finds no foreign persistence/table queries in Library. |

## Executed verification

- Foundation: `.\mvnw.cmd "-Dtest=LibraryDomainTest,FlywayV23ToV24EventUpgradeIntegrationTest,ModuleBoundaryTest" test` — BUILD SUCCESS, 10 tests, 0 failures/errors/skips, 34.019s, finished 2026-10-04T16:29:32+07:00.
- API checkpoint: `.\mvnw.cmd "-Dtest=LibraryIntegrationTest" test` — BUILD SUCCESS, 10 tests, 0 failures/errors/skips, 44.149s, finished 2026-10-04T16:33:59+07:00.
- Focused: `.\mvnw.cmd "-Dtest=FlywayV24ToV25LibraryUpgradeIntegrationTest,LibraryQueryPlanIntegrationTest,LibraryDomainTest,LibraryIntegrationTest,FlywayV23ToV24EventUpgradeIntegrationTest,ModuleBoundaryTest" test` — BUILD SUCCESS, 26 tests, 0 failures/errors/skips, 1m10s, finished 2026-10-04T16:38:31+07:00. Precedes the final deactivation/return race case and added copy tie-page assertions; full verify must include those changes.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, **382 tests/64 suites**, zero failures/errors/skips, **7m32s**, finished **2026-10-04T16:47:05+07:00**. Covers the final added races and copy tie-page assertions plus all Phase 1–5C regression. Independently summed Surefire XML agrees; jar packaged; all 42 Hikari pools closed cleanly, no fork termination or closed-container warnings. Library adds 22 cases across four suites (5 domain, 11 API, 5 exact-upgrade, 1 selective-plan).

## Limits and remaining work

Student eligibility is decision-time through its own application projection; no foreign write lock. Title-first locking deliberately serializes circulation within a title and is sufficient for local scope, without a throughput claim. Catalog and loan history are retained; no deletion, renewal, reservation, fine, lost-book workflow, Student self-borrow or automatic Notification/Finance write. Returning does not require currently active references. Direct SQL is not a supported audited mutation interface. Audit viewing 5D and whole-phase regression closure 5E remain incomplete.

Later status: whole Phase 5 backend closure reviewed PASS on 2026-10-04 after 5D and final 393-test/67-suite regression. See [Phase 5 closure](phase-5-final-review.md); earlier pending notes above are historical slice-time evidence.
