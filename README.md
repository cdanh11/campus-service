# CampusOne

CampusOne is a production-oriented Intelligent Smart Campus Platform. It is being prepared as a Java backend that gives university operations a consistent, secure foundation rather than a collection of disconnected systems.

## Problem Statement

University operations commonly span separate processes for identity, academic records, accommodation, finance, communications, and reporting. CampusOne aims to provide a coherent backend platform for those capabilities while preserving clear domain ownership and operational reliability.

## Product Vision

Build an extensible, maintainable platform that can support campus operations today and workflow and AI-assisted capabilities later, without introducing distributed-system complexity before it is justified.

## Initial Scope

Phase 1 will bootstrap the backend and establish Identity and Access: user and role management, authentication, authorization, PostgreSQL persistence, Flyway migrations, and foundational tests. No business-domain modules are implemented yet.

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

## Proposed Technology Baseline

The following technologies are proposed for the initial backend. Exact versions will be selected and verified during Spring Boot bootstrap.

- Java 21 and Spring Boot 3.x
- Maven
- PostgreSQL, Spring Data JPA, and Flyway
- Spring Security with JWT-based authentication
- Bean Validation
- OpenAPI/Swagger
- JUnit 5, Mockito, Spring Boot Test, and Testcontainers
- Docker and Docker Compose for local infrastructure

Redis is not a baseline dependency. It will be introduced only for a concrete caching, token, rate-limiting, or temporary-data requirement.

## Architecture

CampusOne will begin as a modular monolith: one deployable application with explicit module boundaries and controlled dependencies. This keeps the first release straightforward to develop and operate while allowing proven modules to be extracted later if required. See [Architecture](docs/architecture.md) and [ADR 0001](docs/decisions/0001-modular-monolith-first.md).

## Status

**Phase 0: repository foundation.** This repository currently contains documentation and AI-agent configuration only. No Spring Boot application, database schema, local infrastructure, or production deployment configuration has been generated.

## Planned Phases

1. Phase 0: product scope, architecture baseline, conventions, test strategy, ADRs, and agent rules.
2. Phase 1: Spring Boot bootstrap, shared kernel, Identity and Access, PostgreSQL, Flyway, and authentication/authorization tests.
3. Later: Student and Faculty; Academic; Dormitory and Finance; supporting modules; observability and deployment; workflow and AI capabilities.

## Documentation Map

- [Architecture](docs/architecture.md): system boundaries and technical direction.
- [Development](docs/development.md): local workflow and repository conventions.
- [Testing](docs/testing.md): test strategy and evidence expectations.
- [Architecture Decision Records](docs/decisions/README.md): durable technical decisions.
- [Agent Instructions](AGENTS.md): concise operating rules for coding agents.

Setup commands will be added after the Spring Boot application and its Maven build are generated. Until then, no build or run command is valid for this repository.
