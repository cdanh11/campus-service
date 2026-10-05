# Phase 8A2 — Functional permissions review

## Gate

PASS, 2026-10-05. Backend authorization/migration/lifecycle, frontend boundaries, real-browser journeys and contract provenance are verified. The initial FAIL findings are resolved and preserved below. Whole Phase 8 is not complete.

## Implemented scope

Nine functional administration roles and separate AUDIT_VIEWER/REPORTING_VIEWER, plus existing ADMIN/USER. Backend path/method enforcement and frontend menu/direct-route boundaries. See [matrix](../permissions.md) and [ADR 0017](../decisions/0017-functional-administration-permissions.md).

V26 adds role rows only. Existing memberships and released V1–V25 are unchanged. Global ADMIN retains account/role management and final-active-admin protection.

## Verified evidence — 2026-10-05

- `./mvnw.cmd "-Dtest=FunctionalAdministrationAuthorizationIntegrationTest,AdminUserControllerIntegrationTest,FirstAdministratorProvisioningIntegrationTest" test`: BUILD SUCCESS, 40 tests, zero failures/errors/skips, 1m51s, finished 22:14:22 +07. This preceded the narrowed UUID reference matcher.
- Whole backend `./mvnw.cmd clean verify`: BUILD SUCCESS, 440 tests / 78 suites, zero failures/errors/skips, 25m27s, finished 22:56:53 +07. Surefire XML totals independently match; the repackaged Boot jar exists. This is backend regression evidence, not whole Phase 8 closure.
- Final `./mvnw.cmd "-Dtest=FunctionalAdministrationAuthorizationIntegrationTest,FlywayV25ToV26PermissionsUpgradeIntegrationTest" test`: BUILD SUCCESS, 18 tests, zero failures/errors/skips, 1m32s, finished 22:17:04 +07. Covers all 13 role codes, independent expected access matrix, read/write separation, multiple-role union, unauthorized and future-path denial, exact upgrade preservation and Hibernate validation with Flyway disabled.
- Client `npm run verify`: contract check, lint and production build passed. Vitest: 100 passed, 1 failed across 27 files, 197.33s. Failure: existing portal Event registration test exceeded its 5000ms timeout. New navigation/direct-route/combined-role overview tests passed. At that checkpoint the cause was not established. Subsequent isolated verification passed with the same configuration, timeouts and assertions, as recorded below.
- Focused client Event/Workspace rerun: 19/19 tests passed in 28.52s. Subsequent full `npm run verify` completed with 98 passed / 3 failed in 295.54s (Event initial-load/paging waits and Reporting test timeout). A one-worker diagnostic still hit the Reporting timeout while Maven was running and was stopped; it is not a complete suite result. Do not claim the cause is proven or hide these failures. The isolated sequential run after Maven finished passed, as recorded below.
- Final backend focused lifecycle/upgrade/OpenAPI command: `./mvnw.cmd "-Dtest=FunctionalAdministrationAuthorizationIntegrationTest,FlywayV25ToV26PermissionsUpgradeIntegrationTest,OpenApiOwnerContractIntegrationTest" test` — BUILD SUCCESS, 30 tests, zero failures/errors/skips, 2m15s, finished 23:03:14 +07. Adds real account creation/login/role replacement/stale rejection/refresh revocation checks for every new role; explicitly verifies issued JWT lifetime is unchanged. OpenAPI authorization wording now describes administrative permission rather than incorrectly claiming every owner endpoint is ADMIN-only.
- Isolated client `npm run verify` after Maven finished: contract/lint/build and 101/101 tests across 27 files passed; Vitest 108.53s, started 22:58:12 +07. Same configuration, timeouts and assertions. Earlier concurrent failures are retained above; use sequential regression on this machine.
- First whole real-browser execution on backend 3420a28: 24 passed / 1 failed in 4.6m. All 11 scoped-role journeys passed. Notification timed out selecting a recipient outside the virtual dropdown viewport after the fixture grew; the test now searches by exact email through the real reference API before selecting (both initial and reopened publication). No timeout/assertion/business-rule weakening. The failed runner correctly did not copy contracts; the corrected whole-suite rerun passed, as recorded below.
- Corrected whole real-browser execution: 25/25 Chromium journeys passed in 3.0m against clean backend 3420a28dcc38724a315f32ce6c339a7a75efcd55. Maven harness BUILD SUCCESS, 1 test / zero failures/errors/skips, 3m50s, finished 23:20:19 +07. Covers all 11 new roles plus prior owner/auth/portal journeys. Contract export/provenance copied only after this successful run.
- Real-browser discovery: 25 journeys in 11 files, including 11 new scoped-role login/API/route tests. Discovery is not an execution PASS.
- Native runtime: normal Flyway startup applied exactly V26, Hibernate validation succeeded and health returned UP. Existing two global ADMIN were preserved. Eleven scoped/viewer accounts were created through the ADMIN API; every account logged in, read its approved owner endpoint (200), failed an account-create attempt (403), and had the expected malformed-owner-write result (400 for operators, 403 for viewers). Malformed-write checks verify authorization before validation, not a successful business mutation. Credentials remain in ignored `.demo/accounts.json` and are not printed.
- Reviewed all 10 controller diffs: only authorization wording changed, no DTO/routes/business logic changes. Reviewed role/security policy, exact reference path ordering, V26 preservation, lifecycle tests and scope documentation.
- `git diff --check` passed in both repositories. No released migration modifications in the working diff.

## Review findings and resolutions

- Scoped administrator overview initially listed only People cards, leaving other operators with no useful destinations. Fixed to list all allowed functional destinations; combined Event/Library overview test passed.
- Reporting reference grants use explicit list/UUID-detail paths before owner wildcards, excluding raw circulation/membership management. Account reference grants exclude future non-UUID subroutes. Backend tests verify denial.
- Cross-function UI routes are blocked before data requests, including Finance/Student and Finance/Dormitory, Dormitory/payments, Audit/dashboard, Reporting/audit and Academic/personnel management. Reference reads do not open another owner's editing UI.
- No per-unit row isolation, field redaction or approval hierarchy is claimed. Stateless issued JWTs remain valid until expiry as documented.

## Closure and remaining Phase 8 work

Latest frontend verification after contract regeneration: contract/lint/build and 101 tests / 27 files PASS (77.69s Vitest); mocked Chromium 13/13 PASS (1.0m). All real Chromium journeys 25/25 PASS as recorded above. Contract comparison proves only reviewed authorization summary/info changes; paths, DTOs and constraints are preserved. CI backend pin matches verified production export at 3420a28dcc38724a315f32ce6c339a7a75efcd55. Post-push GitHub CI [run 37341349374](https://github.com/cdanh11/campus-client/actions/runs/37341349374) completed SUCCESS at client commit 6b9d3dcfb573acf30339f589ae043896c9cf2dbb, confirmed through the public Actions API. This is separate evidence from the local results above.

Backend functional implementation is committed at 3420a28. User configuration files and the local roadmap are excluded. No released migration, database or volume was repaired/deleted. Native demo accounts were created through the authorized ADMIN API; no public provisioning endpoint was added.

At this 8A2 checkpoint, portable Docker setup, approximately 200 Students and the wider demo inventory, acceptance matrix and Phase 8 closure were separate unfinished gates. Docker/data subsequently passed [8A3 review](phase-8a3-demo-review.md); 8B/8C remain incomplete.
