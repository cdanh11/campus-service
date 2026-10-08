# Phase 5A — Notification Review

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Status: PASS for Phase 5A. No unresolved blocker or major finding in this review. Phase 5B–5E are incomplete; this is not whole-Phase-5 closure.

Base main 4d305db, merged Phase 4 PR #14; branch feature/supporting-services. User approved all Phase 5 modules, local in-app notifications, Event Student self-service plus ADMIN, Library ADMIN circulation and retained ADMIN-only audit viewing on 2026-10-04.

| Requirement | Evidence |
| --- | --- |
| Text/domain | Four NotificationDomainTest cases: six-character trimming, ROOT case expansion/Unicode limits, invalid lifecycle shape/version/query bounds, immutable publish, retained/idempotent read and invalid read time. |
| Templates/notices | Actual create/update/duplicate/stale APIs; locked ACTIVE template snapshots; deactivation/edit leaves draft content intact, publish makes content immutable, stored POST/GET precision. |
| Local delivery | 1–100 distinct explicit ACTIVE recipients, invalid mixed batch rollback, account status projection sees separate committed deactivation even with Identity cached in holder transaction. No external transport or credentials. |
| Ownership/security | Anonymous denial on all 12 operations, USER denial on all nine ADMIN operations. Foreign-owned read/write returns 404; actor/recipient client spoof ignored, inbox filtered by JWT principal. |
| Atomic audit | Real PostgreSQL trigger rejects template create/update, notice create/edit, publish and read; complete rows/version/event counts unchanged. Batch publication audit failure leaves no partial deliveries. |
| Locks/concurrency | Concurrent publish commits one batch/event; concurrent read is idempotent with one version/event. Actual production notice lock in separate transactions with bounded lock_timeout fails 55P03, then publish succeeds after release. Latches/futures bounded, transaction/executor cleanup, no arbitrary synchronization sleep. |
| Queries/API | Count/page filters, literal wildcard escaping, allowlisted sorts and actual timestamp-tie inbox UUID pages; bounded notice batch avoids N+1 content reads. Generated OpenAPI Bearer and explicit schemas/required fields. |
| V23 schema | Genuine V22 baseline fixtures across Identity/registry/Academic/Dormitory/Finance including receipt, every legacy table/history captured, only V23 applied. All four tables' columns/types/lengths/defaults/nullability/PK/FKs/uniqueness/indexes; SQLSTATE rejected writes and valid Unicode/JSONB/lifecycle boundaries. No invented JSONB length limit. |
| Exact Hibernate | Same upgraded public database/schema; Flyway disabled, ddl-auto=validate and all 29 production entities. History unchanged. Prior V22 test freezes original 25 entity packages; V1–V22 immutable. |
| Boundaries | Notification owns data/query/audit, eligibility via IdentityUserDirectory, no foreign persistence reference. Source review plus ModuleBoundaryTest. |

## Execution

- Compile: BUILD SUCCESS, 13.718s (before subsequent API additions).
- Domain/boundary/historical V22: BUILD SUCCESS, eight tests, 40.095s.
- Initial API behavior: BUILD SUCCESS, 13 tests, 40.375s.
- Initial complete focused: BUILD SUCCESS, 20 tests, 1m09s.
- Final focused `.\mvnw.cmd "-Dtest=NotificationIntegrationTest,NotificationDomainTest,FlywayV22ToV23NotificationUpgradeIntegrationTest,FlywayV21ToV22PaymentUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 23 tests, zero failures/errors/skips, 1m05s, finished 2026-10-04T14:56:00+07:00. Full verify also covers subsequent allowlisted-sort and actual tied inbox-page assertions.
- Full `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 323 tests in 53 suites, zero failures/errors/skips, 5m58s, finished 2026-10-04T15:03:14+07:00. Surefire XML independently summed; jar packaged, all 34 Hikari pools shut down, no fork termination or closed-container warning. V1–V22 unchanged; working diff checked.

## Limits

Status eligibility is checked at the operation decision, not frozen by cross-module locking. No SMTP/SMS, broadcast, executable templates, scheduled/automatic domain subscription or external retry queue. Local delivery/publish is atomic and rollback may be retried with its unchanged version. Direct SQL is not an audited interface and application immutability is not a database immutability claim. No measured load/coverage percentage. Event, Library, audit viewing and whole Phase 5 closure require their own gates.

Later status: whole Phase 5 backend closure reviewed PASS on 2026-10-04 after 5D and final 393-test/67-suite regression. See [Phase 5 closure](phase-5-final-review.md); earlier pending notes above are historical slice-time evidence.
