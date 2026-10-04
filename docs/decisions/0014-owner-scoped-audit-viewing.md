# ADR 0014 — Owner-scoped retained audit viewing

Status: Accepted, 2026-10-04. Verification is recorded separately in the 5D review.

## Context

The approved local Phase 5 requires ADMIN to read retained history from eight owners. Identity/People have VARCHAR metadata and no resource version; newer sources record JSONB/status/version. Merging independently paginated sources would produce incorrect global ordering/counts, and reading foreign tables from an aggregator would violate module ownership.

## Decision

Use one selected source per request. Each owner exports an application AuditReadPort specialization and implements it over only its own table. Shared contracts define a bounded filter and safe recorded-field DTO; AuditViewingService routes to those contracts without SQL or persistence imports.

Read-only REPEATABLE_READ owner transactions make count and page share a snapshot despite concurrent audit writes. Sorting uses occurredAt with a UUID ascending tie-breaker. Filters are bound parameters, source/resource policies are explicit, and tables/projections are fixed owner constants.

Keep historical versions null where they were never recorded; USER is an API label for Identity's target user. Identity/People metadata always projects empty because no additional historical key is approved. JSONB owners select only a string status and allow values specific to that resource; arbitrary request bodies, secrets, nested data and unknown values never appear in the response. This is a safe projection, not a schema constraint or metadata length limit. Stored audit rows are unchanged.

## Consequences

ADMIN can query/get all eight sources without a new schema, cross-source union, history reconstruction, mutation, export or expiry job. Existing target indexes are reviewed on selective fixtures. Unfiltered history/actor-only queries may scan; no load/latency guarantee is claimed. A truly merged timeline would require a separately approved read model with real global pagination semantics.
