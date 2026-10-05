# Phase 5D — Read-only ADMIN Audit Viewing

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Approved on 2026-10-04: ADMIN reads retained audit history; no deletion or automated expiry. Implemented after Library 5C PASS; **5D review PASS** on 2026-10-04. See ../reviews/phase-5d-audit-viewing-review.md and ../api/audit-viewing.md.

## Actual historical schemas

- Identity owns identity_admin_audit_events: target_user_id, actor, action, occurred_at and VARCHAR(1000) metadata; no resource_version column.
- Shared people-registry audit owns people_registry_audit_events: resource_type/target_id, actor, action, occurred_at and VARCHAR(1000) metadata; no resource_version column.
- Academic, Dormitory, Finance, Notification, Event and Library own their respective audit tables, including resource_version and JSONB metadata; actual shape inspected before implementation.

Do not backfill or invent missing historic version values, claim VARCHAR metadata was constrained as JSONB, or read another module's tables from an audit aggregator. Any JSON parsing/sanitization must have deterministic handling of valid legacy text and malformed direct-SQL fixtures without leaking private content or causing the whole page to fail.

## Proposed query contract

Use a selected source per request with domain-owned application query ports and one shared bounded DTO/filter contract. This avoids pretending separately paginated sources form a correct globally ordered page. ADMIN API provides get by source/id and list by source with optional target/actor/action/resource/time filters as supported by that source. Count and page remain in the owner database query, stable occurredAt/id order; bound page, size, offset and validate all source-specific fields. Missing source/event yields safe errors. Versions are nullable only for sources that never recorded them.

Responses expose event identity, source, resource, target, actor UUID, action, optional recorded version and time. Metadata uses an explicit safe projection of approved historical keys; do not expose passwords, credentials, tokens or arbitrary request payload. No audit mutation, expiry, export, unbounded cross-source aggregation or reconstruction from current entity values. Existing business audit recording remains unchanged.

## Gate

ADMIN versus USER/anonymous checks for every route; real rows across every source, historical no-version behavior, safe metadata, invalid filters/ranges/offsets, exact time boundaries, actual equal-time UUID pages/counts and missing records. ModuleBoundaryTest plus source review of SQL ownership; assert queries do not write history. Review selective query plans before adding a justified new index; any new index needs its own migration/upgrade verification, never alter old migrations. Run focused and full clean verify, review diff and documentation, then Phase 5E requirement-by-requirement closure across Phase 1–5.

Verified: two ADMIN GET operations, eight owner application ports/adapters, safe recorded-field/status projection and read-only REPEATABLE_READ count/page snapshot. Selective plans use existing indexes on 10,000 rows in each audit source; no new migration/entity, V1–V25 unchanged. Final focused 17 tests (1m10s); full clean verify BUILD SUCCESS, 393 tests/67 suites, zero failures/errors/skips, 7m36s, finished 2026-10-04T17:14:15+07:00, all 44 pools closed cleanly. Exact V25 still validates 36 production entities with Flyway disabled and ddl-auto=validate. Source/diff/docs review and git diff --check passed; see 5E closure for whole-phase status.
