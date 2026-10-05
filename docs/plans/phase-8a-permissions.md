# Phase 8A2 — Functional campus permissions

Scope approved by the owner on 2026-10-05: replace a five-group proposal with function-specific administration for all implemented services. Implementation starts after the 8A1 bootstrap gate passes.

## Role matrix

ADMIN is the global administrator and the only role allowed to create accounts, replace roles, change account status or reset passwords. USER retains its existing personal inbox/Event capabilities. Functional roles never imply ADMIN.

| Role | Main access | Required reference reads |
| --- | --- | --- |
| ORGANIZATION_ADMIN | Organization administration | None |
| STUDENT_ADMIN | Student profiles | Organizations and Identity account references |
| PERSONNEL_ADMIN | Faculty/Staff profiles | Organizations and Identity account references |
| ACADEMIC_ADMIN | Programs/courses/terms/offerings/sections/enrollment | Organizations, Students, Faculty/Staff |
| DORMITORY_ADMIN | Inventory and current accommodation/history | Students |
| FINANCE_ADMIN | Fees, charge snapshots, manual receipts/reversal | Students |
| NOTIFICATION_ADMIN | Templates/drafts/publication | Identity account references |
| EVENT_ADMIN | Event catalog, membership and attendance | Students |
| LIBRARY_ADMIN | Titles/copies/borrowing/return | Students |
| AUDIT_VIEWER | Read selected-source audit | None; the existing audit UI uses UUID inputs |
| REPORTING_VIEWER | Dashboard/reports/CSV | Students; section, bed, copy and Event list/UUID detail |

Existing reference APIs return owner DTOs; granted GET access is explicit, not a claim of field-level redaction. Methods other than GET/HEAD on reference owners are denied. Audit/report viewers cannot mutate any owner or manage Identity accounts. Unlisted ADMIN paths remain global-ADMIN-only. Personal ownership rules stay unchanged.

## Implementation and verification

- Add immutable corrective V26 role seeds; preserve V1–V25. No hierarchy/multi-campus/schema tenancy is implied.
- Enforce method/path permissions in backend security; keep account/role APIs ADMIN-only. Test every role's allowed owner, denied owners, denied identity mutations and denied audit/report viewing for functional operators. Test anonymous/USER denial and global ADMIN regression.
- Adapt frontend role-based navigation and direct route boundaries; reference reads do not automatically authorize an owner administration page. User administration offers only actual role codes and explains multiple roles combine access.
- Verify role assignment/replacement and sessions under the accepted token strategy, plus exact V25→V26 Flyway/Hibernate validation. Update generated contract provenance and CI backend pin only after actual contract export/review.
- Provide two global ADMIN and at least one fixture account per functional/viewer role. Verify real login and forbidden direct requests; do not rely on hidden menus.

## Limits

Permissions are function-based campus roles for currently implemented APIs. Per-organization row scoping, confidential-field redaction, multi-level financial approvals, event budgets/partners and delegation periods have no approved domain model yet. These cannot be advertised as implemented. No public registration or automatic administrator provisioning is added.

Status: PASS for 8A2; see [review](../reviews/phase-8a2-permissions-review.md) for backend, UI, contract and real-browser evidence. Docker setup, substantial data inventory and whole Phase 8 acceptance/regression remain incomplete.
