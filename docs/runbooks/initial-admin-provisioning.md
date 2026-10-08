# First administrator provisioning

The explicit portable CLI is approved in [ADR 0016](../decisions/0016-explicit-administrator-bootstrap.md) for Phase 8 setup. It creates the first ADMIN in the configured database, not on normal web startup. No public provisioning API is provided.

## Preconditions

Confirm the intended database/environment and authorized administrator. Supply the same profile, PostgreSQL and JWT configuration as the application; Flyway and Hibernate validation run normally. Use private credentials, not shared examples. If any ADMIN already exists, including inactive accounts, this command refuses to run. Account recovery is a different approved procedure.

## Native Windows setup

With an ignored .env configured for the local development profile:

```powershell
.\scripts\start-local.ps1 -BootstrapAdmin
```

The wrapper starts/waits for the existing Compose postgres, asks for email/display name and a hidden password plus confirmation, then invokes the offline CLI. To use an already running configured database, add -SkipDatabase. Secrets are process environment, never command-line arguments or logs; wrapper-created bootstrap variables are removed afterward.

## Packaged application

After building the current jar, run it with the intended environment supplied externally:

```text
java -jar target/campus-service-0.0.1-SNAPSHOT.jar --bootstrap-admin
```

An interactive console asks for credentials with hidden password entry. Without a console, a private setup wrapper may supply CAMPUS_BOOTSTRAP_EMAIL, CAMPUS_BOOTSTRAP_DISPLAY_NAME and CAMPUS_BOOTSTRAP_PASSWORD in process environment. Do not put these in tracked files, terminal history or public Compose configuration. Never pass a password as an argument. Docker one-command demo wrapping remains a separate Phase 8 slice until verified.

## Behavior and verification

The command runs no HTTP listener, obtains the existing ADMIN guard, validates email/display name/password using the existing Identity rules and delegating bcrypt encoder, and saves an ACTIVE ADMIN plus self-attributed USER_CREATED audit in one transaction. It closes its context/pool after completion. Failure rolls back account and role writes; no schema/volume deletion or Flyway repair is performed.

Sign in through the normal application and verify authorized account access. Additional administrators are created through the authenticated ADMIN API. Record safe completion evidence without passwords, hashes, JWTs, cookies, signing keys or connection secrets. Do not bypass final-active-administrator protection for recovery.

Demo setup generates per-installation credentials in ignored private files when automated fixture accounts are requested; these are fictional demonstration accounts, not a production seed policy. Scope and actual gate status are recorded in the [Phase 8 plan](../plans/phase-8-9-local-demo.md).
