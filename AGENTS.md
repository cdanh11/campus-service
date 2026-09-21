# Campus Service Agent Instructions

## Purpose and Phase

Campus Service is an Intelligent Smart Campus Platform. Phase 1A provides the Spring Boot bootstrap, PostgreSQL migration baseline, Actuator health endpoint, and Testcontainers integration tests. Identity and Access is not implemented yet.

## Repository Structure

- `README.md`: product scope and project status.
- `docs/`: architecture, development, testing, and ADRs.
- `.opencode/agents/`: role-specific agent definitions.
- `.env.example`: non-secret environment variable names and placeholders.

## Required Workflow

Follow: **inspect -> plan -> implement -> build/test -> review diff -> report**. Adapt the build/test step to the repository state. Do not invent build, run, test, migration, or formatting commands until `pom.xml` exists.

## Architecture and Domains

- Start as a modular monolith; do not introduce microservices, message brokers, Kubernetes, or other distributed infrastructure without an approved need.
- Keep domain modules independent. A module must not access another module's persistence internals.
- Phase 1 is limited to shared foundations and Identity and Access. Do not implement future domains opportunistically.
- Record consequential architectural changes in an ADR.

## Coding Principles

- Prefer small, explicit, maintainable changes.
- Follow established conventions once application code exists.
- Preserve unrelated user changes.
- Do not claim features are implemented when they are only planned.

## Security and Data

- Never commit, print, or expose secrets, tokens, credentials, or private data.
- Use `.env.example` for safe placeholders only; never create a tracked `.env`.
- Use JWT and authorization decisions only as documented or approved; do not weaken access controls for convenience.

## Database and Tests

- Use PostgreSQL and Flyway for approved Phase 1 persistence work.
- Treat applied migrations as immutable; add corrective migrations instead of editing released ones.
- Add proportionate tests for changed behavior, including authorization and migration effects where applicable.
- Never claim tests passed unless they were actually run and their result was verified.

## Git and Safety

- Do not push, deploy, rewrite history, force operations, or run destructive Git or filesystem commands without explicit user approval.
- Inspect `git status` and the diff before reporting completion.

## Definition of Done

Work is complete when requirements are met, affected documentation or ADRs are updated, relevant validation has run or its absence is stated, the diff is reviewed, and the final report accurately lists changes and limitations.
