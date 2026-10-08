# Administration permission matrix

Phase 8A2 implementation: functional roles across existing owner APIs. This is not a complete university HR/approval hierarchy. Verification is [PASS for 8A2](reviews/phase-8a2-permissions-review.md); whole Phase 8 subsequently passed its [local acceptance/regression review](reviews/phase-8-acceptance-review.md).

| Role | Own operations | Additional read references |
| --- | --- | --- |
| ADMIN | All owner APIs, account/password/status/role management | All |
| ORGANIZATION_ADMIN | Organization units | None |
| STUDENT_ADMIN | Student profiles | Organization units; account list/detail |
| PERSONNEL_ADMIN | Faculty/staff profiles | Organization units; account list/detail |
| ACADEMIC_ADMIN | Programs, courses, terms, offerings, sections, enrollments | Organization units, Students, faculty/staff |
| DORMITORY_ADMIN | Buildings, rooms, beds, assignments/releases | Students |
| FINANCE_ADMIN | Fees, charges, receipts/reversals | Students |
| NOTIFICATION_ADMIN | Templates, notices, publication/deliveries | Account list/detail |
| EVENT_ADMIN | Events, memberships, cancellation/restoration, attendance | Students |
| LIBRARY_ADMIN | Titles, copies, borrowing/returns | Students |
| AUDIT_VIEWER | GET/HEAD retained audit APIs only | None |
| REPORTING_VIEWER | GET/HEAD dashboard/reports/CSV only | Students; section, bed, copy and event list/UUID detail |
| USER | Own inbox and Event portal | No administrator APIs |

Reference grants never authorize writes. Account references exclude password/role/status mutations and future non-UUID subroutes. Report references do not grant access to raw loans, Event registration management, notifications or audit. Existing reference DTOs are unchanged; operators can read their exposed fields across all units. Multiple assigned roles combine access. Only global ADMIN assigns roles; scoped administrators cannot elevate themselves or others.

The frontend hides unrelated menus and blocks direct routes before fetching owner data. Reference permissions do not expose the owner's management UI. Backend authorization remains authoritative for direct HTTP clients.

Role changes retain the documented stateless JWT lifetime; session/refresh invalidation does not revoke already issued access JWTs immediately. See [ADR 0017](decisions/0017-functional-administration-permissions.md).

## Mapping university responsibilities

Roles represent capabilities, not organization-unit membership. Assign only the capabilities needed by each operator; do not grant ADMIN just because someone manages a department. Typical combinations are:

| Responsibility | Suggested roles |
| --- | --- |
| Organization/reference-data administration | ORGANIZATION_ADMIN |
| Student affairs and student communication | STUDENT_ADMIN, NOTIFICATION_ADMIN |
| Personnel/HR administration | PERSONNEL_ADMIN |
| Training/Academic administration | ACADEMIC_ADMIN |
| Accommodation administration | DORMITORY_ADMIN |
| Finance office | FINANCE_ADMIN |
| Event coordination | EVENT_ADMIN, optionally NOTIFICATION_ADMIN |
| Library circulation | LIBRARY_ADMIN |
| Internal audit | AUDIT_VIEWER |
| Management reporting | REPORTING_VIEWER |
| Platform account administration | ADMIN |

These are assignment examples, not automatic department-to-role grants. Event coordination does not imply Finance access; assign FINANCE_ADMIN separately only when that person is authorized to record charges/receipts. A Student affairs operator does not gain Academic or account-management writes through Student reference permissions.

This covers the implemented campus capabilities. Procurement, payroll, admissions, approval chains, per-department data isolation and event budgets are not implemented, so no role claims access to those workflows. The two global administrators are demo inventory, not a hard-coded limit on ADMIN accounts.
