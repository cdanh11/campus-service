# Local Campus Platform demo

This guide runs the two existing repositories locally. The [Phase 8–9 plan](../plans/phase-8-9-local-demo.md) defines the remaining test/demo gates; running the servers is only the startup step.

For a new demo machine, use [the Docker route](docker-demo.md): `scripts/start-demo.ps1 -Seed` builds both repositories without host Java/Node, creates private per-installation accounts and loads the substantial fixtures. The native development route below remains available.

## Prerequisites

- Sibling campus-service and campus-client checkouts, Java 21, Node >=24.13 within major 24, npm, Docker Desktop running.
- Backend ignored .env with SPRING_PROFILES_ACTIVE=local, configured PostgreSQL connection and a valid Base64 JWT key of at least 32 random bytes. Do not publish the file or key. Example placeholders are not usable secrets.
- An ADMIN account. On a new installation, use `scripts/start-local.ps1 -BootstrapAdmin` as described in the [provisioning runbook](initial-admin-provisioning.md). No public bootstrap endpoint is provided. Do not put account passwords in screenshots or documents.
- Ports 8080 and 3000 available; PostgreSQL port matches .env. Compose uses a Campus Service volume; never delete it to troubleshoot. Other Docker projects may use other ports.

## Start

In campus-service:

```powershell
.\scripts\start-local.ps1
```

The script imports simple NAME=value lines from .env into its own process, validates local profile/key presence, starts only Compose postgres with --wait and then runs .\mvnw.cmd spring-boot:run. Flyway and Hibernate validation remain enabled. No database, volume or account is deleted/created by a reset procedure.

In a second terminal, in campus-client:

```powershell
npm ci # first setup or dependency changes
npm run dev
```

Open http://localhost:3000 and sign in with the existing local account. Vite proxies /api to http://localhost:8080. Browser authentication still uses the normal API and HttpOnly cookie.

## Check and stop

- Backend: GET http://localhost:8080/actuator/health should return UP.
- Frontend: http://localhost:3000 should load the login page.
- Login and an authorized account read confirm the normal API path; health/HTML alone do not prove all business flows.
- Stop each application with Ctrl+C in its terminal. Leave PostgreSQL running, or run docker compose --env-file .env stop postgres. Never use down -v or volume deletion.

If startup fails, check Docker readiness, occupied ports, matching database credentials, local profile and a real JWT key. Do not print .env or bypass migration validation. A 401 at login needs valid local credentials; do not weaken authorization. Blank registry screens can simply mean no demo data yet.

## Test commands

Backend: .\mvnw.cmd clean verify. Frontend: npm run verify and npm run test:e2e. Real-backend browser suite: .\scripts\test-backend.ps1 from campus-client against a clean backend checkout; stop local Vite first because it uses the same port. Testcontainers databases are separate from the developer/demo database.

Runtime evidence belongs in a dated review; this guide does not claim the Phase 8 acceptance matrix is complete.
