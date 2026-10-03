# Campus Service

Campus Service is a production-oriented Intelligent Smart Campus Platform. It is being prepared as a Java backend that gives university operations a consistent, secure foundation rather than a collection of disconnected systems.

## Problem Statement

University operations commonly span separate processes for identity, academic records, accommodation, finance, communications, and reporting. Campus Service aims to provide a coherent backend platform for those capabilities while preserving clear domain ownership and operational reliability.

## Product Vision

Build an extensible, maintainable platform that can support campus operations today and workflow and AI-assisted capabilities later, without introducing distributed-system complexity before it is justified.

## Implemented Scope

The implemented backend includes the Phase 1 platform and Identity foundations, Phase 2 organization and people registries, and Academic catalogs, terms, course offerings, class sections and administrative enrollment. Campus operations and frontend remain future work. This personal project is developed locally; production deployment is not a completion requirement for these phases.

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

**Phase 3D: Academic hardening — review PASS.** Phase 1, Phase 2 and Phase 3A–3C are merged into `main`. Phase 3D completes the approved Academic foundation with transactional audit for all administrative mutations, OpenAPI Bearer requirements, exact V17→V18 schema validation, query/index evidence and cross-module regression guards. Final clean verify: 200 tests, no failures/errors/skips, 4m56s on 2026-10-04. See the [Phase 3D plan](docs/plans/phase-3d-academic-hardening.md), [final Phase 1–3 review](docs/reviews/phase-3d-final-review.md), [Academic API contract](docs/api/academic.md) and [Testing](docs/testing.md).

[Phase 3A catalog APIs](docs/plans/phase-3a-academic-catalog.md) remain available under `/api/v1/admin/academic/programs` and `/courses`, with normalized unique codes, credits 1–30 and paginated search. [Phase 3B delivery APIs](docs/plans/phase-3b-academic-offerings.md) manage terms, offerings and sections. [Phase 3C enrollment](docs/plans/phase-3c-academic-enrollment.md) supports withdrawal/re-enrollment with version and capacity protection. Student self-service, waitlists, grading, fees and schedules remain future scope.

Administrator APIs require `ROLE_ADMIN`. Identity authentication, session revocation and admin management, plus organization/student/faculty-staff registries, remain available. Runtime administrator provisioning and production deployment stay outside this implementation scope.

## Planned Phases

1. Phase 0: product scope, architecture baseline, conventions, test strategy, ADRs, and agent rules.
2. Phase 1: Spring Boot bootstrap, shared kernel, Identity and Access, PostgreSQL, Flyway, and authentication/authorization tests.
3. Phase 2: Organization, Student and Faculty/Staff registries with query, audit and migration hardening.
4. Phase 3: Academic catalog (3A), terms/class sections (3B), enrollment (3C), and Academic hardening (3D).
5. Later: selected Dormitory/Finance and supporting modules; reporting; frontend; end-to-end release readiness; optional workflow/AI.

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
