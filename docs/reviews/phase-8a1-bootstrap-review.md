# Phase 8A1 — Explicit bootstrap review

Status: PASS, 2026-10-05. This gate covers offline first-administrator provisioning and its native wrapper only. Phase 8 overall remains IN PROGRESS; scoped permissions, Docker packaging, substantial fixtures and full acceptance/regression gates are outstanding.

## Requirements and implementation

- Explicit --bootstrap-admin entry point, never an automatic web startup hook or HTTP endpoint.
- Non-web context forced by the command, even when environment requests servlet startup. Same production migrations/entity validation and Identity adapters as the normal application; context/pool close after completion.
- Hidden console input with confirmation, or private process environment for setup wrappers; no password command arguments/logs. Windows wrapper releases native password buffers and removes bootstrap environment variables.
- Existing production email/profile/password validation and delegating bcrypt encoding. Admin guard serializes bootstrap transactions; refusal covers any ADMIN, including disabled/suspended accounts, and never resets an account.
- Initial account/roles plus self-attributed USER_CREATED audit are atomic. Audit metadata is empty. No migration/database/volume deletion or repair.
- Ignored .demo credential material is also excluded from Docker build context. Existing user configuration edits remain separate and are not staged.

## Verification

Final focused command:

```powershell
.\mvnw.cmd "-Dtest=FirstAdministratorProvisioningIntegrationTest,AdminUserManagementServiceTest,AdminUserControllerIntegrationTest" test
```

BUILD SUCCESS: 25 tests, zero failures/errors/skips, 1m29s, completed 2026-10-05T22:01:24+07:00. Includes five new PostgreSQL non-web integration cases (18.80s): successful validated/hash/audit setup, existing disabled-admin refusal, invalid input, audit-failure rollback and two competing setup transactions with exactly one winner. Nineteen existing ADMIN HTTP cases and the existing service test also pass.

Runtime evidence: CLI created the first synthetic demo ADMIN (Maven run BUILD SUCCESS 37.433s at 21:55:13+07, context/pool shut down). The second global ADMIN was created through the authenticated owner API. Login through the real frontend proxy, /me with ADMIN, persisted ADMIN count=2 and logout all passed. Final CLI invocation refused the existing accounts and exposed no Tomcat listener despite a servlet environment override. Credentials were generated per installation and saved only in ignored .demo/accounts.json; values were not printed.

PowerShell parser and git diff --check PASS. The final changed Java test source was recompiled before the authoritative final run. Earlier failed runs are not green evidence: the audit failure was translated by Spring and its exception assertion was corrected; adding HTTP-equivalent email validation required replacing a padded email fixture with an uppercase valid email. A run compiled before that fixture edit failed; the complete final rerun above passes.

## Review passes and limits

Pass 1 reviewed domain validation, guard/count/audit atomicity, concurrent setup and absence of a web endpoint. Pass 2 reviewed secret handling, inactive-admin refusal, forced non-web mode, cleanup and Docker context exclusions. Findings were corrected before this gate. Whole-repository/migration/frontend permission reviews and full clean verify remain later Phase 8 gates; this document does not claim they were rerun or that all possible defects are excluded.

No Docker image/rehearsal, 200-Student inventory or scoped role gate is claimed here. The [permission plan](../plans/phase-8a-permissions.md) is approved but not implemented at this checkpoint. The running native web application still uses the prior source process; future new web behavior requires a restart after verification.
