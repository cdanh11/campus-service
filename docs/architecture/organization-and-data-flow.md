# Organization and data flow

This document describes how Campus Platform maps a university into departments, how each department maps to one role, and how data moves between owners. The role grants are authoritative in [Permissions](../permissions.md) and [ADR 0018](../decisions/0018-department-functional-roles.md).

## Campus organization

```mermaid
flowchart TB
    Board["Ban Giám hiệu / University board"] --> Academic["Phòng Đào tạo<br/>Academic Affairs"]
    Board --> Services["Phòng Công tác sinh viên<br/>và dịch vụ<br/>Campus Services"]
    Board --> Finance["Phòng Tài chính<br/>Finance"]
    Board --> Audit["Kiểm soát nội bộ<br/>Internal Audit"]
    Academic --> Units["Đơn vị, hồ sơ sinh viên,<br/>giảng viên và nhân sự"]
    Academic --> Training["Chương trình, học kỳ,<br/>lớp học phần, thời khóa biểu"]
    Services --> Dorm["Ký túc xá"]
    Services --> Comms["Thông báo và sự kiện"]
    Services --> Library["Thư viện"]
    Finance --> Money["Học phí, khoản thu,<br/>biên nhận"]
    Audit --> Evidence["Audit, báo cáo, CSV"]
    Platform["Quản trị hệ thống<br/>ADMIN"] -.-> Identity["Tài khoản, vai trò,<br/>phiên đăng nhập"]
```

`ADMIN` is a platform function, not a fifth business department. It exists so accounts and roles can be provisioned and recovered; business staff receive a department role instead.

## Role to module ownership

```mermaid
flowchart LR
    subgraph AA["ACADEMIC_AFFAIRS"]
        A1["Organization units"]
        A2["Student profiles"]
        A3["Faculty / staff profiles"]
        A4["Academic catalog,<br/>delivery, enrollment,<br/>timetable"]
    end
    subgraph CS["CAMPUS_SERVICES"]
        C1["Dormitory"]
        C2["Notifications"]
        C3["Events"]
        C4["Library"]
    end
    subgraph FI["FINANCE"]
        F1["Fees and charges"]
        F2["Receipts, reversal,<br/>cancellation"]
    end
    subgraph AU["AUDITOR"]
        U1["Retained audits"]
        U2["Reports and CSV"]
    end
    subgraph AD["ADMIN"]
        D1["Accounts and roles"]
        D2["Every owner API"]
    end
```

A department owns every write in its boxes. It does not automatically own another department's box. Approved cross-reads are shown below; they are read-only.

## Cross-department reads

```mermaid
flowchart LR
    AA["ACADEMIC_AFFAIRS"] -->|"account list/detail"| IAM["Identity accounts"]
    CS["CAMPUS_SERVICES"] -->|"student eligibility,<br/>account linking"| STU["Student profiles"]
    CS --> IAM
    FI["FINANCE"] -->|"student eligibility"| STU
    AU["AUDITOR"] -->|"approved references"| STU
    AU -->|"sections, beds,<br/>copies, events"| REF["Owner reference lists"]
    AU -->|"read-only"| OBS["Audits and reports"]
    FI -.->|"denied"| DORM["Dormitory data"]
    AA -.->|"denied"| MONEY["Finance data"]
```

Denied arrows are deliberate: Finance cannot inspect dormitory stays, and Academic Affairs cannot post charges. A person who genuinely performs both jobs receives both roles.

## Data flows by department

### Academic affairs: profile to enrollment

```mermaid
sequenceDiagram
    participant AA as Academic Affairs
    participant AC as Academic service
    participant ST as Student contract
    participant DB as PostgreSQL
    AA->>AC: create term, offering, section
    AC->>DB: write catalog and timetable
    AA->>AC: enroll student in section
    AC->>ST: read active Student profile
    ST-->>AC: UUID and status
    AC->>DB: capacity check, lock order, retained enrollment
    AC-->>AA: refreshed version and status
```

The Student profile is owned by the Student module. Academic reads it through an application contract; it never imports Student tables. Withdrawal and restoration keep the same retained enrollment and re-check capacity.

### Campus services: accommodation, communication, events, library

```mermaid
flowchart LR
    Student["Active Student profile"] --> Dorm["Assign / release bed"]
    Student --> Event["Register, cancel, attend event"]
    Student --> Loan["Borrow and return copy"]
    Staff["CAMPUS_SERVICES operator"] --> Dorm
    Staff --> Event
    Staff --> Loan
    Staff --> Notice["Publish notification"]
    Notice --> Inbox["Recipient inbox deliveries"]
    Dorm --> Audit["Owner audit"]
    Event --> Audit
    Loan --> Audit
    Notice --> Audit
```

Dormitory, Event and Library each ask the Student contract for fresh eligibility at decision time. Notification resolves active account UUIDs through Identity and writes one inbox delivery per recipient. Each owner writes its own audit row synchronously; the audit module only reads those rows later.

### Finance: charge to balance

```mermaid
sequenceDiagram
    participant FIN as Finance
    participant FS as Finance service
    participant ST as Student contract
    participant DB as PostgreSQL
    FIN->>FS: create charge from active fee
    FS->>ST: read active Student profile
    ST-->>FS: UUID and status
    FS->>DB: immutable fee snapshot + charge
    FIN->>FS: record manual receipt
    FS->>DB: lock charge, check outstanding, write receipt
    FIN->>FS: reverse receipt or cancel charge
    FS->>DB: recompute balance and increment version
    FS-->>FIN: balance, effective payments, version
```

Finance never writes Academic or Dormitory data automatically. Dormitory fees are manual charges; there is no scheduler or payment gateway.

### Internal audit and reporting: read-only evidence

```mermaid
flowchart LR
    AU["AUDITOR"] -->|"GET/HEAD"| A["Retained audit APIs"]
    AU -->|"GET/HEAD"| R["Dashboard, reports, CSV"]
    AU -->|"GET/HEAD"| REF["Student, section, bed,<br/>copy, event references"]
    A -->|"owner query contracts"| O["Owner modules"]
    R -->|"owner query contracts"| O
    AU -.->|"no POST/PUT/PATCH/DELETE"| X["Any owner row"]
```

Audit and reporting are read models over owner contracts. The auditor cannot mutate an owner row, read credentials, read loan circulations, read Event registrations or read notification deliveries.

## Request authorization flow

```mermaid
sequenceDiagram
    participant Browser
    participant Security as Spring Security
    participant Policy as Path/method policy
    participant Controller as Owner controller
    participant Service as Application service
    participant DB as PostgreSQL
    Browser->>Security: /api/v1/admin/... + Bearer JWT
    Security->>Security: validate signature, expiry, issuer, audience
    alt missing or invalid token
        Security-->>Browser: 401
    else authenticated
        Security->>Policy: role claims + HTTP method + path
        alt no matching grant
            Policy-->>Browser: 403
        else granted
            Policy->>Controller: authorized request
            Controller->>Service: use case with actor UUID
            Service->>DB: owner-scoped transaction
            DB-->>Service: rows
            Service-->>Browser: response
        end
    end
```

The JWT carries role codes as `ROLE_<CODE>` authorities. Grants are evaluated before controller validation, so a denied caller cannot probe payload errors. The UI mirrors the same matrix, but direct HTTP clients always meet the backend check.

## Related decisions

- [ADR 0001: Modular monolith](../decisions/0001-modular-monolith-first.md)
- [ADR 0003: Stateless access tokens](../decisions/0003-authentication-token-strategy.md)
- [ADR 0014: Owner-scoped audit viewing](../decisions/0014-owner-scoped-audit-viewing.md)
- [ADR 0015: Owner-scoped reporting](../decisions/0015-owner-scoped-current-state-reporting.md)
- [ADR 0018: Department-based roles](../decisions/0018-department-functional-roles.md)
