# ADR 0018 — Department-based functional roles

## Status

Accepted. Supersedes [ADR 0017](0017-functional-administration-permissions.md).

## Context

ADR 0017 introduced eleven roles shaped around individual owner endpoints: four people/catalog administrators, four campus-service administrators and two read-only viewers. The grants were correct, but the model described screens rather than jobs. An operator assigned to a library shift could not also answer a dormitory request without a second account, while a graduation-project reviewer needed to understand eleven nearly identical role descriptions to defend the design.

The approved requirement is a role that a person can hold for a full working day: several related duties inside one department, with clear boundaries against other departments and against platform administration.

## Decision

Keep `USER` and global `ADMIN`, and replace the nine operators and two viewers with four department roles:

| Role | Real-world counterpart | Day-to-day scope |
| --- | --- | --- |
| `ACADEMIC_AFFAIRS` | Phòng Đào tạo | Organization units, Student and Faculty/Staff profiles, programs, courses, terms, offerings, sections, enrollments, timetable |
| `CAMPUS_SERVICES` | Phòng Công tác sinh viên và dịch vụ | Dormitory inventory and stays, notifications, events and attendance, library catalog and circulation |
| `FINANCE` | Phòng Tài chính | Fees, Student charges, manual receipts, reversals and cancellations |
| `AUDITOR` | Kiểm soát nội bộ | Read-only retained audits, current-state reports/CSV and approved reference lists |

`ADMIN` remains the platform-administration role: accounts, passwords, statuses, role assignment, organization units as a break-glass path, and every owner API. `USER` remains the personal portal. Only `ADMIN` assigns roles.

Access is still enforced at the HTTP boundary by path and method, not by data ownership. GET/HEAD uses reader grants and other methods use writer grants; unmatched `/api/v1/admin/**` paths stay `ADMIN`-only. `ACADEMIC_AFFAIRS` and `CAMPUS_SERVICES` may read the account list/detail to link profiles. `CAMPUS_SERVICES`, `FINANCE` and `AUDITOR` may read the Student registry for eligibility and reconciliation. `AUDITOR` additionally reads section, bed, copy and event list/UUID detail, but not loans, registrations, inboxes, accounts or finance.

Memberships are migrated in place by V28. The mapping copies every old operator to its department and both viewers to `AUDITOR`, deduplicates memberships, then removes the old role rows. `USER`, `ADMIN`, accounts, history and business tables are untouched. The migration is forward-only; released migrations remain immutable.

The React client mirrors the same six roles: it groups navigation by department, splits the former "Ký túc xá & tài chính" entry into "Ký túc xá" and "Tài chính", and blocks unrelated direct routes before owner requests. UI visibility is convenience, never enforcement.

## Consequences

The model is defensible as an organizational design rather than an endpoint list. A campus-services operator can cover reception, dormitory, library, events and announcements during one shift; an academic-affairs operator can follow a cohort from profile through enrollment; a finance operator handles the complete charge/payment lifecycle; an auditor can inspect operations without any write path.

Least privilege becomes coarser inside a department: separate Library and Dormitory staff now share `CAMPUS_SERVICES`, and an academic operator who only maintains the catalog also receives Student and personnel writes. This is accepted for a single-campus demo. Row-level isolation, field redaction, approval chains and delegated role administration remain out of scope. An existing database migrated to V28 keeps old demo accounts whose memberships now point at department roles; a reseed adds the new `demo.academic_affairs@`, `demo.campus_services@`, `demo.finance@` and `demo.auditor@` accounts.

Stateless access JWTs issued before the upgrade may still carry removed role names until they expire (maximum 15 minutes); they authorize nothing because no current rule matches them. Refresh sessions are revoked by the existing security-version transaction. New login/refresh reflects current roles.

## Alternatives considered

Keeping eleven roles was rejected because the artificial split is harder to explain and does not match any real shift. Collapsing every operator into `ADMIN` was rejected because it destroys the least-privilege demonstration. Splitting `CAMPUS_SERVICES` into dormitory/library/event sub-roles was rejected for the same granularity problem. Adding per-unit ACL rows was deferred because it requires new policy tables, owner contracts and query scoping beyond this project slice.

## Review conditions

Revisit when the campus needs per-unit data isolation, sensitive-field redaction, separate create/update/approve grants, delegated role management, or immediate access-token revocation.
