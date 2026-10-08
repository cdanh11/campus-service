# Phase 8A local startup checkpoint

> Historical pre-bootstrap checkpoint (21:22 on 2026-10-05). First-account setup was subsequently completed; see the [8A1 review](phase-8a1-bootstrap-review.md). The open prerequisite below describes the earlier checkpoint, not current account inventory.

Status: startup checks PASS; full 8A is INCOMPLETE pending an approved local first-administrator bootstrap procedure. This is not Phase 8 closure or business-journey acceptance.

Reviewed 2026-10-05 on feature/local-demo-test-plan. Backend source baseline is 795588e; frontend is merged main d1d7906 before the current documentation changes. Existing user changes in application.yml and application-local.yml are preserved and used by the local run, not included as new reviewed code changes.

## Scope and evidence

- Revised the remaining roadmap to local validation/demo quality (8) and demo/portfolio handoff (9), with no required production operations or Workflow/AI. Current README/agent/development/testing references and phase checkpoint notices were reviewed.
- Added scripts/start-local.ps1: imports ignored environment values without printing them, requires local profile and a valid Base64 key, waits for only the Campus Service Compose PostgreSQL service and runs the existing Maven Spring Boot command. PowerShell parser validation PASS and actual startup PASS.
- Replaced only the placeholder local JWT key in ignored .env with 32 random bytes; the key is not tracked or reported. No database/volume was deleted and no administrator was created.
- Existing Campus Service PostgreSQL volume was retained. Normal Flyway startup upgraded the configured database to V25; Hibernate schema validation completed and Tomcat started on 8080. Startup duration reported by Spring Boot: 23.824s. Maven is running the application; this is not a completed clean verify or a new BUILD SUCCESS test result.
- Backend GET /actuator/health: UP. Frontend GET http://localhost:3000/: HTTP 200. Vite startup: 1.713s. GET /api/v1/auth/me through the frontend proxy without authentication: HTTP 401 as expected.
- Read-only count in the configured local Campus Service database found zero Identity users. The owner corrected an accidental earlier answer: no local ADMIN has been created. A local-only bootstrap procedure has been proposed and awaits explicit approval before any account insertion. No account email, password/hash or token was read or printed.

## Review limits

No full test suite was rerun for Markdown changes. The startup script was syntax-checked and executed; the app/API/security implementation was not changed. Normal migrations and Hibernate startup are verified, while login and business journeys remain unverified for this local database. Historical test counts remain in their checkpoint reviews.

Documentation links and working diffs are checked before delivery. Both applications remain running for the owner to inspect. Complete the approved first-account prerequisite before marking 8A PASS or advancing to 8B acceptance work.
