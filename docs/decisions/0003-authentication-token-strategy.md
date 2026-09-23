# ADR 0003: Stateless Access Tokens and Rotating Refresh Cookies

## Status

Accepted for Phase 1B Slice 2.

## Context

Slice 1 supplies Identity users, roles, BCrypt password hashes, sessions, and refresh-token digest persistence. Campus Service needs a secure initial authentication lifecycle without introducing Redis, an external identity provider, an API gateway, or additional services.

## Decision

Campus Service will use normalized email and BCrypt password verification for login. Public registration and username login are not included.

The service will issue HS256 JWT access tokens with a Base64-encoded externally configured secret of at least 256 bits. Access tokens use issuer `campus-service`, audience `campus-service-clients`, and a 15-minute lifetime. Required claims are issuer, audience, immutable user UUID subject, issued-at, expiration, token identifier, and role codes.

The service will issue cryptographically secure opaque refresh tokens with a 30-day lifetime. It will persist only SHA-256 digests in PostgreSQL. The raw refresh token is returned only through an HttpOnly cookie, is not sent in JSON, and is not stored in browser localStorage.

Each refresh rotates the token in one transaction: create the replacement digest, revoke the prior token, and record its replacement. Reuse of a previously rotated token revokes its full associated session and token family. Logout is idempotent: it revokes a valid presented refresh token where present, clears the cookie, and returns `204 No Content` for all token states.

Spring Security will operate statelessly. Only login, refresh, logout, and health are public; all other routes require authenticated access. CSRF is disabled for the stateless Bearer-token API. Refresh and logout cookie requests require configured same-origin protection before browser deployment.

## Consequences

- Access tokens are short-lived and validated without a database lookup for each request.
- Refresh tokens can be rotated, revoked, and audited through the existing Identity schema.
- Reuse detection invalidates the affected session family, limiting continued use of a stolen rotated token.
- Logout cannot immediately invalidate issued access tokens; their maximum remaining validity is 15 minutes.
- The HS256 signing secret must be supplied through external configuration and must not be committed.
- Browser JavaScript cannot read the refresh token, but frontend code must retain the access token only in memory.
- Rate limiting is deferred and must be introduced later at an approved boundary.

## Alternatives Considered

- Refresh JWTs: rejected because opaque persisted tokens support rotation and revocation.
- Refresh token in JSON or localStorage: rejected because browser XSS exposure is greater.
- Redis token blacklist: rejected because it introduces unapproved infrastructure.
- Long-lived access tokens: rejected because credential and logout risk is higher.
- Asymmetric JWT signing: deferred; one modular-monolith issuer/verifier deployment does not yet require it.
- Global `permitAll` or disabled security: rejected because unapproved API routes must be protected.

## Review Conditions

Review this decision before supporting cross-site browser clients, non-browser refresh-cookie clients, multiple issuers, independently deployed services, asymmetric signing, external key rotation, global logout, or device/session management.
