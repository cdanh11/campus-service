# ADR 0017 — Functional administration permissions

## Status

Accepted and verified PASS (Phase 8A2); whole Phase 8 remains incomplete.

## Context

The university demo needs distinct operators rather than one all-powerful account per department. Existing modules define the available business operations; this change does not introduce new university workflows.

## Decision

Keep ADMIN as global administrator and USER as the personal-portal account. Add ORGANIZATION_ADMIN, STUDENT_ADMIN, PERSONNEL_ADMIN, ACADEMIC_ADMIN, DORMITORY_ADMIN, FINANCE_ADMIN, NOTIFICATION_ADMIN, EVENT_ADMIN and LIBRARY_ADMIN. Add read-only AUDIT_VIEWER and REPORTING_VIEWER. Multiple roles combine grants. Only ADMIN manages accounts, passwords, statuses and role assignments. The final-active-global-admin invariant is unchanged.

Enforce path and HTTP-method grants in Spring Security; unmatched administrator paths remain ADMIN-only. UI navigation and route boundaries mirror function grants but are not security enforcement. GET/HEAD reference reads support existing selectors; other methods require the owning operator role. See the [permission matrix](../permissions.md).

Released V1–V25 are immutable. V26 inserts role catalog entries without altering existing memberships. The real V25→V26 upgrade must preserve data and validate production JPA entities with Flyway disabled and ddl-auto=validate against that exact database.

## Consequences

These are function-level roles, not organization-scoped rows. A Student administrator can operate all Student records. Reference endpoints retain their existing DTO fields and paging/search; this change does not provide field-level redaction. Audit viewers can inspect retained audit data across owners, not raw owner records. Reporting viewers see aggregate/dashboard/report data and explicit selector references.

Existing stateless access JWTs retain their role claims until expiry (maximum 15 minutes). Role replacement uses the existing security-version/session revocation transaction; new login/refresh reflects current roles. This does not claim immediate invalidation of issued JWTs.

## Alternatives considered

Five broad roles would combine unrelated Event, Library and Notification operations. Per-endpoint database ACLs and per-unit row scope would require new policy and owner contracts beyond this approved demo slice.

## Review conditions

Revisit for departmental row isolation, sensitive field redaction, split create/update/approve permissions, budget approval, delegated role management or immediate access-token revocation.
