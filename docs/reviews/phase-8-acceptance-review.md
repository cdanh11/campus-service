# Phase 8 — Acceptance and source review

## Gate

PASS locally, 2026-10-08. Phase 8A1–8A3 retain their separate checkpoint reviews. Current 8B acceptance and 8C regression passed after the reproduced findings below were corrected. Backend runtime/test source is `f7b34e9`; frontend runtime/test source is committed at `2773134`, with verified contract provenance/CI pin at `a7c5398`. Later documentation commits do not change the tested source. This is local evidence; post-push GitHub CI and user PR/merge are separate.

## Acceptance traceability

The following assertions passed in the current backend/frontend regressions. Mocked UI states remain separate from real HTTP/database journeys and the seeded Docker browser checks. Test discovery or a test filename alone was not used as execution evidence.

| Screen / responsibility | Owner API | Required outcomes | Inspected evidence passing in the current regression |
| --- | --- | --- | --- |
| Login / session | `/api/v1/auth/*` | Invalid credentials; exact refresh Origin; rotation/reuse; logout/reload; expiry; two tabs; no persisted access token | `AuthControllerIntegrationTest`, `JwtAuthenticationFilterTest`; client API tests; real `auth.spec.ts` |
| Account / permissions | `/api/v1/admin/users` | Role union; forbidden cross-owner writes; session revocation; stale writes; final active ADMIN protection | `AdminUserControllerIntegrationTest`, `FunctionalAdministrationAuthorizationIntegrationTest`; real `users.spec.ts`, `permissions.spec.ts` |
| Units / Students / personnel | `/api/v1/admin/organization-units`, `/students`, `/faculty-staff` | Unique normalized identifiers; valid links; bounded search/page/sort; validation; audit | Registry controller/domain tests, `PeopleRegistrySearchIntegrationTest`, `PeopleRegistryAuditIntegrationTest`; real `people.spec.ts` |
| Academic | `/api/v1/admin/academic/*` | Fresh versions; active references; DRAFT/OPEN prerequisites; capacity; withdrawal/restoration keeps history | Catalog/delivery/enrollment controller/domain tests; real `academic.spec.ts` |
| Accommodation | `/api/v1/admin/dormitory/*` | Active inventory; one current stay per Student/bed; release retains history; concurrent assignment protection | Dormitory inventory/assignment controller/domain tests; real `operations.spec.ts` |
| Finance | `/api/v1/admin/finance/*` | Exact integer VND; snapshots; partial payment; no overpayment; receipt uniqueness; reversal; cancellation only without effective payment | Obligation/payment controller/domain tests; client lossless JSON/Finance tests; real `operations.spec.ts` |
| Notification | `/api/v1/admin/notifications/*`, `/api/v1/notifications` | Explicit publication; recipient limit; private inbox; escaped text; fresh-version read; readable timestamps | `NotificationIntegrationTest`, `NotificationDomainTest`; client Inbox tests; real `notifications.spec.ts`, `portal.spec.ts` |
| Event | `/api/v1/admin/events`, `/event-registrations`; personal Event endpoints | OPEN admission; capacity; own membership; no self-ATTEND; retained cancel/restore; readable timestamps | Event controller/domain tests; client Events tests; real `events.spec.ts`, `portal.spec.ts` |
| Library | `/api/v1/admin/library/*` | One open loan per copy; active references; default 14-day due date; return history; fresh versions | Library controller/domain tests; real `library.spec.ts` |
| Audit | `/api/v1/admin/audits/*` | Read-only authorized sources; bounded filters; no mutation API | Audit viewing integration/contract/query-plan tests; real `insights.spec.ts` |
| Reporting | `/api/v1/admin/reports/*` | Eight dashboard groups; consistent snapshot; bounded report/CSV; formula escaping; exact VND | Reporting integration/snapshot/CSV/query tests; real `insights.spec.ts` |
| Shared UI | Existing owner endpoints only | Empty/loading/error states; submitted search; page reset; keyboard/focus; mobile layout; no unauthorized prefetch | Client component tests and mocked browser suite; real browser evidence recorded separately |
| Migration / boundaries | Flyway V1–V26; production entities | Released migrations unchanged; genuine upgrade data preservation; exact-schema validation without create/update; no cross-owner persistence access | Upgrade integration tests, `ModuleBoundaryTest`, clean verify |
| Portable seeded demo | Owner APIs through local proxy | Substantial actual inventory; references/capacity; repeat without owner writes; recovery; preserved restart data; distinct installation cookies | [8A3 review](phase-8a3-demo-review.md), demo inventory/browser/resume/persistence verifiers |

## Review method and scope

The review follows three passes: business/security and transaction rules; persistence/query/schema and API contracts; frontend session/permissions, final diffs and runnable documentation. Read actual assertions, reproduce confirmed findings, then rerun affected tests and the complete regressions. A test name, discovery count, health response or previous checkpoint does not prove the current gate.

Inspected Identity authentication/session/refresh, bootstrap, account mutation and persistence; Organization/Student/personnel domain/application/API/persistence; Academic catalog/delivery/enrollment and locks; Dormitory inventory/assignment; Finance fee snapshots/payments/reversal/balances; Notification inbox/publication; Event admission/membership; Library circulation; all owner report and audit queries, authorization policy and shared API contracts. Entity mappings, immutable parent columns, optimistic versions, safe audit metadata and scalar owner reference ports were checked against actual migrations. SQL ownership is reviewed separately because ModuleBoundaryTest guards Java dependencies only.

The second pass checked role unions and reference reads against independent authorization expectations, parent-first locks and refresh-under-lock stale checks, bounded query offsets/sort allowlists, literal LIKE escaping where the owner contract defines it, exact VND arithmetic/CSV neutralization, retained history and audit rollback. Existing People wildcard search semantics are not silently replaced. The V25→V26 upgrade test preserves legacy users/memberships/history, adds exactly the approved roles and validates all 36 production entities on the exact upgraded public schema with Flyway disabled.

Frontend review covers API token memory/refresh serialization/session epochs, cache clearing and no automatic conflict replay; menu/direct-route boundaries; generated contracts/lossless integer values; personal inbox/Event ownership and dialog focus. Follow-up reads covered shared registry/reference forms, dashboard/report/audit/payment pages, query/form rules, application/session routes and owner application ports/dashboard adapters. These are bounded source inspections, not an independent security certification or a claim that every line/test case has been examined. Representative rendered views and current real-browser/demo execution are recorded below.

## Confirmed findings and corrections

| Finding | Correction | Regression evidence |
| --- | --- | --- |
| Login/refresh response hard-coded a 900-second lifetime despite configured JWT TTL | Use configured seconds; compare response with a genuinely signed/decoded JWT at 300/900 seconds | 19 focused tests PASS; `89b1d07` |
| Final-admin guard also blocked deactivating non-admins and removing ADMIN from inactive users | Check the target is ACTIVE and ADMIN before deciding the mutation reduces active administrators | Eight genuine HTTP/PostgreSQL cases; 66-test extended Identity regression PASS; `3ad7143` |
| Account pagination overflow returned 500 | Reject offsets beyond the JPA integer bound; accept the exact supported boundary | HTTP 400/error-contract boundary assertions in the same 66-test regression; `b559227` |
| Unicode uppercase expansion exceeded the approved 32-character identifier columns | Revalidate canonical Organization/Student/personnel identifiers | Three RED cases; 16 domain/controller tests PASS including exact 32-character boundary; `159c6df` |
| Integer request fields silently coerced fractional/scientific JSON or numeric strings | Configure integer deserialization to reject coercion; keep decimal money/query binding/serialization unchanged | Genuine HTTP RED returned 200 and changed an account; 33 JSON/HTTP/OpenAPI/boundary tests PASS; `f7b34e9` |
| Personal inbox/Event timestamps displayed raw ISO/UTC values | Shared display-only CampusTime, fixed Asia/Ho_Chi_Minh and explicit UTC+7; retain original instant in semantic time element | 107 unit/component tests, 13 mocked and 25 real browser tests PASS, including exact time/instant assertions; `2773134` |

Each correction was reviewed and committed by function after its focused PASS. Released V1–V26, owner DTO shapes and endpoints are unchanged. User application YAML changes and the local roadmap were not staged.

## Actual verification checkpoints

All dates below are 2026-10-08, timezone +07. Maven totals were checked against Surefire XML; zero failures/errors/skips on the successful commands.

| Source / command | Actual result | Elapsed / finish |
| --- | --- | --- |
| `89b1d07` plus preserved user configuration; backend `./mvnw.cmd clean verify` | BUILD SUCCESS, 453 tests / 79 suites; predates guard/offset/Unicode corrections | 9m57s; 22:37:51 |
| Identity guard/offset and related authorization/persistence/revocation/audit regression | BUILD SUCCESS, 66 tests | 1m45s; 22:49:37 |
| Organization/Student/personnel domain + controller regression | BUILD SUCCESS, 16 tests | 1m05s; 22:53:33 |
| `159c6df` plus preserved user configuration; backend `./mvnw.cmd clean verify` | BUILD SUCCESS, 465 tests / 80 suites; predates strict integer JSON correction | 10m58s; 23:05:11 |
| Current frontend `npm run verify` | Contracts/lint/TypeScript/build PASS; 107 tests / 28 files | Vitest 149.83s; started 23:08:13 |
| Current frontend `npm run test:e2e` | 13/13 mocked Chromium PASS | 59.9s |
| IntegerJsonContractTest, AdminUserControllerIntegrationTest, OpenApiOwnerContractIntegrationTest, ModuleBoundaryTest | BUILD SUCCESS, 33 tests | 1m13s; 23:19:16 |
| `f7b34e9` plus preserved user configuration; backend `./mvnw.cmd clean verify` | BUILD SUCCESS, 476 tests / 81 suites; terminal and XML totals agree | 11m39s; 23:32:06 |
| Frontend source later committed at `2773134`, isolated clean backend `f7b34e9`; full real Chromium suite | 25/25 PASS in 11 files; Maven harness BUILD SUCCESS, one opt-in harness test | Browser 3.4m; harness 4m36s; 23:37:30 |
| `node --test --test-isolation=none scripts/demo-client.test.mjs` | 10/10 loader safety tests PASS | 569.58ms |

RED reproductions establish the findings, not the final gate. Test synchronization was corrected to use persisted versions/timestamps and explicit JPA flush before JDBC audit inspection; business assertions were retained. One sandbox execution did not complete after a JVM attach-pipe error; elevated verification then passed. An execution timeout or missing terminal result is reported as incomplete, not BUILD FAILURE.

The historical real harness first failed because port 3000 was occupied; no browser case ran. A subsequent retained log has partial results and no final total, so it is incomplete. The integration checkout retains an identical frontend-owned copied fixture: automatic approval review rejected its deletion for lack of explicit path-specific authorization. Direct opt-in execution preserves the fixture, avoids removal/overwrite and still uses isolated Testcontainers rather than the developer database. Port/revision/hash checks precede execution. No contract is copied on failure.

Current successful direct harness command, from the isolated checkout:

```powershell
.\mvnw.cmd "-Dtest=CampusClientBrowserIntegrationTest" "-Dcampus.client.dir=D:\Project\campus-client" test
```

The production export is semantically identical to the stored frontend OpenAPI snapshot. Generated-contract check passed; provenance and CI now point to the tested, pushed backend SHA `f7b34e99d5cf5f434fa1308ff2e90bee58fca4fc`. Backend full verification includes preserved user YAML; the clean isolated browser/image build excludes those user edits.

## Current Docker dataset and demonstration

Rebuilt backend image from the clean `f7b34e9` checkout and frontend image from clean `a7c5398`; revision labels were inspected. Docker packaging deliberately skips tests and is not counted as another test run. `scripts/start-demo.ps1 -SkipBuild -Seed` reused the existing private configuration/administrator marker and persistent volume; normal login verified the installation without provisioning another ADMIN. Inventory verified at 23:43:25 +07 with `ownerWritesThisRun: 0`.

| Inventory | Actual retained data |
| --- | --- |
| Accounts / people | 2 global ADMIN, 11 scoped/viewer accounts, 200 linked Student USER; 16 units, 80 personnel |
| Academic | 12 programs, 60 courses, 3 terms, 90 offerings, 150 sections; 800 enrollments (740 current / 60 withdrawn) |
| Accommodation | 4 buildings, 80 rooms, 240 beds; 120 stays (100 assigned / 20 released) |
| Finance | 6 fees, 600 charges (580 open / 20 cancelled); 450 receipts (420 recorded / 30 reversed) |
| Notification | 24 templates/publications, 1,200 deliveries (40 read / 1,160 unread) |
| Event | 16 events; 400 memberships (256 registered / 64 cancelled / 80 attended) |
| Library | 120 fixture titles plus one retained recovery-check title; 360 copies, 200 loans (120 open / 80 returned) |

Inventory verification checks relationships, role assignments, uniqueness/capacity, all charge balances, 200 Student logins/inboxes, owner audit coverage and eight dashboard groups through approved owner APIs. It never writes business tables directly. Library dates preserve the actual 14-day default, rather than manufacturing past loans.

`node scripts/verify-demo-browser.mjs` PASS at 23:44:48 +07: proxy login/HttpOnly cookie/no token storage, refresh rotation/reload, denied Origin, two-tab logout, FINANCE_ADMIN menu/HTTP/direct-route boundaries and linked Student mobile inbox/Event without ADMIN calls or page overflow. Visually inspected the freshly captured desktop Student table and mobile Inbox: readable layout, pagination, UTC+7 timestamps, no visible clipping. Screenshots/credentials remain ignored/private; native/Docker cookie coexistence retains its separately recorded 8A3 evidence and was not claimed rerun here.

`node scripts/verify-demo-resume.mjs --reconcile-existing` PASS at 23:45:51 +07: retained recovery title reconciled using owner audit, one matching title, zero duplicate POSTs and zero wrong-target owner writes. This rerun reconciles the existing journal; the original genuinely lost create/mutation responses remain the dated 8A3 evidence, not newly injected writes.

`scripts/verify-demo-persistence.ps1` PASS at 23:47:07 +07: snapshots of 25 business tables match across stop/restart, including complete UUID/status/version digests, role assignments, retained audit IDs, volume creation identity and Flyway checksums/history. All three services returned healthy. No repair or database/volume deletion occurred.

## Closure and limits

No unresolved blocker/major from this review. Final functional diffs and Git checks preserve released V1–V26 and unrelated user YAML; local roadmap is not staged. Final relative-link inspection found no missing file targets in 82 backend and 33 frontend Markdown files, including the closure reviews. README contains current scope/setup rather than an execution journal.

Evidence is Windows/Java 21/PostgreSQL 17.6 and Chromium local testing, not production uptime/load SLA, independent penetration testing or cross-browser certification. Scoped roles grant functions with documented read references, not department-level row isolation. Personal portal remains own Inbox/Event; Finance remains manual VND receipts. Phase 9 walkthrough/presentation/final rehearsal is next; PR/merge belongs to the user.
