# Architecture Decision Records

Architecture Decision Records (ADRs) capture consequential technical decisions, the context in which they were made, and the conditions that would justify revisiting them. They prevent undocumented assumptions from becoming accidental architecture.

## Status

Use one of these statuses near the top of every ADR:

- **Proposed:** under consideration and not yet binding.
- **Accepted:** approved and currently in effect.
- **Superseded:** replaced by a later ADR, linked from this record.
- **Deprecated:** no longer recommended, without a direct replacement.

## Numbering and Format

Name files `NNNN-short-title.md` using four-digit, increasing numbers. Do not renumber existing records. Use this structure:

1. Title
2. Status
3. Context
4. Decision
5. Consequences
6. Alternatives considered
7. Review conditions

Create an ADR when a decision changes module boundaries, persistence strategy, security posture, public API contracts, operational complexity, or a similarly durable concern. Update the affected ADR status instead of rewriting history when a later decision replaces it.

## Index

- [0001: Modular Monolith First](0001-modular-monolith-first.md)
- [0002: PostgreSQL and Flyway](0002-database-and-migration.md)
- [0003: Stateless Access Tokens and Rotating Refresh Cookies](0003-authentication-token-strategy.md)
- [0004: Administrator User Management Policy](0004-admin-user-management-policy.md)
