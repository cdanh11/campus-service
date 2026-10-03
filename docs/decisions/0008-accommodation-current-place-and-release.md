# ADR 0008: Accommodation Current Place and Release

## Status

Accepted for Phase 4A2 implementation, extending ADR 0007 and the user's current-place/history choice on 2026-10-04.

## Context

Bed locks alone cannot stop the same Student taking two beds in different buildings. Release history also must not disappear when a bed is reused.

## Decision

Dormitory owns immutable assignment identity, Student/bed UUIDs, ASSIGNED/RELEASED, version and assignment/release/history timestamps. Partial unique indexes enforce one ASSIGNED row for each Student and bed. Released records are terminal; a new stay creates a new UUID. No date reservation, transfer, delete or billing integration.

Admission requires ACTIVE Student through the Student application service and ACTIVE building/room/bed. Release is allowed after Student deactivation. Eligibility is checked at the decision, without cross-module persistence locking; deactivation does not rewrite an existing assignment.

Lock order is building → room → bed → assignment. Inventory bed deactivation rejects current assignments under that same bed lock. Distinct buildings can admit concurrently, but the current-Student unique index atomically rejects the losing transaction. No occupancy counter.

Assignment/release and status-only Dormitory audit commit together. V20 extends resource/actions with a compatibility CHECK: inventory permits CREATED/UPDATED and assignment permits ASSIGNED/RELEASED. Missing audit persistence rolls back occupancy, history/version and business data.

Persistence returns refreshed stored records after writes, keeping POST/PUT timestamps identical to later GET at PostgreSQL's actual precision.

## Consequences

- Database uniqueness complements application eligibility and locking; supported writes cannot create two current places.
- Conservative building locks serialize stays within a building. No throughput/latency claim.
- Foreign keys preserve Student/bed identity without external persistence queries.
- Historical V19 entity scan excludes new assignment mappings; exact V20 validation includes all 21 current production entities.

## Alternatives Considered

- Rely only on bed locks or a check-before-insert: rejected because different-bed same-Student races remain.
- Reuse released row: rejected because a new stay should retain a distinct history entry.
- Student repository locks or a shared person module: rejected to preserve module ownership.

## Review Conditions

Revisit for future-date booking, transfer, broader role/self-service access or measured lock contention. Preserve history, current-place uniqueness and atomic audit.
