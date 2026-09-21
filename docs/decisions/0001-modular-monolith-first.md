# ADR 0001: Modular Monolith First

## Status

Accepted

## Context

Campus Service spans several university domains, but the initial release has no demonstrated need for independently deployable services. Starting with microservices would add network contracts, distributed data consistency, deployment, observability, and operational burdens before the first business capability is proven.

## Decision

Campus Service will begin as one Spring Boot deployable organized as a modular monolith. Modules will align to business domains, own their internal persistence and implementation details, and communicate through explicit application-level contracts. The first implementation module is Identity and Access.

Message brokers, service discovery, Kubernetes, and other distributed-system infrastructure are excluded from the initial source structure.

## Consequences

- Development, testing, local operation, and deployment remain simpler during early phases.
- Module boundaries must be actively enforced in code reviews to avoid a coupled monolith.
- Cross-module transactions can remain local initially, but future extraction will require deliberate data and integration design.
- A single deployment unit means independent scaling and releases are not available until justified extraction occurs.

## Alternatives Considered

- **Microservices from the start:** rejected because current scope does not justify distributed operational cost.
- **Layered monolith without domain modules:** rejected because it weakens ownership and makes later extraction harder.
- **Separate applications per domain now:** rejected because the domains and their operating needs are not yet validated.

## Review Conditions

Review this decision when a module has evidence of independent scaling, ownership, release cadence, reliability isolation, or integration requirements. Any extraction requires an ADR covering interfaces, data ownership, migration, deployment, and observability.
