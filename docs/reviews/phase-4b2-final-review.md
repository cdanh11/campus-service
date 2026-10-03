# Phase 4B2 — Manual Payments Review

Status: PASS on 2026-10-04 for Phase 4B2. Phase 4C and final Phase 4 closure remain incomplete.

Base ce5abce; feature/finance-payments depends on Finance obligations/Dormitory reviewed branches. V22 only; V1–V21 unchanged.

| Gate | Evidence |
| --- | --- |
| Exact money/lifecycle | Integer VND maximum/minimum, fraction/overflow rejection, partial/full amounts, immutable receipt/history, terminal full reversal with reason; no reuse of reversed number. |
| Balance/version | One aggregate balance query, charge version advances with every payment/reversal including coincident clock times. Cancellation requires zero net RECORDED amount. |
| Security/API | Anonymous/USER denials on all five new operations, trusted actor, malformed/query/request/version/reason validation, safe errors and Bearer/unique OpenAPI schemas. |
| Retained obligations | Settlement and reversal remain available after Student/fee deactivation; cancelled charges reject posting. |
| Atomic audit | PostgreSQL trigger rejects payment/reversal audit; complete receipt/charge rows/versions/events/balances remain unchanged. Existing obligation audit/cancel regressions run too. |
| Concurrency | Final balance competitors, duplicate receipt across charges, competing reversals, cancel/post and reverse/post; persisted outcomes/version/history/event counts. |
| Cache/lock | Cached charge and receipt refresh after separate committed changes. Separate transaction lock_timeout 500ms asserts 55P03 and subsequent success; bounded futures/latches and cleanup. |
| Queries | Database filters/count/page, allowed sorts and literal receipt search, actual timestamp-tie UUID pagination. |
| Upgrade/JPA | Real V21→only V22, legacy Identity/registries/Academic/Dormitory/Finance snapshots/history, complete new schema/defaults/constraints/indexes with SQLSTATE writes. Exact upgraded public schema Hibernate validate, Flyway disabled, 25 production entities; historical V21 remains 24. |

## Execution

Compile BUILD SUCCESS, 11.370s. Behavior focused (ManualPaymentTest, ManualPaymentIntegrationTest, FinanceObligationIntegrationTest): BUILD SUCCESS, 30 tests, no failures/errors/skips, 1m12s.

First complete focused gate failed at testCompile due to incorrectly escaped JSON in the new upgrade fixture. Replaced with PostgreSQL jsonb_build_object; production code/schema unchanged.

Complete focused command: `.\mvnw.cmd "-Dtest=ManualPaymentTest,ManualPaymentIntegrationTest,FinanceObligationIntegrationTest,FlywayV21ToV22PaymentUpgradeIntegrationTest,FlywayV20ToV21FinanceUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 38 tests, no failures/errors/skips, 1m45s. Review then clarified that CANCELLED retains original amount but has zero collectible outstanding, with domain/HTTP regression assertions covered by full verification.

Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 301 tests in 49 suites, zero failures/errors/skips, 7m05s, finished 2026-10-04T03:25:33+07:00; jar packaged and Surefire XML independently summed. Includes 21 new cases (3 domain, 15 API/transaction/lock/concurrency, 3 genuine upgrade/schema) and all Phase 1–4B1 regression.

Source/diff review inspected charge→receipt refresh locks, explicit charge version increment, single-query balance projection, immutable mappings, rollback/error/audit paths, bounded database queries and module ownership. `git diff --check` passed; V1–V21 unchanged, V22 only. No outstanding blocker/major in 4B2.

Non-blocking execution observation remains: Hikari warnings after disposable containers close, slow cached-context/JVM teardown and Surefire terminating its fork after the 30-second exit timeout. All tests completed with verified results; Maven returned BUILD SUCCESS and packaged the jar. This remains explicit Phase 4C work, not an external execution timeout or hidden test failure.

## Limits

Aggregate balance/cancellation protection is an application transaction invariant, not a cross-row database CHECK. Direct SQL is not a supported audited interface. Receipt reason is stored but no public audit endpoint or refund transfer exists. No load/coverage percentage claim. Existing full-suite slow JVM teardown remains a 4C task.
