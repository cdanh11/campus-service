# ADR 0012 — Event Membership Ownership and Capacity

Status: Accepted business/architecture decision, 2026-10-04. Implementation verification is tracked separately in the Phase 5B review.

## Context

Campus Event requires ACTIVE Student admission, bounded capacity, retained cancellation and ADMIN attendance. Users explicitly chose linked-account Student self-service plus ADMIN operation, same-record restoration with expectedVersion and audit history, and OPEN-only admission with manual ADMIN closure. Event dates must not silently impose a registration cutoff.

## Decision

Event owns catalog, membership and mutation-audit tables. A retained unique (event_id, student_id) membership is REGISTERED, CANCELLED or ATTENDED. Cancellation frees capacity while preserving identity/creation history; restoration reuses its UUID, records latest registeredAt, clears current cancellation time and atomically writes RESTORED audit. ATTENDED is terminal and continues to consume a seat. Cancel remains permitted after event closure/cancellation or Student deactivation; attendance is ADMIN-only on OPEN/CLOSED. No physical deletion, waitlist or automatic bulk membership rewrite on event cancellation.

Admission/restoration requires OPEN and ACTIVE Student at the operation decision; startsAt/endsAt describe the event, not an implicit admission window. ADMIN manages any Student, including profiles without Identity accounts. Self-service derives its Student UUID only through StudentAccountDirectory's current Identity link; unknown/foreign membership is not exposed. Owner writes re-evaluate the link after waiting for the Event lock. Student owns a scalar UUID/status projection so cached profile entities cannot retain stale eligibility. No foreign repository/table access or cross-module locks.

All mutations acquire the Event write lock first, then the membership lock when applicable, refreshing managed state before optimistic-version decisions. Membership operations and catalog capacity reductions count REGISTERED/ATTENDED rows under that same Event lock. Unique membership protects duplicate rows; aggregate capacity is enforced by supported application transactions, not a cross-row SQL CHECK or direct-SQL guarantee. Catalog and membership HTTP expectedVersion values are strict nonnegative JSON integers. Each mutation synchronously records trusted actor, target/action/version/time and status-only JSON metadata; failed audit insertion rolls back the complete business transaction.

Queries stay in Event ownership, use bounded database count/page/filter and allowlisted sorts with UUID tie-breakers. Catalog reads are authenticated, including DRAFT rows; own membership reads never accept a client-selected Student identity. ADMIN audit viewing is a later read-only slice, not an implicit Event feature. New V24 is finalized with genuine V23→V24 data/history/schema validation and Hibernate validate against the exact upgraded schema, with Flyway disabled during that validation.

## Consequences and limits

Same-Event admission/restore/cancel/attendance/capacity updates serialize while independent events remain independent. ACTIVE status and link eligibility are decision-time checks; unrelated registry changes are not frozen by a distributed or cross-module lock. SQL bypasses are outside the supported audited API. No fees, ticket provider, automatic Notification integration, scheduler or load benchmark is introduced. Delivered V1–V23 remain immutable; historical V23 validation freezes its 29 entity packages before Event adds three entities.
