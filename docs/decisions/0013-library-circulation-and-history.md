# ADR 0013 — Library circulation and retained history

Status: Accepted, 2026-10-04. Implements approved Phase 5C rules; verification status is recorded separately in the review.

## Context

The local personal project needs a small catalog and physical-copy circulation registry. ADMIN records loans and returns. It does not need reservations, renewals, lost-book fees, ISBN verification or a payment gateway.

## Decision

Library owns titles, copies, loans and audit. A copy retains its title association. One OPEN loan is allowed per copy, protected by a partial unique PostgreSQL index and refreshed application locks. Borrow locks title then copy; return uses title then copy then loan. Parent deactivation and admission therefore serialize without erasing outstanding loans. Title-first serialization is intentionally conservative; no high-throughput claim is made.

Eligibility reads ACTIVE Student through its owner application's fresh projection. Library stores scalar UUID references and does not import Student persistence. Foreign Student changes remain decision-time eligibility, without cross-module write locks.

The server records UTC instants and dueAt=borrowedAt+14 elapsed days. Returning an OPEN loan retains identifiers, borrowed/due/creation times, sets returnedAt and increments its optimistic version. It remains allowed after reference deactivation or overdue. Next borrowing uses a new history row, without rewriting the returned row. Dates and actors cannot be chosen by client payloads.

Six ADMIN mutations write status-only audit atomically. Audit insertion failure rolls back business changes and versions. JSONB remains an object without an invented length limit. Catalog search and loan filters are bounded and have explicit sorts/UUID tie-breakers.

## Consequences

V25 adds only Library-owned tables; V1–V24 remain immutable. Historical V24 validation freezes its 32-entity scan before adding Library. The Library gate requires all 36 production entities validated on a populated database upgraded exactly V24→V25, with Flyway disabled and ddl-auto=validate. History is retained without scheduled deletion. No Finance/Notification coupling or Student self-borrow is introduced.
