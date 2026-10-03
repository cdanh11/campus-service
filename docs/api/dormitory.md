# Dormitory API — Inventory Foundation (4A1)

All operations require Bearer authentication and ROLE_ADMIN. Base path: /api/v1/admin/dormitory. The fixed resource parameter accepts only buildings, rooms or beds; unknown resources have no matching endpoint. There is no DELETE or allocation endpoint in 4A1.

| Operation | Contract |
| --- | --- |
| POST /{resource} | code, name; parentId required for rooms (building UUID) and beds (room UUID), absent for buildings. Returns 201, Location and ACTIVE item at version 0. |
| GET /{resource}/{id} | Returns item or 404 DORMITORY_RESOURCE_NOT_FOUND. |
| PUT /{resource}/{id} | code, name, status and nonnegative expectedVersion from latest read. Parent is immutable and is not a request field. Returns resulting item/version. |
| GET /{resource} | content/page/size/totalElements/totalPages; q, status, optional parentId for rooms/beds and sort. |

code uses Locale.ROOT uppercase after trimming exactly space/tab/newline/carriage return/vertical tab/form feed and must have 2–32 Unicode characters after expansion. name has 2–160 characters with the same trimming. Building code is globally unique; room code is scoped to building; bed code to room. PostgreSQL independently enforces case-insensitive uniqueness.

Creation requires active ancestors. Deactivate active children before their parent; activation requires active ancestors. There are no cascades. ACTIVE inventory does not imply vacancy; allocation/occupancy arrives in 4A2.

page defaults 0; size defaults 20 and is 1–100; page×size cannot exceed Integer.MAX_VALUE. q has at most 100 characters, literal case-insensitive code/name matching; %, _ and ! are escaped. status is ACTIVE/INACTIVE. parentId is not supported for buildings. sort is field,asc or field,desc, with code/name/status/createdAt/updatedAt allowed, always followed by UUID ascending. Default sort is code,asc.

Errors follow timestamp/status/code/message/path/traceId. Invalid body/text is 400 VALIDATION_FAILED; malformed JSON/UUID/type is 400 MALFORMED_REQUEST; invalid query bounds/enum/sort is 400 INVALID_QUERY_PARAMETER. Anonymous 401, USER 403. Duplicate code, stale version, inactive ancestor and active-child deactivation are 409 with DORMITORY_CODE_ALREADY_EXISTS, CONCURRENT_MODIFICATION, DORMITORY_REFERENCE_UNAVAILABLE and INVALID_DORMITORY_STATE respectively. Missing parent/resource is 404.

Successful create/update writes exactly one audit event with the JWT principal actor, resource type/UUID, actual resulting version, status-only JSON metadata and time. Reads and rejected operations add none. Audit persistence failure returns 500 AUDIT_WRITE_FAILED and rolls back business data/version. No credentials, names/contact details or full request snapshots are audited.
