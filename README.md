# Campus Service

Campus Service is a production-oriented Intelligent Smart Campus Platform. It is being prepared as a Java backend that gives university operations a consistent, secure foundation rather than a collection of disconnected systems.

## Problem Statement

University operations commonly span separate processes for identity, academic records, accommodation, finance, communications, and reporting. Campus Service aims to provide a coherent backend platform for those capabilities while preserving clear domain ownership and operational reliability.

## Product Vision

Build an extensible, maintainable platform that can support campus operations today and workflow and AI-assisted capabilities later, without introducing distributed-system complexity before it is justified.

## Implemented Scope

The implemented backend covers platform/Identity, organization/people registries, Academic catalog/delivery/enrollment/audit, Dormitory inventory/current accommodation, Finance fee snapshots/manual receipts/reversal, Notification, Event membership, Library circulation, ADMIN audit viewing and Phase 6 Reporting/dashboard/CSV/operational observability. Each phase has separate review evidence. Frontend remains incomplete; production deployment is not a completion requirement for this local personal project.

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

**Advanced future domains**

- Workflow Automation
- AI Assistant
- RAG
- GraphRAG

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

**Phase 6 — backend complete, review PASS.** Phase 5 is merged into main (38c7228). Phase 6 is delivered on feature/reporting: ADMIN eight-group dashboard, Student VND debt/current accommodation/section enrollment/Event membership/OPEN and overdue Library reports, typed owner contracts and bounded formula-safe CSV. Safe request correlation, structured completion events and in-process metrics are implemented; public Actuator remains health only. Final clean verify: **BUILD SUCCESS, 416 tests/74 suites, zero failures/errors/skips, 10m12s**, finished 2026-10-04T21:07:26+07:00; XML independently checked, 48 pools close cleanly. V1–V25 unchanged; no new schema/entity. See [Phase 6 plan](docs/plans/phase-6-reporting.md), [final review](docs/reviews/phase-6-final-review.md), [Reporting API](docs/api/reporting.md), [observability and measured limits](docs/runbooks/reporting-observability.md) and [Testing](docs/testing.md). Reports are current-state, not historical reconstruction; load evidence is local application/database evidence, not a production SLA. Phase 7 frontend requires its own approved stack and use cases.

[Phase 3A catalog APIs](docs/plans/phase-3a-academic-catalog.md) remain available under `/api/v1/admin/academic/programs` and `/courses`, with normalized unique codes, credits 1–30 and paginated search. [Phase 3B delivery APIs](docs/plans/phase-3b-academic-offerings.md) manage terms, offerings and sections. [Phase 3C enrollment](docs/plans/phase-3c-academic-enrollment.md) supports withdrawal/re-enrollment with version and capacity protection. Academic Student self-service, waitlists, grading, automatic tuition calculation and schedules remain future scope.

Administrator APIs require `ROLE_ADMIN`. Identity authentication, session revocation and admin management, plus organization/student/faculty-staff registries, remain available. Runtime administrator provisioning and production deployment stay outside this implementation scope.

## Planned Phases

1. Phase 0: product scope, architecture baseline, conventions, test strategy, ADRs, and agent rules.
2. Phase 1: Spring Boot bootstrap, shared kernel, Identity and Access, PostgreSQL, Flyway, and authentication/authorization tests.
3. Phase 2: Organization, Student and Faculty/Staff registries with query, audit and migration hardening.
4. Phase 3: Academic catalog (3A), terms/class sections (3B), enrollment (3C), and Academic hardening (3D).
5. Phase 4: Dormitory inventory (4A1), accommodation assignment (4A2), Finance obligations (4B1), manual payments (4B2), operations hardening (4C).
6. Phase 5: Notification (5A), Event (5B), Library (5C), ADMIN audit viewing (5D), regression closure (5E).
7. Later: reporting; frontend; end-to-end release readiness; optional workflow/AI.

## Documentation Map

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
