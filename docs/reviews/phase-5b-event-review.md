# Phase 5B Event Review

Status: PASS for Phase 5B on 2026-10-04. No unresolved blocker/major finding within approved scope. Notification 5A and Event 5B are complete; Library, audit viewing and whole Phase 5 closure remain incomplete.

Base main 4d305db; branch feature/supporting-services. User approved linked Student self-service plus ADMIN, same-record restoration with expectedVersion/audit, and OPEN-only admission with manual ADMIN closure. [ADR 0012](../decisions/0012-event-membership-ownership-and-capacity.md) records the contract.

## Requirement evidence

| Requirement | Verified evidence |
| --- | --- |
| Catalog/domain | Four domain cases cover exact six-character trim, ROOT expansion/Unicode limits, schedule/capacity/version/lifecycle and bounded query/sort. Eight catalog API cases verify all six operation authorization, stored precision, trusted actor, malformed/duplicate/stale/terminal behavior, strict JSON capacity and version parsing, actual tied pages and OpenAPI. |
| Ownership/eligibility | Two Student-directory PostgreSQL cases plus registration API cases verify current link/status, no contact fields, inactive/unlinked/missing profiles, owner/foreign 404, spoofed Student/actor ignored, ADMIN for Student without account and owner ATTEND forbidden. Scalar by-ID projection rejects independently committed Student deactivation despite cached entity. |
| Lifecycle/history | Five membership-domain cases and 13 registration API cases verify cancellation frees a seat, restoration reuses UUID/creation history and latest registration time, expectedVersion, terminal attendance consuming capacity, OPEN/CLOSED attendance, no attendance on cancelled Event, retained cancellation after Event closure/cancellation or Student inactivity, and OPEN past-event admission without implicit date cutoff. All eight registration operations have authorization coverage and Bearer schemas. |
| Atomic audit | Real PostgreSQL trigger failure rolls back every catalog create/update and membership register/cancel/restore/attend row/version/event. Trusted actor and status-only metadata, with complete retained action/version sequence. |
| Capacity/locks/races | Real count replaces zero scaffold under refreshed Event lock. Last-seat new/new and restore/new races, duplicate membership, competing restores, cancel/attendance, closure/admission and capacity reduction/admission. Cached catalog/member state rejects old versions. Separate transactions with bounded production Event/member lock_timeout yield SQLSTATE 55P03, then succeed after release; bounded latches/futures and reliable cleanup, no sleep-based synchronization. |
| Queries | Both surfaces have bounded filters/status/allowlisted sorts, actual timestamp-tie UUID pages/counts and literal catalog matching. One query-plan case with 10,000 Events, 10,000 Students and 10,000 memberships verifies existing selective catalog/Student indexes, valid Event-prefixed count index and agreement with production page/count projections; no forced planner. |
| Genuine V24 | Populated V23 Identity/People/Academic/Dormitory/Finance/Notification data and all old tables/history captured; apply exactly V24. Four upgrade cases verify all three tables' actual columns/types/approved lengths/defaults/nullability, PK/FKs/retained unique/CHECK/indexes, specific SQLSTATE rejected writes, temporal/Unicode boundaries and large JSONB without invented limit. No V1–V23 changes. |
| Exact Hibernate | All 32 production entities on the exact upgraded public database/schema; Flyway disabled, ddl-auto=validate, no create/update or Flyway bean, old rows/history unchanged. Historical V23 entity scan freezes original 29. |
| Ownership/review | Event owns query/mutation/audit; Student-owned scalar application contract, no foreign persistence imports/table reads. ModuleBoundaryTest, complete source/schema/diff/docs review and git diff --check. |

## Execution and corrected findings

- Catalog checkpoint full verify previously passed 339 tests/57 suites, 6m20s; this is historical pre-membership evidence.
- Fractional capacity JSON initially produced 201; Event-local strict count parser fixed it. Cached-state test cleanup was corrected to rollback its expected-error transaction.
- Version regression failed one of eight API cases (39.914s): expectedVersion=0.5 was accepted as zero. Strict Event-local version parser fixed fractional/string/overflow inputs; version/query focused passed 14 tests (53.035s).
- Membership/schema focused passed 20 tests (1m02s), including exact 32-entity validation.
- Complete focused command `.\mvnw.cmd "-Dtest=EventRegistrationIntegrationTest,EventRegistrationTest,EventCatalogIntegrationTest,EventCatalogQueryPlanIntegrationTest,CampusEventTest,FlywayV23ToV24EventUpgradeIntegrationTest,FlywayV22ToV23NotificationUpgradeIntegrationTest,StudentAccountDirectoryIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 40 tests, zero failures/errors/skips, 1m29s, finished 2026-10-04T15:56:41+07:00, before subsequent final-seat/lifecycle and expanded plan assertions.
- Later all 13 registration API cases passed. Expanded plan test initially rejected a valid parent-prefixed unique index; assertion now accepts either justified Event index and verifies actual event/status predicates. Final query-plan focused: BUILD SUCCESS, one case, 32.276s, finished 2026-10-04T16:02:40+07:00.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, **360 tests/60 suites**, zero failures/errors/skips, **6m29s**, finished **2026-10-04T16:10:34+07:00**. Independently summed Surefire XML agrees; jar packaged; all 39 Hikari pools closed without fork termination or closed-container warnings. Covers every subsequent assertion and all Phase 1–5B regression.

## Limits

Eligibility/link checks are decision-time, without freezing foreign registry transactions; direct SQL bypasses are not supported audited mutation interfaces. Aggregate capacity is application-transaction protection, not an invented cross-row CHECK. No fees, waitlist, scheduler, automatic Notification coupling, external ticketing, measured coverage percentage or production latency/load claim. Catalog DRAFT reads are authenticated as documented. Library 5C, audit viewing 5D and closure 5E have separate gates; this PASS does not complete the goal.