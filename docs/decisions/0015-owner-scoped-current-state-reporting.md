# ADR 0015 — Owner-scoped current-state Reporting

Status: Accepted for user-approved Phase 6, 2026-10-04. Implementation gates remain separate.

## Context

ADMIN needs a cross-domain operational dashboard and five report families for the local campus project. Reporting must not become a back door into foreign persistence or expose account credentials/contact data. Finance reversal, retained membership and deactivated inventory make naive joins or historical interpretations incorrect.

## Decision

Owners expose typed application query contracts. Owner infrastructure performs SQL only over owner tables, including owner-local joins and payment preaggregation. Reporting composes these contracts without SQL or foreign persistence imports. Cross-domain references remain UUIDs; frontend can resolve them through authorized existing APIs.

Reporting uses one read-only REPEATABLE_READ transaction per response/export. All owner reads share its PostgreSQL snapshot and common time boundary. Reports describe current recorded state; timestamps do not provide a historical as-of ledger. No duplicated reporting tables, refresh job, broker, cache or distributed read model is introduced.

Counts and VND sums remain exact. Only RECORDED receipts contribute to effective payments; collectible outstanding excludes CANCELLED charges. Physical occupancy includes ASSIGNED beds even if their parent is later inactive, whereas available inventory requires the complete ACTIVE parent chain. OPEN Library loans due strictly before the report instant are overdue; returned loans never count as currently overdue.

ADMIN-only versioned JSON endpoints and bounded CSV are separate projections of the same owner queries. CSV must quote delimiters/newlines/quotes and neutralize spreadsheet formulas; reject oversized exports explicitly. Do not export secrets, arbitrary JSON metadata or private contact fields. No third-party integration is introduced without a concrete approved use case.

## Consequences

Owners may use the single SQL-agnostic shared ReportJdbc helper for bound filters/count/limit mechanics; it has no tables or domain eligibility logic. ModuleBoundaryTest allows precisely that technical class and continues rejecting other foreign infrastructure. Typed row projections and owner-provided fixed columns prevent arbitrary SQL/field selection. 6C query review uses owner-local LATERAL receipt aggregation for selective debt pages, while the whole-campus dashboard retains full preaggregation. Safe synchronous request correlation/JSON completion events and bounded Micrometer tags do not add a public metrics endpoint or distributed infrastructure.

Reporting has no schema ownership for this initial read-only slice. Existing V1–V25 remain immutable; future measured index changes require new migrations. Long scans consume a database connection/snapshot and must be measured and bounded. Stable identifiers enable authorized frontend composition without inventing a shared person module or foreign joins. A future historical dashboard requires separately designed retained facts and cannot be inferred from these current tables.
