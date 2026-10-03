# ADR 0009: VND Obligations and Immutable Fee Snapshots

## Status

Accepted for Phase 4B1 implementation. User selected VND and manual payments on 2026-10-04; payments remain a separate 4B2 gate.

## Context

Financial values must remain exact. Historical obligations must not silently change when an administrator changes the fee definition or deactivates a Student.

## Decision

Finance owns fee definitions, Student charges and Finance audit. Currency is VND, positive integer values from 1 to 9999999999999999999. Java uses BigDecimal with scale normalization and RoundingMode.UNNECESSARY. PostgreSQL uses NUMERIC without a declared scale, with integer and finite range CHECKs: typed NUMERIC(p,0) could round fractions before a CHECK evaluates. No float, FX or payment gateway.

Charges have unique normalized administrator-supplied numbers, immutable Student/fee UUIDs, code/name/amount/currency snapshots and dueDate. ACTIVE fee and ACTIVE Student are required at creation, through the Student application contract. Old obligations retain their snapshots when fees change. Past due dates support recording existing obligations; no overdue job/status.

Fees are ACTIVE/INACTIVE and mutable with expectedVersion. Charges are OPEN/CANCELLED; cancellation is terminal and versioned, possible after reference deactivation. No deletion, arbitrary amount override, automatic Academic/Dormitory billing or charge editing.

Creation locks fee before snapshotting; fee mutations use that lock. Cancellation refreshes the charge with PESSIMISTIC_WRITE. Unique indexes protect case-insensitive numbers/codes. Persistence flushes/refreshes stored records before returning. Student status is checked at the decision without cross-module persistence locks.

All supported writes require an actor and share the transaction with synchronous status-only Finance audit. Audit resource/action compatibility permits FEE CREATED/UPDATED and CHARGE CREATED/CANCELLED. Audit failure rolls back the business mutation/version; no polymorphic target FK, invented JSON length cap or public audit query endpoint.

## Consequences

- Existing charges remain meaningful and exact independently of current fee configuration.
- Conservative fee locking serializes creation against fee changes; no load/latency claim.
- Historical V20 validation freezes its original 21-entity set; V21 includes 24 production entities.
- 4B2 must serialize payment/reversal/cancellation on the charge lock and prohibit cancellation while net payments remain; this is not claimed as implemented by 4B1.

## Revisit When

Multicurrency, discounts/installments, billing automation, accounting integration or measured contention becomes an approved requirement. Preserve exact values and historical snapshots.
