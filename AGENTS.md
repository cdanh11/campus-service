# Campus Service Agent Instructions

## Purpose and Phase

Campus Service is the backend of a personal Campus Platform project. Backend Phases 1–6 and the Phase 7 OpenAPI prerequisite are reviewed PASS. Phase 6 is merged at 7d130f4; contract correction is at 795588e. Approved frontend Phase 7 is complete in the sibling campus-client repository. Phase 8 is reviewed PASS locally in docs/reviews/phase-8-acceptance-review.md; Phase 9 now focuses on portfolio handoff and final rehearsal; follow docs/plans/phase-8-9-local-demo.md. Workflow/AI and production operations are optional future work, not completion requirements. Read README.md, current plans/reviews and Git for authoritative evidence. Never mark an unverified gate complete or provision a first administrator opportunistically.

## Repository Structure

- `README.md`: product scope and project status.
- `docs/`: architecture, development, testing, and ADRs.
- `.opencode/agents/`: role-specific agent definitions.
- `.env.example`: non-secret environment variable names and placeholders.

## Required Workflow

Follow: **inspect -> plan -> implement -> build/test -> review diff -> report**. Adapt the build/test step to the repository state. Do not invent build, run, test, migration, or formatting commands until `pom.xml` exists.

Each phase or subphase requires its own PASS/FAIL review before the next slice; resolve FAIL findings first. Commit by function after PASS when authorized. These rules continue through backend and frontend work.

## Architecture and Domains

- Start as a modular monolith; do not introduce microservices, message brokers, Kubernetes, or other distributed infrastructure without an approved need.
- Keep domain modules independent. A module must not access another module's persistence internals.
- Implement only the approved phase plan. Do not implement future domains opportunistically.
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

- Use PostgreSQL and Flyway for approved persistence work.
- Treat applied migrations as immutable; add corrective migrations instead of editing released ones.
- Add proportionate tests for changed behavior, including authorization and migration effects where applicable.
- Never claim tests passed unless they were actually run and their result was verified.

## Git and Safety

- Do not push, deploy, rewrite history, force operations, or run destructive Git or filesystem commands without explicit user approval.
- Use the user's requested `feature/<function-or-phase>` branch naming.
- `docs/plans/project-roadmap.md` is local tracking: preserve/update it, but do not stage, commit or push it unless the user changes that instruction.
- Inspect `git status` and the diff before reporting completion.

## Definition of Done

Work is complete when requirements are met, affected documentation or ADRs are updated, relevant validation has run or its absence is stated, the diff is reviewed, and the final report accurately lists changes and limitations.
