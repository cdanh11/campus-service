# Finance API — Obligations (4B1)

All eight operations require Bearer authentication and ROLE_ADMIN. Base /api/v1/admin/finance. No payment endpoint in 4B1; manual payments have a separate 4B2 gate.

| Operation | Contract |
| --- | --- |
| POST /fees | code, name, amount; returns 201/Location, ACTIVE definition at version 0, currency VND. |
| GET /fees/{id} | Current fee definition or 404. |
| PUT /fees/{id} | code, name, amount, ACTIVE/INACTIVE status and nonnegative expectedVersion. Returns stored resulting definition/version. |
| GET /fees | q/status/page/size/sort; content/page/size/totalElements/totalPages. |
| POST /charges | chargeNumber, studentId, feeId, dueDate (ISO date). Returns 201/Location and OPEN charge at version 0, with fee snapshot. |
| GET /charges/{id} | Retained charge/snapshot or 404. |
| PUT /charges/{id} | status=CANCELLED, nonnegative expectedVersion. No editable financial/reference fields. |
| GET /charges | q/status/studentId/feeId/page/size/sort; same page envelope. |

Amounts are positive VND integers from 1 to 9999999999999999999. Fractional/overflow values are rejected; no float rounding or amount override on charges. Clients must preserve exact decimal/integer values (JavaScript consumers should avoid Number for values above its exact integer range). Currency is server-selected VND.

Codes/charge numbers are ROOT uppercase after trimming exactly space/tab/newline/carriage return/vertical tab/form feed; 2–32 Unicode characters after expansion. Fee names are 2–160 characters using the same trim. Fee code and charge number are globally case-insensitively unique. Charge creation requires ACTIVE fee and ACTIVE Student. It stores immutable Student/fee UUIDs, fee code/name/amount/currency, dueDate and creation time; later fee changes/deactivation do not alter old obligations. Past due dates may record existing obligations. CANCELLED is terminal and possible after reference deactivation; no deletion, transfer, automatic billing or overdue scheduler.

Fee fields: id/code/name/amount/currency/status/rowVersion/createdAt/updatedAt. Charge fields: id/chargeNumber/studentId/feeId/feeCode/feeName/amount/currency/dueDate/status/rowVersion/createdAt/updatedAt. Stored timestamps are returned after persistence refresh.

page defaults 0 and must be nonnegative; size defaults 20, allowed 1–100; page×size ≤ Integer.MAX_VALUE. q ≤100 Unicode characters, literal case-insensitive code/name or chargeNumber/feeName substring; %, _ and ! are escaped. Fee sorts code/name/amount/status/createdAt/updatedAt; charge sorts chargeNumber/amount/dueDate/status/createdAt/updatedAt; field,asc/desc followed by UUID ascending. Defaults code,asc and dueDate,asc. Fee status ACTIVE/INACTIVE; charge OPEN/CANCELLED.

Errors use timestamp/status/code/message/path/traceId. Invalid body/domain value 400 VALIDATION_FAILED; malformed JSON/UUID/type 400 MALFORMED_REQUEST; query bounds/enum/sort 400 INVALID_QUERY_PARAMETER. Anonymous 401, USER 403; missing resource 404 FINANCE_RESOURCE_NOT_FOUND. Duplicate number/code 409 FINANCE_CODE_ALREADY_EXISTS; stale expectedVersion 409 CONCURRENT_MODIFICATION; unavailable Student/inactive fee 409 FINANCE_REFERENCE_UNAVAILABLE; incompatible cancellation 409 INVALID_FINANCE_STATE. Missing fee is 404.

Each supported mutation creates exactly one status-only audit event with trusted JWT actor, resource/type/UUID, action, resulting version and timestamp in the same transaction. Reads and rejected operations create none. Audit failure returns 500 AUDIT_WRITE_FAILED and rolls back business fields/version. Contact data, credentials and full request bodies are not audited.
