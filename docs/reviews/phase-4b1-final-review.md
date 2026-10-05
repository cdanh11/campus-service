# Phase 4B1 — Finance Obligations Review

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Status: PASS on 2026-10-04 for Phase 4B1. Finance payments and Phase 4C remain incomplete.

Base 8182597 on feature/finance-obligations, dependent on the reviewed Dormitory feature branch; origin/main remains Phase 3D. V1–V20 unchanged, V21 only.

| Gate | Evidence |
| --- | --- |
| Exact money | Domain/HTTP integer minimum/maximum, fractional and overflow rejection; SQL rejects fractions without scale rounding, NaN/infinity/nonpositive/overflow; exact maximum fee-to-charge snapshot. |
| Historical obligations | Fee changes and fee/Student deactivation retain charge identity, amount/currency/code/name/due date/creation time. Cancellation terminal and versioned; no editable reference/financial fields. |
| Security/API | Anonymous/USER denials on all eight operations, trusted actor, malformed/request/query bounds, duplicate/stale/reference errors; OpenAPI Bearer, unique schemas. |
| Atomic audit | PostgreSQL failing audit trigger exercises fee create/update and charge create/cancel; complete fee/charge row snapshots and event counts unchanged. |
| Concurrency | Competing fee updates/cancellations/duplicate charge creation; fee closure versus admission; persisted versions/snapshots/event counts. Cached fee explicitly sees separately committed deactivation. |
| Locking | Separate transactions, bounded 500ms PostgreSQL lock_timeout, SQLSTATE 55P03, acquisition after release; future/latch bounds and cleanup, no sleep synchronization. |
| Query | Criteria filters/count/page in database; literal q, allowlisted sorts/offset bounds, actual due-date-tie UUID pagination. |
| Upgrade | Migrate V20, representative Identity/roles/registries/Academic/Dormitory/assignment/audit data and all table/history snapshots, apply exactly V21; columns/types/lengths/nullability/defaults/PK/FKs/uniqueness/CHECK/indexes and specific SQLSTATE writes. |
| Exact JPA | Flyway disabled, ddl-auto=validate against same upgraded public schema, 24 production entities; history/data unchanged. Historical V20 frozen at 21. |
| Ownership | Student application contract only; no external persistence imports or direct cross-module queries. Owned FKs preserve UUID references. |

## Execution evidence

- Compile: BUILD SUCCESS, 10.492s.
- Focused: `.\mvnw.cmd "-Dtest=FinanceObligationTest,FinanceObligationIntegrationTest,FlywayV20ToV21FinanceUpgradeIntegrationTest,FlywayV19ToV20AssignmentUpgradeIntegrationTest,ModuleBoundaryTest" test`: BUILD SUCCESS, 23 cases, no failures/errors/skips, 1m17s. Cached-fee and maximum charge snapshot assertions added afterward are covered by full verification below.
- Final `.\mvnw.cmd clean verify`: BUILD SUCCESS, exit 0, 280 tests in 46 suites, zero failures/errors/skips, 6m37s; finished 2026-10-04T02:58:48+07:00 and packaged the jar. Independently summed Surefire XML agrees. Includes all 20 Finance cases (4 domain, 12 API/transaction, 4 exact upgrade/schema) and Phase 1–4A regression, including the subsequent cached-fee/maximum-snapshot assertions.
- Source/diff review covered money normalization, immutable mapping/snapshots, lock refresh/order, transaction/audit boundaries, safe HTTP errors, allowlisted queries and external module imports. `git diff --check` passed. V1–V20 unchanged; V21 only. No outstanding blocker/major in this slice.
- Non-blocking suite lifecycle finding persists: closed-container Hikari warnings and the slow test-JVM exit caused Surefire to terminate the fork after 30 seconds, after all complete successful test results. Maven packaging and exit 0 prove BUILD SUCCESS. Address teardown in 4C rather than hiding it by changing timeouts or skipping tests.

## Limits

No payment, balance, reversal, gateway, automated billing, accounting ledger, multicurrency, discount, installment or self-service. Direct SQL is not an audited business interface. Student status is checked at the decision rather than frozen across modules. Fee locks are conservative; no benchmark/coverage percentage claimed. Phase 4B2 must extend cancellation safety under the charge lock when payment records exist. Full-suite slow test-JVM shutdown observed in 4A2 remains a Phase 4C follow-up.
