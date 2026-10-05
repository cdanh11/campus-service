# Phases 8 and 9 — Local validation and project demonstration

Scope revised by the project owner on 2026-10-05. Phase 7 frontend is complete. This plan replaces the previous production-release/Workflow-AI direction for the remaining personal-project phases. Approval of the plan is not a claim that its gates have passed.

## Goals and boundaries

Finish a demonstrable personal campus project using the existing campus-service backend and campus-client frontend as sibling repositories. Keep the current stack and approved business rules. The required deliverable is a reproducible local demo with test evidence and clear presentation, not a production deployment.

Paid hosting, production secrets/backup/monitoring exercises, new infrastructure, Workflow automation, AI/RAG/GraphRAG and unapproved APIs are outside these phases. Existing optional production runbooks remain reference material. No migration repair, database/volume deletion or public first-administrator endpoint is part of the demo workflow.

## Phase 8 — End-to-end testing and demo quality

### 8A — Portable demo setup and provisioning

The owner approved completion of Phase 8 with substantial fictional demo data on 2026-10-05. Docker Compose packaging should make the two sibling repositories runnable on another machine using Docker/Git, without host Java/Node requirements. Preserve the native development commands too.

- 8A1: explicit offline first-administrator command, hidden terminal input or private process environment, production password validation/encoder, admin guard locking, refusal when any ADMIN exists (including inactive), atomic self-attributed initial audit and no public provisioning endpoint. The command is portable across configured environments; normal web startup never provisions accounts automatically.
- 8A2: permission matrix and enforced backend/frontend roles for organization, Student, personnel, Academic, Dormitory, Finance, Notification, Event and Library administration, plus separate audit/report read permissions. ADMIN alone manages accounts/roles and retains full access. Necessary reference reads are documented explicitly; role-based function access does not claim per-faculty row scoping or approval workflows.
- 8A3: Docker demo runner, independent persistent PostgreSQL volume, generated private per-installation configuration, frontend proxy, bounded readiness checks, clear stop/restart instructions and no destructive reset. Two global ADMIN plus scoped accounts are demo fixtures, not a shared production credential.
- Each slice needs its own tests/review PASS before dependent work. All released V1–V25 remain immutable; approved new roles use a new migration and exact upgraded-schema validation.

### Demo data inventory (part of Phase 8)

Use fictional names and reserved example.test emails; create data through approved application/HTTP use cases. Do not write business tables from another module or weaken validation to load fixtures. Demo loading is explicit, never automatic on normal startup, and refuses non-demo targets.

Target inventory: two global ADMIN, at least one account per scoped role, 200 Student profiles (with linked USER accounts), about 80 personnel, 16 organization units, 12 programs, 60 courses, three terms, 90 offerings, 150 sections and 800 retained enrollments. Dormitory: at least four buildings, 80 rooms/240 beds and 100 current stays with release history. Finance: multiple fee definitions, roughly 600 charges and 450 receipts including partial/paid/unpaid/reversed examples. Supporting services: 24 published notices and substantial deliveries, 16 events and 400 retained memberships, 120 titles/360 copies with roughly 200 retained loans. These are targets pending owner-contract checks; report actual inventory, enforce capacity/foreign-key/business rules and include meaningful status diversity.

Credential material stays in ignored per-installation files/process environment and is never printed in logs or committed. Seed verification checks actual persisted inventory/relationships and repeat-run behavior; a green script exit alone is insufficient. No database/volume deletion is permitted.

### 8B — API and UI acceptance journeys

Build a traceable checklist for ADMIN and regular accounts, mapping screen -> owner API -> expected result -> actual evidence. Cover Identity/People, Academic, Dormitory, Finance, Notification, Event, Library, Audit and Reporting. Include normal writes/reads, validation, duplicate/conflict/stale writes, permissions, capacity, retained history and precise VND/version values.

Keep personal portal scope at own inbox and Event membership. Do not simulate unsupported Student Academic/Dormitory/Finance/Library APIs. Include logout/reload/session expiry, two tabs, empty/error/loading states, search/paging/sort and keyboard/mobile behavior. Separate manual exploration, mocked tests and real-backend E2E evidence.

### 8C — Regression and closure

Run backend clean verify with Docker, frontend verify and mocked browser suites, then the real-backend suite using a clean isolated checkout. Retain genuine migration upgrade and exact Hibernate validation checks. Fix FAIL findings and rerun affected verification before closure; do not weaken assertions or classify a tool timeout as BUILD FAILURE.

Review the exact source/diff, API contracts, documentation links/commands and selected representative UI views. Record real totals, durations, tested commits and limitations in a Phase 8 review. PASS requires the approved acceptance matrix and relevant regressions to pass with no unresolved blocker/major. Production uptime, load SLA and cross-browser certification are not claimed.

## Phase 9 — Demo package and portfolio handoff

### 9A — Demo story and sample data

Prepare a short ADMIN and Student walkthrough using fictional local data, clear starting conditions and expected outcomes. Include the problem, user roles, business safeguards and one meaningful end-to-end story rather than listing screens. Keep passwords, JWTs, personal data and environment values out of screenshots/video/repository files. Any new seed/reset mechanism needs its own explicit design review; no destructive reset is assumed.

### 9B — Presentation and repository entry points

Complete README/navigation, setup/troubleshooting, architecture diagram, representative screenshots or a short video, API/test evidence and a concise CV description. Link both repositories from one clear product entry point; a third repository or paid website is optional. State implemented scope and limits accurately, including manual payments and the current personal portal boundaries.

### 9C — Final rehearsal and handoff

Rehearse startup and the selected demo from documented prerequisites, recheck links and artifacts, and review the final diffs. Rerun behavioral tests when code/configuration changed; do not invent new results for documentation-only edits. Resolve FAIL before the final PASS. The user owns PR/merge and any future publication/deployment decision.

## Delivery rules

Each slice follows inspect -> plan -> implement -> verify -> review -> report, with a PASS/FAIL gate before the next. Use feature/<function-or-phase> branches and functional commits after PASS. Preserve unrelated changes and keep the backend project-roadmap.md local/untracked. Update current status in place and retain dated checkpoint evidence in reviews.

Current status: [8A1 bootstrap](../reviews/phase-8a1-bootstrap-review.md) PASS with final 25 focused tests and native runtime login evidence. Two global demo ADMIN and eleven scoped/viewer demo accounts exist. [8A2 permissions](../reviews/phase-8a2-permissions-review.md) PASS with 101 client tests, 13 mocked and 25 real browser journeys. Portable Docker setup (8A3), the substantial data inventory and 8B/8C remain incomplete. Phase 9 is not implemented. No future gate is marked PASS in advance.
