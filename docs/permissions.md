# Administration permission matrix

The current model is six roles: two platform roles plus four department roles. It replaced the eleven function-specific roles of Phase 8A2; see [ADR 0018](decisions/0018-department-functional-roles.md) and the historical [ADR 0017](decisions/0017-functional-administration-permissions.md). Organization, data flows and Mermaid diagrams are in [Organization and data flow](architecture/organization-and-data-flow.md).

| Role | Real-world counterpart | Own writes | Additional reads |
| --- | --- | --- | --- |
| ADMIN | Quản trị hệ thống | All owner APIs; accounts, passwords, statuses and role assignment; organization units | All |
| ACADEMIC_AFFAIRS | Phòng Đào tạo | Organization units; Student profiles; Faculty/Staff profiles; programs, courses, terms, offerings, sections, enrollments and timetable | Account list/detail/lookup |
| CAMPUS_SERVICES | Phòng Công tác sinh viên và dịch vụ | Dormitory buildings/rooms/beds/assignments; notification templates/notices/publication; events, memberships and attendance; library titles/copies/loans | Student profiles; account list/detail/lookup |
| FINANCE | Phòng Tài chính | Fees, Student charges, manual receipts, reversals and cancellations | Student profiles |
| AUDITOR | Kiểm soát nội bộ | None | Retained audits; dashboard/reports/CSV; Student profiles; section, bed, copy and event list/UUID detail |
| USER | Sinh viên / người dùng | Own inbox acknowledgement; own Event registration and cancellation | Own timetable, enrollments and tuition charges through `/api/v1/me` |

Reference grants never authorize writes. Account references expose list/detail/lookup only, never password, role or status mutation and never future non-UUID subroutes. Auditor references stop at list/UUID detail: no loans, Event registrations, notification deliveries, accounts or finance rows. Report grants do not expose raw owner records.

Every department role operates all records of its domain. There is no per-building, per-program or per-fee row isolation, and no field-level redaction; existing owner DTOs are unchanged. Multiple assigned roles combine grants. Only global ADMIN assigns roles, so a department operator cannot elevate itself or another account.

## What one workday covers

| Department | A normal day |
| --- | --- |
| Phòng Đào tạo | Maintain units and staff, admit/update Student profiles, open a term, publish offerings and sections, place the timetable, enroll or withdraw students, answer cohort questions |
| Phòng Công tác sinh viên và dịch vụ | Allocate and release beds, publish a notice, open an event and take attendance, check out and return library copies, handle student requests across those services |
| Phòng Tài chính | Define fees, issue charges, record manual receipts, reverse a wrong receipt, cancel an obsolete charge and reconcile a Student balance |
| Kiểm soát nội bộ | Review retained mutations, inspect the dashboard and reports, export CSV, cross-check approved reference lists, raise findings without changing data |
| Quản trị hệ thống | Provision accounts, reset credentials, activate/deactivate accounts, assign department roles, maintain organization units, handle break-glass recovery |

This is an assignment model, not an approval chain. Event coordination does not imply Finance access: a person who needs both receives both department roles explicitly.

## Enforcement

Spring Security evaluates `/api/v1/admin/**` before controllers. GET/HEAD uses reader grants; other methods use writer grants. Any administrator path without a matching grant stays ADMIN-only. The filter returns 403 for an authenticated role without a grant and 401 without a token; controllers validate the payload only after the grant passes.

The React client hides unrelated menus and blocks direct routes before owner requests, but this is convenience only. Backend authorization is authoritative for direct HTTP clients and scripts.

## Migrating existing data

V28 maps memberships forward and then deletes the eleven released role rows:

| Released role | Replacement |
| --- | --- |
| ORGANIZATION_ADMIN, STUDENT_ADMIN, PERSONNEL_ADMIN, ACADEMIC_ADMIN | ACADEMIC_AFFAIRS |
| DORMITORY_ADMIN, NOTIFICATION_ADMIN, EVENT_ADMIN, LIBRARY_ADMIN | CAMPUS_SERVICES |
| FINANCE_ADMIN | FINANCE |
| AUDIT_VIEWER, REPORTING_VIEWER | AUDITOR |

USER and ADMIN memberships, accounts, passwords, sessions, business tables and audit history are unchanged. Duplicate memberships collapse, and the migration runs once through Flyway. Access JWTs issued before the upgrade may still name removed roles until they expire (maximum 15 minutes); they match no rule, and refresh/logout invalidation follows the existing session revocation. An existing demo database keeps any pre-existing demo accounts after migration, now attached to department roles; reseeding adds the four department accounts.

## Demo accounts

The portable demo creates two global administrators, one account per department and 200 linked Student users. Every administrator/department account uses the standard demo administrator password; Students use the Student password. The exact private email manifest is `.demo/docker/accounts.json`. Department emails are:

- `demo.academic_affairs@example.test`
- `demo.campus_services@example.test`
- `demo.finance@example.test`
- `demo.auditor@example.test`

## Not implemented

Procurement, payroll, admissions pipelines, approval chains, per-department row isolation, event budgets and delegated role administration are outside the implemented campus capabilities, so no role claims access to them. The two global administrators are demo inventory, not a hard-coded limit on ADMIN accounts.
