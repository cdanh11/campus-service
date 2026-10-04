# Phase 5D — Read-only ADMIN Audit Viewing

Approved on 2026-10-04: ADMIN reads retained audit history; no deletion or automated expiry. Begin implementation only after Library 5C review PASS. No query API is implemented by this plan.

## Actual historical schemas

- Identity owns identity_admin_audit_events: target_user_id, actor, action, occurred_at and VARCHAR(1000) metadata; no resource_version column.
- Shared people-registry audit owns people_registry_audit_events: resource_type/target_id, actor, action, occurred_at and VARCHAR(1000) metadata; no resource_version column.
- Academic, Dormitory, Finance and Notification own their respective audit tables, including resource_version and JSONB metadata. Event and Library audit shape must be inspected after implementation.

Do not backfill or invent missing historic version values, claim VARCHAR metadata was constrained as JSONB, or read another module's tables from an audit aggregator. Any JSON parsing/sanitization must have deterministic handling of valid legacy text and malformed direct-SQL fixtures without leaking private content or causing the whole page to fail.

## Proposed query contract

Use a selected source per request with domain-owned application query ports and one shared bounded DTO/filter contract. This avoids pretending separately paginated sources form a correct globally ordered page. ADMIN API provides get by source/id and list by source with optional target/actor/action/resource/time filters as supported by that source. Count and page remain in the owner database query, stable occurredAt/id order; bound page, size, offset and validate all source-specific fields. Missing source/event yields safe errors. Versions are nullable only for sources that never recorded them.

Responses expose event identity, source, resource, target, actor UUID, action, optional recorded version and time. Metadata uses an explicit safe projection of approved historical keys; do not expose passwords, credentials, tokens or arbitrary request payload. No audit mutation, expiry, export, unbounded cross-source aggregation or reconstruction from current entity values. Existing business audit recording remains unchanged.

## Gate

ADMIN versus USER/anonymous checks for every route; real rows across every source, historical no-version behavior, safe metadata, invalid filters/ranges/offsets, exact time boundaries, actual equal-time UUID pages/counts and missing records. ModuleBoundaryTest plus source review of SQL ownership; assert queries do not write history. Review selective query plans before adding a justified new index; any new index needs its own migration/upgrade verification, never alter old migrations. Run focused and full clean verify, review diff and documentation, then Phase 5E requirement-by-requirement closure across Phase 1–5.
