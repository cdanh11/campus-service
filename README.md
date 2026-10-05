# Campus Service

Campus Service is the Java backend of a personal Campus Platform project. It provides secure, consistent campus operations through a modular monolith and is demonstrated locally with the separate Campus Client frontend.

## Problem Statement

University operations commonly span separate processes for identity, academic records, accommodation, finance, communications, and reporting. Campus Service aims to provide a coherent backend platform for those capabilities while preserving clear domain ownership and operational reliability.

## Product Vision

Build a maintainable campus platform with clear domain ownership, reliable business rules and a reproducible local demonstration backed by test evidence. Workflow/AI are optional future extensions, outside the approved completion roadmap.

## Implemented Scope

The implemented backend covers platform/Identity, organization/people registries, Academic catalog/delivery/enrollment/audit, Dormitory inventory/current accommodation, Finance fee snapshots/manual receipts/reversal, Notification, Event membership, Library circulation, ADMIN audit viewing and Phase 6 Reporting/dashboard/CSV/operational observability. Each phase has separate review evidence. The approved Phase 7 frontend is complete in [campus-client](https://github.com/cdanh11/campus-client). Phases 8 and 9 focus on end-to-end testing, local demo quality and portfolio handoff; production deployment is optional.

## Domain Roadmap

**Core domains**

- Identity and Access
- Student
- Faculty and Staff
- Academic
- Dormitory
- Finance

**Supporting domains**

- Library
- Event
- Notification
- Audit
- Reporting and Analytics

Workflow Automation and AI/RAG/GraphRAG are optional ideas after the current project is complete, not Phase 9 requirements.

## Technology Baseline

The initial backend uses the following baseline. Dependency versions are managed by Spring Boot unless stated otherwise.

- Java 21 and Spring Boot 3.5.16
- Maven
- PostgreSQL, Spring Data JPA, and Flyway
- Spring Security with JWT-based authentication
- Bean Validation
- OpenAPI/Swagger
- JUnit 5, Mockito, Spring Boot Test, and Testcontainers
- Docker and Docker Compose for local infrastructure

Redis is not a baseline dependency. It will be introduced only for a concrete caching, token, rate-limiting, or temporary-data requirement.

## Architecture

Campus Service will begin as a modular monolith: one deployable application with explicit module boundaries and controlled dependencies. This keeps the first release straightforward to develop and operate while allowing proven modules to be extracted later if required. See [Architecture](docs/architecture.md) and [ADR 0001](docs/decisions/0001-modular-monolith-first.md).

## Status

**Backend Phases 1–6 and the Phase 7 contract prerequisite are reviewed PASS.** Phase 6 is merged at 7d130f4; owner-qualified OpenAPI schemas are delivered at 795588e. The separate frontend Phase 7 and its documentation corrections are complete and merged. Historical Maven totals remain in [Testing](docs/testing.md) and the [contract review](docs/reviews/phase-7-api-contract-review.md).

The approved remaining work is [Phase 8 testing/demo quality and Phase 9 portfolio handoff](docs/plans/phase-8-9-local-demo.md). These gates are not yet complete. Reports reflect current state, not historical reconstruction; manual receipts are not a payment gateway. Hosting and Workflow/AI are outside this completion scope.

[Phase 3A catalog APIs](docs/plans/phase-3a-academic-catalog.md) remain available under `/api/v1/admin/academic/programs` and `/courses`, with normalized unique codes, credits 1–30 and paginated search. [Phase 3B delivery APIs](docs/plans/phase-3b-academic-offerings.md) manage terms, offerings and sections. [Phase 3C enrollment](docs/plans/phase-3c-academic-enrollment.md) supports withdrawal/re-enrollment with version and capacity protection. Academic Student self-service, waitlists, grading, automatic tuition calculation and schedules remain future scope.

Administrator APIs use the [functional permission matrix](docs/permissions.md): global ADMIN manages accounts/roles; scoped operators manage their own domain with explicit read references. Phase 8A2 verification is in progress. Identity authentication, session revocation and admin management, plus organization/student/faculty-staff registries, remain available. On a new installation, create the first administrator with the [explicit CLI](docs/runbooks/initial-admin-provisioning.md). No public first-administrator endpoint is provided; production provisioning and deployment remain optional separate work.

## Planned Phases

1. Phase 0: product scope, architecture baseline, conventions, test strategy, ADRs, and agent rules.
2. Phase 1: Spring Boot bootstrap, shared kernel, Identity and Access, PostgreSQL, Flyway, and authentication/authorization tests.
3. Phase 2: Organization, Student and Faculty/Staff registries with query, audit and migration hardening.
4. Phase 3: Academic catalog (3A), terms/class sections (3B), enrollment (3C), and Academic hardening (3D).
5. Phase 4: Dormitory inventory (4A1), accommodation assignment (4A2), Finance obligations (4B1), manual payments (4B2), operations hardening (4C).
6. Phase 5: Notification (5A), Event (5B), Library (5C), ADMIN audit viewing (5D), regression closure (5E).
7. Phase 6: Reporting/dashboard, bounded CSV and observability — complete.
8. Phase 7: separate frontend, ADMIN surfaces and own inbox/Event portal — complete.
9. Phase 8: local startup, API/UI acceptance and full regression — approved, not complete.
10. Phase 9: demo walkthrough, screenshots/video, documentation/CV and final rehearsal — approved, not complete.

## Run the local project

Configure an ignored `.env` from `.env.example` with local database values and a real Base64 JWT key containing at least 32 random bytes. Keep passwords/keys private. In this repository, run:

```powershell
.\scripts\start-local.ps1
```

The script imports `.env` into its process, starts only the Compose PostgreSQL service with its existing volume, waits for readiness and runs Maven Spring Boot in the local profile. Normal startup does not create an ADMIN or delete data. For first-account setup, run it with `-BootstrapAdmin` to enter credentials privately. In a second terminal, run `npm run dev` from the sibling `campus-client` folder (after `npm ci` on first setup). Open http://localhost:3000; the backend uses port 8080 by default. See the [local demo guide](docs/runbooks/local-demo.md).

## Documentation Map

- [Phase 8–9 scope](docs/plans/phase-8-9-local-demo.md): testing, demo and portfolio handoff.
- [Local demo](docs/runbooks/local-demo.md): startup and checks.
- [Architecture](docs/architecture.md): system boundaries and technical direction.
- [Development](docs/development.md): local workflow and repository conventions.
- [Testing](docs/testing.md): test strategy and evidence expectations.
- [Initial Administrator Provisioning](docs/runbooks/initial-admin-provisioning.md): safe operational handoff for the first administrator.
- [Phase 1 Release](docs/runbooks/phase-1-release.md): release gate, runtime configuration, deployment verification, and recovery guidance.
- [Architecture Decision Records](docs/decisions/README.md): durable technical decisions.
- [Agent Instructions](AGENTS.md): concise operating rules for coding agents.

The verified Windows build command is:

```powershell
.\mvnw.cmd clean verify
```

It compiles with Java 21 and runs PostgreSQL Testcontainers integration tests. Docker Compose provides optional local PostgreSQL; verification uses isolated PostgreSQL Testcontainers.
