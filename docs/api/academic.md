# Academic API Contract

All data operations below require Bearer authentication and ROLE_ADMIN. Generated OpenAPI at `/v3/api-docs` and Swagger UI describe the request/response schemas; these documentation routes are public under the existing security configuration and expose no runtime records.

## Resources and operations

Base path: `/api/v1/admin/academic`.

| Resource | POST fields | PUT fields in addition to expectedVersion | List filters / default sort |
| --- | --- | --- | --- |
| programs | code, name, organizationUnitId, optional status | code, name, organizationUnitId, status | q, status / code,asc |
| courses | code, title, credits, organizationUnitId, optional status | code, title, credits, organizationUnitId, status | q, status / code,asc |
| terms | code, name, startDate, endDate | code, name, startDate, endDate, status | q, status / code,asc |
| offerings | termId, courseId | status | termId, courseId, status / createdAt,desc |
| sections | offeringId, code, capacity, optional facultyId | code, capacity, facultyId, status | q, offeringId, status / code,asc |
| enrollments | studentId, sectionId | status | studentId, sectionId, status / createdAt,desc |

POST returns 201 and Location. GET `/{id}` returns 200. GET collection returns content/page/size/totalElements/totalPages. PUT `/{id}` returns 200 and requires the nonnegative expectedVersion from the latest read. References on offerings, sections and enrollments cannot be changed through PUT. No DELETE operation is exposed.

Program/Course status is ACTIVE/INACTIVE; credits are 1–30. Term status is PLANNED/ACTIVE/CLOSED/CANCELLED, dates are inclusive and start <= end. Offering/Section status is DRAFT/OPEN/CLOSED/CANCELLED. Enrollment status is ENROLLED/WITHDRAWN; re-enrollment uses PUT on the same record. See the phase plans for exact lifecycle and historical-deactivation rules. Admission is based on active/open statuses; registration calendar windows are not implemented.

## Queries

page is zero-based (default 0); size is 1–100 (default 20); page × size must be <= Integer.MAX_VALUE. q is at most 100 characters and matches literally, case-insensitively: code/name for programs and terms, code/title for courses, code for sections. `%` and `_` are literal input, not wildcard operators. Empty/whitespace q means no text filter.

sort is exactly `field,asc` or `field,desc`; each query appends ID ascending as a stable tie-breaker. Allowed fields:

- Programs: code, name, status, createdAt, updatedAt.
- Courses: code, title, credits, status, createdAt, updatedAt.
- Terms: code, name, startDate, endDate, status, createdAt, updatedAt.
- Offerings: status, createdAt, updatedAt.
- Sections: code, capacity, status, createdAt, updatedAt.
- Enrollments: status, createdAt, updatedAt.

## Errors and audit

Errors use timestamp/status/code/message/path/traceId. traceId may be null under the existing tracing baseline. Validation/malformed/query errors return 400; missing/invalid access tokens 401; insufficient role 403; absent Academic resource 404; duplicates, stale versions, unavailable references, invalid lifecycle and full section return 409. Enrollment capacity uses SECTION_CAPACITY_EXCEEDED. Catalog retains its established resource-specific codes; delivery/enrollment use ACADEMIC_RESOURCE_ALREADY_EXISTS, ACADEMIC_RESOURCE_NOT_FOUND and ACADEMIC_REFERENCE_UNAVAILABLE. Stale updates use CONCURRENT_MODIFICATION across resources.

Every successful HTTP POST/PUT records one Academic audit event with the validated actor, resource identifier/type, action, resulting row version and status/time. Enrollment actions distinguish WITHDRAWN/REENROLLED. Reads and rejected operations write no success event. Business write and audit share one transaction: failure to persist audit returns 500 AUDIT_WRITE_FAILED, and business data/version/occupancy roll back. Do not retry blindly after a lost network response; read the resource and use the returned version.

Internal provisioning and test-fixture application entry points are distinct from the audited HTTP entry point. There is no audit read endpoint or database-enforced append-only policy in this phase. Audit does not store credentials, tokens, contact information, names or arbitrary request payload.
