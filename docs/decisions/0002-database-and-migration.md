# ADR 0002: PostgreSQL and Flyway

## Status

Accepted

## Context

CampusOne requires a reliable relational data store for identity and future university operational records, plus a reproducible way to evolve schemas across local, test, and deployed environments. Unmanaged ORM schema generation does not provide an adequate audit trail or controlled upgrade path.

## Decision

Use PostgreSQL as the initial relational database and Flyway for versioned schema migrations. Each module owns its tables and migrations. Migrations will be ordered, reviewed, committed with the code that needs them, and tested against a clean PostgreSQL database.

Applied migrations are immutable outside disposable local development. Correct schema issues with new migrations rather than modifying a migration that may already have run elsewhere.

## Consequences

- Schema evolution is explicit, reproducible, and reviewable.
- Integration tests must validate migrations against PostgreSQL behavior, preferably using Testcontainers.
- Module data ownership must be designed deliberately; direct cross-module table access is not an acceptable convenience shortcut.
- Developers need access to PostgreSQL-compatible local or test infrastructure once Phase 1 starts.

## Alternatives Considered

- **Hibernate/JPA schema generation as the migration mechanism:** rejected because it lacks controlled, auditable schema history.
- **Database-agnostic embedded database for integration confidence:** rejected because it can diverge from PostgreSQL behavior.
- **NoSQL as the primary store:** rejected for the initial operational record model, which benefits from relational integrity and transactions.

## Review Conditions

Review this decision if verified workload, data-model, tenancy, regulatory, availability, or integration requirements cannot be met by PostgreSQL and Flyway. Any change must define data migration, rollback, retention, and test strategy.
