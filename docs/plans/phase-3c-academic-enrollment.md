# Phase 3C — Enrollment Foundation

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

## Scope and implementation order

Start from merged Phase 3B (`a903c3e`) on `feature/academic-enrollment`. Implement domain/schema, persistence/application, ADMIN HTTP APIs, then adversarial migration/concurrency/security verification. Preserve V1–V16; add V17. Student eligibility is obtained through the Student application contract, never through its persistence internals. No student self-service, waitlist, grades, tuition or scheduling is included.

## Business contract

- One enrollment record per student/section pair, with immutable identifiers, status ENROLLED or WITHDRAWN, nonnegative expectedVersion for PUT, and retained timestamps/version. No physical DELETE.
- Creation requires an ACTIVE student, ACTIVE term, OPEN offering and OPEN section. Only ENROLLED rows consume capacity. Missing/inactive students are unavailable external references; missing Academic resources are 404.
- Withdrawals release capacity, preserve history and remain possible after section closure or student deactivation. Withdrawal is not a grade/completion decision.
- WITHDRAWN → ENROLLED is allowed on the same record with expectedVersion, rechecking student eligibility, parent lifecycle and available capacity. Approved by the user on 2026-10-03. Duplicate POST never silently reactivates an existing record.
- All enrollment writes lock term → offering → section before reading occupancy or locking the enrollment. Section closure follows the same lock order. No independent counter or cached capacity calculation.
- Capacity is protected for application mutations under PostgreSQL READ COMMITTED; direct SQL writes are not an approved enrollment interface. PostgreSQL enforces references, pair uniqueness, status, nullability and version boundaries.
- References are checked at the enrollment decision; later Student deactivation does not rewrite historical enrollment. There is no cross-module lock or atomic freeze of Student status.
- ADMIN POST/GET/list/PUT `/api/v1/admin/academic/enrollments`; bounded database pagination, studentId/sectionId/status filters, allowlisted sorting and stable id tie-breaker. Error envelope follows existing APIs: 400 malformed/validation/query, 401/403 security, 404 missing Academic resource, 409 duplicate/stale/ineligible/full/state.

## Strict review gate

PASS requires requirement-by-requirement evidence, rather than a green build alone:

1. Domain transitions, immutable references, rejected mutation rollback and version behavior.
2. HTTP authorization on every route, invalid bodies/query parameters, duplicate/missing/inactive references, capacity boundaries and stable pagination.
3. Separate real transactions synchronized with latches/barriers and bounded waits: two students compete for the last seat; duplicate enrollment races; competing stale updates; withdrawal/re-enrollment races; section close versus enrollment. Assert persisted outcomes and occupancy, not only response status. No arbitrary sleep as primary synchronization.
4. Genuine V16→V17 upgrade: representative baseline records and full history preserved; columns/types/defaults/nullability, PK/unique/FKs/CHECK/indexes, valid/invalid SQLSTATE assertions; production JPA entities validated against that exact schema with Flyway disabled and no create/update behavior. Historical V16 validation keeps its original production entity set.
5. Query/index and module-boundary review, unchanged V1–V16, focused tests followed by persistent `./mvnw.cmd clean verify`, verified totals and `git diff --check`.
6. Resolve every blocker/major finding before PASS. Document residual limitations and defer wider Academic audit to 3D. Update docs only with actual verification evidence; commit by function and push after PASS. Local roadmap stays untracked.

Status: implemented; final review PASS on 2026-10-04; merged in PR #10 at `51b48b3`.

## Verified outcome

- Focused Enrollment + 3B regression/schema tests: BUILD SUCCESS, 47 tests, no failures/errors/skips, 1m32s.
- Full final `./mvnw.cmd clean verify`: BUILD SUCCESS, 176 tests, no failures/errors/skips, 4m18s. The first full run failed on the closure/admission cached-state race; it was corrected before this final result.
- V1–V16 remain unchanged; V17 is the only new migration. Diff whitespace checks are clean. Roadmap stays local.

## Review evidence matrix

| Gate | Evidence |
| --- | --- |
| Membership lifecycle/history/version | EnrollmentTest; withdraw/re-enroll, same-state rejection, stale rollback, deactivated Student and closed-section cases in EnrollmentIntegrationTest. |
| Authorization and HTTP contract | Anonymous 401 and USER 403 for all four operations; ADMIN POST/get/list/PUT; exact duplicate/reference/capacity/stale/state codes; invalid JSON/UUID/status/version/query bounds. |
| Capacity and transactional integrity | Last-seat race, duplicate race, competing withdrawals, restored membership versus new student; persisted occupancy and version assertions after failure. |
| Parent lifecycle/lock behavior | Closure versus admission; separate PostgreSQL transactions with lock_timeout and SQLSTATE 55P03, successful acquisition after release; deterministic cached-entity/committed-closure regression. |
| Database evolution | V16 fixtures → exactly V17; all pre-existing tables/history snapshot preserved; metadata/defaults/PK/FKs/pair uniqueness/indexes and 23502/23503/23505/23514/22001 rejection cases. |
| Hibernate validation | Minimal production entity scan, exact upgraded public schema, ddl-auto=validate, Flyway disabled; 15 entities and unchanged history/data; historical V16 scan fixed at its 14 entities. |
| Query and module boundary | Repository specifications execute filters/count/page at PostgreSQL; page/size/offset bounds, allowlisted sort + ID tie-breaker; section/status and student/status indexes; Student application access only. |

The first full suite caught a real stale-persistence-context issue during section closure versus admission: the old locking JPQL query could compare a cached version before refresh. Delivery adapters now load/find the entity and acquire PESSIMISTIC_WRITE through refresh directly. Unused locking queries were removed. The deterministic regression retains an old section in one context, commits closure through another transaction, then proves admission uses the new CLOSED state. Existing 3B lifecycle tests are part of the focused regression gate.

Residual boundaries: no load benchmark; parent locks serialize a term's writes; external Student status is checked at the decision rather than atomically frozen; course-level uniqueness across sections, registration windows, self-service, waitlist and wider Academic mutation audit are not part of 3C. Closed sections retain enrollment history. See ADR 0005.
