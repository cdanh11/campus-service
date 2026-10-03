# Phase 3D — Academic Hardening

## Scope and sequence

Start from merged Phase 3C at `51b48b3` on `feature/academic-hardening`. Preserve V1–V17 and all unrelated local changes, including the untracked roadmap. No Phase 4, frontend, production deployment or new Academic workflow.

1. Add Academic-owned audit for all twelve administrative create/update operations covering Program, Course, Term, Offering, Section and Enrollment. Actor comes from authenticated JWT principal, never the request body. Mutation and audit commit/rollback together. Enrollment withdrawal/re-enrollment has explicit actions. Store resource ID/type/version/status and time, without names, contact data, passwords or tokens. Internal provisioning/fixture use cases remain unaudited; HTTP mutations always use the audited application entry point.
2. Add V18 with approved audit columns, JSONB object metadata (no arbitrary length limit), FK to actor, resource/action/version constraints and resource/time indexes. No polymorphic target FK; Academic owns target existence checks. Record this policy in ADR 0006.
3. Consistent safe audit-error envelope; inspect existing error handlers and security scopes. Verify generated OpenAPI describes protected Academic operations and actual request/query contracts. Document endpoint behavior and deferred use cases.
4. Query/index review with actual PostgreSQL EXPLAIN evidence where useful, bounded pagination/stable sort/literal search tests. Add indexes only for identified queries; do not claim benchmarks from tiny data or force planner options to imply performance.
5. Real V17→V18 upgrade with full baseline/history preservation, schema/default/constraint/index/SQLSTATE checks and Hibernate validate on that exact database/schema with Flyway disabled. Historical V17 validation freezes its entity set.
6. Review Phase 1–3 source boundaries, security/configuration, transaction/audit/version behavior and migration immutability. Resolve blocker/major findings with regression tests. Build full project, inspect diff, then scoped commits/push after PASS.

## Strict verification gate

- Every resource create/update via HTTP records exactly one correct actor/target/action/version/status event. Reads and rejected/malformed/unauthorized/stale/duplicate mutations record none. Audit failure rolls back create/update and version/seat changes; success is never returned for partial work.
- Real concurrent update/enrollment/parent lifecycle tests remain green under audit. Verify event count matches successful transactions and failed writes add no events.
- Migration tests prove defaults/nullability/PK/actor FK/enums/version/object metadata and valid/invalid inserts with specific SQLSTATE; no invented JSONB length cap. Validate all production entities on the exact upgraded schema and prove Flyway is absent and history unchanged.
- OpenAPI and HTTP authorization for catalog/delivery/enrollment, documented query/error shapes; module application contracts only, no external persistence imports.
- Focused tests followed by persistent `.\mvnw.cmd clean verify`, actual Maven outcome/totals/time, `git diff --check`, unchanged V1–V17 and no secrets/generated files staged. Roadmap remains local.

Status: implemented; review PASS on 2026-10-04, ready for user PR/merge. Phase 3 is complete within its approved catalog/delivery/enrollment/hardening scope.

## Verified outcome

- HTTP 3A–3C regression: BUILD SUCCESS, 50 tests, no failures/errors/skips, 4m16s.
- Focused audit/query/concurrency/migration/boundary/Phase 2 paging: BUILD SUCCESS, 28 tests, no failures/errors/skips, 1m18s.
- Full final clean verify: BUILD SUCCESS, 200 tests, no failures/errors/skips, 4m56s. Documentation totals are based on the completed build, not estimates.
- Review added missing JPA offset guards for all three Phase 2 query endpoints; boundary tests verify reject and valid-edge cases. Actual PostgreSQL EXPLAIN on 10,000 synthetic pairs supports keeping the existing enrollment indexes without speculative additions.
- See [final Phase 1–3 review](../reviews/phase-3d-final-review.md), [API contract](../api/academic.md) and ADR 0006. V1–V17 remain unchanged; V18 is the only new migration. Audit has no JSONB length cap, private snapshots, query endpoint, backfill or claim of DB-enforced append-only behavior. Roadmap remains local.
