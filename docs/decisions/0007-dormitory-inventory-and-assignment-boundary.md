# ADR 0007: Dormitory Inventory and Assignment Boundary

## Status

Accepted for Phase 4A1 implementation. The user approved the current-place assignment direction for 4A2 on 2026-10-04; allocation is not implemented by this slice.

## Context

Dormitory needs inventory before accommodation assignment. Reusing Academic sections or Student persistence would blur ownership and make later occupancy rules difficult to review.

## Decision

Dormitory owns separate building, room and bed tables. A room has one immutable building UUID; a bed has one immutable room UUID. Codes are case-insensitively unique globally for buildings, within a building for rooms, and within a room for beds. Each item has a label, ACTIVE/INACTIVE status, timestamps and optimistic version. Do not invent a second room capacity/occupancy counter or additional location/gender rules.

ADMIN HTTP create/update must record a synchronous Dormitory-owned audit event with JWT actor, target/type/action/resulting version/time and status-only JSON object metadata. Audit failure rolls back the business write. There is no polymorphic target FK, audit read API, JSON length limit, historical backfill or DB-enforced append-only claim.

Inventory mutations lock building → room → bed. Active child creation/activation requires active ancestors; parent deactivation refuses active immediate children. Deactivate descendants explicitly before a parent; do not cascade historical status. Direct SQL is not the supported lifecycle interface.

Phase 4A2 will add one current ASSIGNED allocation per Student and per bed. RELEASED frees the bed while preserving history. No future interval booking. Student eligibility must use a Student application contract. Occupied-bed/deactivation safeguards and allocation locking must be implemented and verified together before assignments are exposed.

Finance owns obligations and manual payment records independently. There is no automatic billing integration or external payment gateway in 4A1.

## Consequences

- Locks and exact upgrade tests keep ancestor availability and version behavior reviewable, at the cost of serializing inventory mutations within a building.
- The fixed resource HTTP route parameter reuses validation/query code only for three structurally identical inventory types; table/entity selection is an internal enum allowlist. Clients cannot select arbitrary tables or move parents through PUT.
- Cross-module references are limited to the approved audit actor FK at this stage.
- Historical V18 validation freezes its production entity scan; V19 validates the upgraded inventory and audit mappings.

## Alternatives Considered

- Single polymorphic inventory table: rejected in favor of explicit building/room/bed foreign keys.
- Automatic parent status cascades or an occupancy counter: rejected because they hide separate lifecycle/history decisions.
- Combining inventory, allocation and finance in one release: rejected because each has distinct concurrency invariants.

## Review Conditions

Revisit for approved future booking, room eligibility rules, occupancy changes, direct integrations or measured load requiring narrower locks. Preserve audit atomicity, immutable parents and migration evidence.
