# ADR 0006: Academic Mutation Audit

## Status

Accepted within approved Phase 3D hardening; verification is tracked in its phase plan.

## Context

Academic catalog, delivery and enrollment administration changes operational data across multiple resources. Existing Identity and People Registry audit tables have domain-specific constraints and must not be repurposed by editing released migrations or reading their persistence internals.

## Decision

Academic owns `academic_audit_events`, added by V18. Administrative application entry points require an actor UUID supplied from the validated JWT principal. Each successful HTTP create/update writes one event in the same transaction as the business mutation. Audit failure aborts the mutation and returns a safe 500 `AUDIT_WRITE_FAILED` envelope. Reads and rejected operations write no success events.

Events store UUID, actor FK to Identity, resource type/UUID, action, resulting row version, occurrence time and JSONB object metadata containing the resulting lifecycle status. Actions are CREATED/UPDATED plus WITHDRAWN/REENROLLED for enrollment. Target is polymorphic and has no target FK; the application obtains the identifier from the actual saved resource. No names, contact details, credentials, tokens or unrestricted request payload are recorded. There is no JSONB length limit; metadata must be an object.

Internal domain/application provisioning and fixture entry points remain available without audit, as in the existing People Registry pattern. HTTP controllers always route mutations through the audited application facade; clients cannot choose to skip audit or supply another actor. There is no audit query endpoint, history backfill, retention/deletion job or claim of database-enforced immutability in 3D.

## Consequences

- Failure cannot leave committed business data without its corresponding audit event.
- Audit owns one new persistence mapping, while modules communicate through application contracts.
- Audit introduces a write/FK dependency on the actor Identity row. JWT authorization remains the documented token policy; an absent actor row cannot silently bypass audit.
- Resource/type/time and actor/time indexes support future authorized audit queries without exposing a new interface now.
- History starts with deployment of V18; historical mutations are not reconstructed.

## Alternatives Considered

- Reuse People Registry audit: rejected because its resource constraints and ownership are distinct.
- Async audit after commit: rejected because loss/failure would permit successful unrecorded mutations.
- Database triggers: deferred because the actor is an authenticated application principal and a domain-level audit policy is sufficient for supported writes.
- Full request snapshots: rejected because they add unnecessary private data and schema coupling.

## Review Conditions

Revisit for approved audit querying/retention, external integrations, stronger database immutability or operational load requiring a new reliable write strategy. Preserve transaction rollback, security and migration evidence.
