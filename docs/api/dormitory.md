# Dormitory API — Inventory and Accommodation (4A1/4A2)

All operations require Bearer authentication and ROLE_ADMIN. Base path: /api/v1/admin/dormitory. The inventory resource parameter accepts only buildings, rooms or beds; unknown resources have no matching endpoint. Assignments have separate fixed routes. There is no DELETE endpoint.

| Operation | Contract |
| --- | --- |
| POST /{resource} | code, name; parentId required for rooms (building UUID) and beds (room UUID), absent for buildings. Returns 201, Location and ACTIVE item at version 0. |
| GET /{resource}/{id} | Returns item or 404 DORMITORY_RESOURCE_NOT_FOUND. |
| PUT /{resource}/{id} | code, name, status and nonnegative expectedVersion from latest read. Parent is immutable and is not a request field. Returns resulting item/version. |
| GET /{resource} | content/page/size/totalElements/totalPages; q, status, optional parentId for rooms/beds and sort. |

code uses Locale.ROOT uppercase after trimming exactly space/tab/newline/carriage return/vertical tab/form feed and must have 2–32 Unicode characters after expansion. name has 2–160 characters with the same trimming. Building code is globally unique; room code is scoped to building; bed code to room. PostgreSQL independently enforces case-insensitive uniqueness.

Creation requires active ancestors. Deactivate active children before their parent; activation requires active ancestors. A bed with an ASSIGNED occupant cannot be deactivated. There are no cascades. ACTIVE inventory indicates availability status; occupancy is tracked separately by assignments.

page defaults 0; size defaults 20 and is 1–100; page×size cannot exceed Integer.MAX_VALUE. q has at most 100 characters, literal case-insensitive code/name matching; %, _ and ! are escaped. status is ACTIVE/INACTIVE. parentId is not supported for buildings. sort is field,asc or field,desc, with code/name/status/createdAt/updatedAt allowed, always followed by UUID ascending. Default sort is code,asc.

Errors follow timestamp/status/code/message/path/traceId. Invalid body/text is 400 VALIDATION_FAILED; malformed JSON/UUID/type is 400 MALFORMED_REQUEST; invalid query bounds/enum/sort is 400 INVALID_QUERY_PARAMETER. Anonymous 401, USER 403. Duplicate code, stale version, inactive ancestor and active-child deactivation are 409 with DORMITORY_CODE_ALREADY_EXISTS, CONCURRENT_MODIFICATION, DORMITORY_REFERENCE_UNAVAILABLE and INVALID_DORMITORY_STATE respectively. Missing parent/resource is 404.

Successful create/update writes exactly one audit event with the JWT principal actor, resource type/UUID, actual resulting version, status-only JSON metadata and time. Reads and rejected operations add none. Audit persistence failure returns 500 AUDIT_WRITE_FAILED and rolls back business data/version. No credentials, names/contact details or full request snapshots are audited.

Inventory POST/PUT returns stored PostgreSQL timestamp precision and actual resulting rowVersion after flush/refresh. A subsequent GET returns the same representation when there is no intervening mutation; createdAt remains unchanged through PUT.

## Current accommodation

| Operation | Contract |
| --- | --- |
| POST /assignments | Required studentId and bedId. Active Student and active building/room/bed required. Returns 201, Location and ASSIGNED record at version 0. |
| GET /assignments/{id} | Returns assignment or 404 DORMITORY_RESOURCE_NOT_FOUND. |
| PUT /assignments/{id} | Required status=RELEASED and nonnegative expectedVersion. Returns released record with incremented version. Student/bed references are immutable. |
| GET /assignments | Same page/size/offset bounds as inventory; optional studentId, bedId and ASSIGNED/RELEASED status. sort allows assignedAt/createdAt/updatedAt/status, asc/desc, then UUID ascending; default assignedAt,desc. |

Response fields: id, studentId, bedId, status, rowVersion, assignedAt, releasedAt, createdAt and updatedAt. Timestamp responses reflect stored PostgreSQL precision. Each Student and each bed can have at most one ASSIGNED record. RELEASED is terminal, frees the bed and retains the original references and admission time; a later stay creates a new UUID. Release remains possible after Student deactivation. There are no future booking intervals, transfers, automatic billing or self-service routes.

Duplicate/current-place conflicts return 409 ACCOMMODATION_ALREADY_ASSIGNED; unavailable Student returns 409 STUDENT_UNAVAILABLE; incompatible release state returns 409 INVALID_ASSIGNMENT_STATE. Stale version, unavailable inventory and malformed requests follow the existing error contract. Successful assignment/release records one ASSIGNMENT audit event with ASSIGNED/RELEASED action, actor, resulting version and status metadata in the same transaction; failures roll back both.
