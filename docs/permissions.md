# Administration permission matrix

Phase 8A2 implementation: functional roles across existing owner APIs. This is not a complete university HR/approval hierarchy. Verification is [PASS for 8A2](reviews/phase-8a2-permissions-review.md); whole Phase 8 remains incomplete.

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
