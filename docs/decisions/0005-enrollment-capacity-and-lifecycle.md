# ADR 0005: Enrollment Capacity and Lifecycle

## Status

Accepted for Phase 3C implementation. Final verification is tracked separately in the phase plan.

## Context

Two administrative enrollment requests may compete for the last section seat. Independent counts followed by inserts can overbook under READ COMMITTED. Existing Academic delivery mutations already lock term → offering → section. Student profile ownership must remain in the Student module.

## Decision

Academic owns one enrollment record per Student/class-section pair. PostgreSQL enforces pair uniqueness, references, required fields, ENROLLED/WITHDRAWN status and nonnegative version. ENROLLED consumes capacity; WITHDRAWN releases it. Records are retained, without physical deletion. The user approved re-enrollment on the same record with expectedVersion on 2026-10-03.

All supported enrollment writes acquire term → offering → section locks before checking occupancy and refreshing/locking an existing enrollment. Count ENROLLED records inside that transaction; do not maintain a second seat counter. New and restored enrollment requires an ACTIVE Student plus ACTIVE term and OPEN offering/section. Student eligibility uses its application service. Withdrawal remains possible after parent closure or Student deactivation. Section closure retains enrollments as historical membership; it does not automatically withdraw students.

Student eligibility is checked at the operation decision, without cross-module persistence locks. Subsequent Student deactivation does not invalidate historical membership. Active term status controls admission; calendar registration windows are not implemented in this phase. Academic catalog/organization/faculty deactivation does not independently rewrite or withdraw memberships in an already open section.

Only ADMIN HTTP operations are exposed. Student self-service and its authorization policy require a later explicit scope. Duplicate POST is a conflict; lifecycle updates use PUT with expectedVersion, and repeating the current status is a conflict.

## Consequences

- Supported concurrent operations cannot exceed section capacity or create duplicate membership. Direct SQL writes are outside the supported enrollment interface and are not protected by the application capacity check.
- Parent locking serializes mutations within a term, preserving the existing lock order and preventing admission after a winning parent closure. This conservative strategy is acceptable for the local project baseline; no load benchmark is claimed.
- Hibernate persistence contexts must refresh enrollment state after locks, before comparing expectedVersion, so stale cached state cannot drive lifecycle checks.
- There is no waitlist, course-level duplicate rule across multiple sections, automatic capacity increase, enrollment history event stream or completion/grade policy in 3C. Broader mutation audit belongs to 3D.

## Alternatives Considered

- Unlocked count/insert: rejected because it permits overbooking.
- Separate occupied-seat counter: deferred because it introduces another mutable invariant and recovery requirement.
- Enrollment triggers or a distributed lock: deferred because supported writes run in one PostgreSQL-backed application with an established lock order.
- Reading Student tables or locking its persistence entity: rejected because it violates module ownership.

## Review Conditions

Revisit when load measurements justify narrower locking, Student deactivation needs atomic admission coordination, registration windows or course-level eligibility are approved, self-service is introduced, or mutation history becomes part of the approved audit scope. Preserve upgrade tests and transaction/concurrency evidence when changing the strategy.
