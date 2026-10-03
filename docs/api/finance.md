# Finance API — Obligations and Manual Payments (4B1/4B2)

All operations require Bearer authentication and ROLE_ADMIN. Base /api/v1/admin/finance. Payments are manual records; no gateway or real-money transfer.

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

## Manual payment and balance

| Operation | Contract |
| --- | --- |
| POST /payments | receiptNumber, chargeId, amount, expectedChargeVersion; returns 201/Location and RECORDED receipt at version 0. Charge must be OPEN; amount cannot exceed current outstanding. |
| GET /payments/{id} | Retained receipt or 404. |
| PUT /payments/{id} | status=REVERSED, expectedVersion (receipt), expectedChargeVersion, reason. Full receipt reversal only; history/amount/references immutable. |
| GET /payments | q/chargeId/status/page/size/sort, same page envelope/bounds as obligations. |
| GET /charges/{id}/balance | chargeId, original amount, currency, status, rowVersion, paidAmount and outstandingAmount read together. |

Receipt fields: id/receiptNumber/chargeId/amount/currency/status/rowVersion/recordedAt/reversedAt/reversalReason/createdAt/updatedAt. Receipt number has the same 2–32 normalized Unicode code rules and is globally unique, including REVERSED history. Amount has the same exact integer VND range. recordedAt is server time. Reversal reason is 2–500 Unicode characters after the same six-character boundary trim. RECORDED has null reversal fields; REVERSED has reason/time and is terminal. Reason stays on the receipt and is not copied to audit metadata.

Payments may be partial or full. Every payment/reversal advances charge version; reload balance/charge rowVersion before the next mutation. Reversal also requires the latest receipt rowVersion. Student/fee deactivation does not prevent settlement of an OPEN retained obligation. Effective paid amount sums only RECORDED receipts. Cancellation requires effective paid=0; reverse receipts first when correcting a paid charge. OPEN outstanding=amount−paid; CANCELLED retains original amount but reports paid=0 and collectible outstanding=0. Fully paid charges remain OPEN; balance carries the payment result.

Payment q matches receiptNumber literally; status RECORDED/REVERSED, optional chargeId. Sorts receiptNumber/amount/status/recordedAt/createdAt/updatedAt, asc/desc then UUID ascending, default recordedAt,desc. Reusing any receipt number is 409 FINANCE_CODE_ALREADY_EXISTS; stale receipt/charge version is 409 CONCURRENT_MODIFICATION; overpayment 409 PAYMENT_EXCEEDS_BALANCE; incompatible state or cancellation with effective payments 409 INVALID_FINANCE_STATE. Other errors follow the contract above.

Payment/reversal writes one PAYMENT RECORDED/REVERSED audit event atomically with receipt and charge-version changes. Failure rolls back all rows/versions/balances. No backdated settlement, partial reversal, physical deletion, refund transfer, reconciliation or self-service.
