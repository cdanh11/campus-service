# Notification API

Phase 5A reviewed PASS; actual verification evidence is recorded in the Phase 5A review. Bearer JWT required. All `/api/v1/admin/notifications/**` operations require ADMIN; `/api/v1/notifications` serves only the authenticated principal's own inbox.

| Operation | Request/response |
| --- | --- |
| POST /admin/notifications/templates | code/name/title/body → 201/Location, ACTIVE template/version 0 |
| GET /admin/notifications/templates/{id} | Template or 404 |
| PUT /admin/notifications/templates/{id} | code/name/title/body/status/expectedVersion → saved template |
| GET /admin/notifications/templates | q/status/page/size/sort, default code,asc |
| POST /admin/notifications/notices | templateId → 201/Location, DRAFT snapshot/version 0 |
| GET /admin/notifications/notices/{id} | Notice or 404 |
| PUT /admin/notifications/notices/{id} | title/body/expectedVersion → edited draft; PUBLISHED is immutable |
| POST /admin/notifications/notices/{id}/publish | recipientIds/expectedVersion → PUBLISHED notice; one atomic local delivery batch |
| GET /admin/notifications/notices | q/status/page/size/sort, default createdAt,desc |
| GET /notifications | Own deliveries with immutable title/body snapshot; status/page/size/sort, default deliveredAt,desc |
| GET /notifications/{id} | Own `{delivery,title,body}`; missing/another owner's ID returns 404 |
| PUT /notifications/{id}/read | expectedVersion → saved delivery; repeated READ keeps its original result/time/version |

All paths above have `/api/v1` prefix. Template code is ROOT uppercase after exactly space/tab/newline/carriage-return/vertical-tab/form-feed boundary trimming; 2–32 Unicode code points after expansion. Name/title are 2–160; plain-text body 2–4000. No executable markup/template rendering is provided. Status ACTIVE/INACTIVE for template, DRAFT/PUBLISHED for notice, UNREAD/READ for delivery. Responses retain stored PostgreSQL timestamp precision.

Publishing accepts 1–100 distinct ACTIVE account UUIDs; a mixed invalid batch delivers nothing. There is no implicit broadcast, SMTP/SMS, scheduled send or automatic event subscription. Template/content changes or template deactivation preserve existing snapshots. A failed transaction can be retried with the original version. Recipient identity for inbox/read comes from the JWT principal and cannot be supplied by the request.

Page defaults 0, size 20; size 1–100, page nonnegative and page×size ≤ Integer.MAX_VALUE. q ≤100 Unicode characters and literal case-insensitive matching: template code/name or notice title, with %, _ and ! escaped. Inbox has no free-text filter. Template sorts code/name/status/createdAt/updatedAt; notice title/status/createdAt/updatedAt; inbox status/deliveredAt/createdAt/updatedAt. Each allowlisted asc/desc sort ends with UUID ascending for stable ties. List responses contain content/page/size/totalElements/totalPages; inbox batches notice reads to avoid a query per delivery.

Errors follow timestamp/status/code/message/path/traceId: anonymous 401, USER on ADMIN routes 403; missing/foreign-owned 404 NOTIFICATION_NOT_FOUND; duplicate template 409 NOTIFICATION_CODE_ALREADY_EXISTS; stale 409 CONCURRENT_MODIFICATION; inactive recipient/template 409 NOTIFICATION_REFERENCE_UNAVAILABLE; immutable notice 409 INVALID_NOTIFICATION_STATE. Invalid body 400 VALIDATION_FAILED, malformed UUID/JSON/type 400 MALFORMED_REQUEST, invalid list query 400 INVALID_QUERY_PARAMETER. Audit failure returns 500 AUDIT_WRITE_FAILED and rolls back fields/versions/delivery rows. Successful template/draft/publication/read mutations write status-only audit; reads/rejected requests/idempotent acknowledgements do not add events.
