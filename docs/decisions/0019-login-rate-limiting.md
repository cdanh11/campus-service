# ADR 0019: In-Process Throttling for Credential Endpoints

## Status

Accepted for the monolith hardening slice.

## Context

ADR 0003 deferred rate limiting at an approved boundary. Login and refresh are the only endpoints that verify credentials or rotate a long-lived cookie, so they are the first targets for brute-force and credential-stuffing attempts. The deployment remains a single Spring Boot instance with PostgreSQL; no Redis or gateway is approved.

## Decision

Campus Service applies a fixed-window limiter inside the application process to `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh`, keyed by client address and endpoint. Exceeding the limit returns `429 RATE_LIMITED` with a `Retry-After` header in the standard error envelope. The limiter is enabled by the `production` profile with 20 login and 60 refresh attempts per minute per client address; other profiles keep it disabled so tests and local demos are not throttled. The window state is bounded in memory and expires after one minute. That profile also enables `server.forward-headers-strategy: native`, so Tomcat resolves the client address from `X-Forwarded-For` only when the direct peer is an internal proxy.

## Consequences

- Obvious credential attacks from one client address are slowed without new infrastructure.
- Limits are per instance and per address, so distributed deployments or shared NAT egress would need a shared store or a gateway rule; that review is required before scaling out.
- A successful login is still possible for other clients; throttling does not lock accounts or reveal whether an email exists.
- The frontend surfaces `429` as a retryable error; no client-side lockout state is introduced.

## Alternatives Considered

- Bucket4j or Redis-backed counters: rejected because they add a dependency or infrastructure without a multi-instance need.
- Account lockout after failed attempts: rejected because it enables denial of service against known accounts.
- Gateway/WAF throttling: deferred until a gateway exists.

## Review Conditions

Revisit this decision when the service runs more than one instance, sits behind a shared proxy that collapses client addresses, or adds a gateway that can enforce limits earlier.
