# Phase 1B Slice 2: Authentication and Token Lifecycle

## Status

Approved for implementation, subject to the production allowed-origin configuration being set before browser deployment.

## Slice 1 Baseline

Slice 1 provides normalized email identity, BCrypt password hashes, account status, roles, PostgreSQL-backed sessions, refresh-token digest storage, token replacement links, expiry constraints, and Flyway migrations V1 through V4. No authentication HTTP endpoints or Spring Security configuration exist yet.

## Approved Decisions

- Login uses normalized email and password. Username login and public registration are excluded.
- Access tokens are signed HS256 JWTs using a Base64-encoded secret of at least 256 bits.
- Access tokens are returned in JSON and held by the frontend only in memory.
- Access tokens expire after 15 minutes.
- Refresh tokens are cryptographically secure opaque values held only in an HttpOnly cookie.
- Refresh tokens expire after 30 days and are stored only as SHA-256 digests.
- Every successful refresh rotates the refresh token.
- Reuse of a rotated refresh token revokes the complete session and its refresh-token family.
- Logout is idempotent and returns `204 No Content`.

## Access Token Contract

- Algorithm: HS256.
- Issuer: `campus-service`.
- Audience: `campus-service-clients`.
- Required claims: `iss`, `aud`, `sub`, `iat`, `exp`, `jti`, and `roles`.
- `sub` is the immutable Identity user UUID.
- `roles` contains existing role codes, initially `USER` and `ADMIN`.
- Access tokens do not contain email, password hashes, refresh tokens, session identifiers, audit data, or signing material.
- Spring Security-supported JWT encoder and decoder components are used; no custom JWT implementation is introduced.

## Refresh Cookie Contract

- Name: `CAMPUS_REFRESH` by default, configurable.
- Attributes: `HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth`.
- `Secure` is enabled outside the `local` profile.
- The raw refresh token appears only in the `Set-Cookie` response header and is never returned in JSON, persisted, logged, or placed in browser localStorage.
- Login and refresh responses use `Cache-Control: no-store` and `Pragma: no-cache`.
- Refresh and logout accept refresh credentials only from the refresh cookie, not from request bodies, query strings, or authorization headers.
- CSRF is disabled for the stateless Bearer-token API. Cookie-bearing refresh and logout requests require a configured same-origin policy before browser deployment; `campus.security.allowed-origins` must be explicitly configured for non-local environments.

## Endpoint Contracts

### POST /api/v1/auth/login

Authentication: public.

Request:

```json
{
  "email": "user@example.com",
  "password": "raw password supplied over HTTPS"
}
```

Success: `200 OK`.

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "<uuid>",
    "email": "user@example.com",
    "status": "ACTIVE",
    "roles": ["USER"]
  }
}
```

Side effects: verify the password through `PasswordEncoder.matches`, create a session, persist only the refresh-token digest, set the refresh cookie, and update `last_login_at` in one transaction.

Failure: nonexistent email, incorrect password, suspended account, and disabled account all return `401 AUTHENTICATION_FAILED` with `Authentication failed`.

Rate limiting: deferred. A future boundary may limit by normalized email and source address without changing the failure response.

### POST /api/v1/auth/refresh

Authentication: public endpoint with the refresh cookie and allowed browser origin.

Request body: none.

Success: `200 OK` with a replacement refresh cookie.

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Side effects: hash the presented opaque value, lock and load its record, validate session and token state, persist a replacement token, revoke the prior token with `replaced_by_id`, and return a new access token in one transaction. Reuse of a rotated token revokes the session and all tokens in that session.

Failures: missing, invalid, expired, revoked, or reused refresh tokens return the defined `401` error code. Rejected browser origin returns `403 INVALID_REQUEST_ORIGIN`.

Rate limiting: deferred.

### POST /api/v1/auth/logout

Authentication: public endpoint that consumes the refresh cookie when present.

Request body: none.

Success: always `204 No Content` and clear the refresh cookie with matching attributes and `Max-Age=0`.

Side effects: revoke a valid active presented refresh token. Missing, expired, invalid, or already revoked cookies do not change the response. Logout does not immediately revoke issued access tokens; they remain valid for at most 15 minutes.

Rate limiting: deferred.

### GET /api/v1/auth/me

Authentication: valid Bearer access token required.

Success: `200 OK`.

```json
{
  "id": "<uuid>",
  "email": "user@example.com",
  "status": "ACTIVE",
  "roles": ["USER"]
}
```

Side effects: none; use a read-only user lookup.

## Error Model

```json
{
  "timestamp": "2026-09-22T00:00:00Z",
  "status": 401,
  "code": "AUTHENTICATION_FAILED",
  "message": "Authentication failed",
  "path": "/api/v1/auth/login",
  "traceId": "<optional-correlation-id>"
}
```

| Status | Code | Message |
| --- | --- | --- |
| 400 | `MALFORMED_REQUEST` | `Request is invalid` |
| 401 | `AUTHENTICATION_FAILED` | `Authentication failed` |
| 401 | `MISSING_ACCESS_TOKEN` | `Access token is required` |
| 401 | `INVALID_ACCESS_TOKEN` | `Access token is invalid` |
| 401 | `ACCESS_TOKEN_EXPIRED` | `Access token has expired` |
| 401 | `REFRESH_TOKEN_MISSING` | `Refresh token is required` |
| 401 | `REFRESH_TOKEN_INVALID` | `Refresh token is invalid` |
| 401 | `REFRESH_TOKEN_EXPIRED` | `Refresh token has expired` |
| 401 | `REFRESH_TOKEN_REVOKED` | `Refresh token is revoked` |
| 401 | `REFRESH_TOKEN_REUSED` | `Refresh token reuse detected` |
| 403 | `ACCOUNT_NOT_ACTIVE` | `Account is not permitted to authenticate` |
| 403 | `FORBIDDEN` | `Access is forbidden` |
| 403 | `INVALID_REQUEST_ORIGIN` | `Request origin is not allowed` |
| 500 | `INTERNAL_ERROR` | `An unexpected error occurred` |

Responses never include raw passwords, password hashes, raw refresh tokens, token digests, signing secrets, stack traces, or unapproved persistence details.

## Spring Security Rules

- Configure a stateless `SecurityFilterChain`.
- Disable form login, HTTP Basic, and server-side sessions.
- Public routes are limited to `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, and `GET /actuator/health`.
- Every other route requires authentication.
- Convert validated `roles` claims to `ROLE_<ROLE_CODE>` authorities.
- Configure custom authentication-entry-point and access-denied responses using the error model.
- Use `DelegatingPasswordEncoder` with BCrypt support and only `PasswordEncoder.matches` for verification.

## Account and Token Lifecycle

- Only `ACTIVE` accounts may log in, refresh, or use `/me`.
- Login creates one session expiring with the configured refresh-token lifetime.
- Refresh values are generated with `SecureRandom`, encoded Base64 URL without padding, and SHA-256-hashed before persistence.
- Rotation persists the replacement before linking and revoking the prior token.
- A rotated-token reuse event revokes the entire associated session. All tokens associated with that session are then rejected.
- Logout revokes only the current presented token and clears the cookie.

## Package and File Structure

- `com.campus.identity.application`: login, refresh, logout, and current-user use cases with transaction boundaries.
- `com.campus.identity.api`: controllers, request/response DTOs, cookie handling, and narrow error handling.
- `com.campus.identity.infrastructure.security`: JWT support, opaque-token generation and hashing, authenticated principal, origin validation, and Spring Security configuration.
- Extend existing Identity ports and persistence adapters only for locking refresh-token lookup and session-family revocation.

## Configuration Contract

| Spring property | Environment variable |
| --- | --- |
| `campus.security.jwt.issuer` | `JWT_ISSUER` |
| `campus.security.jwt.audience` | `JWT_AUDIENCE` |
| `campus.security.jwt.secret` | `JWT_SECRET` |
| `campus.security.jwt.access-token-ttl` | `JWT_ACCESS_TOKEN_TTL` |
| `campus.security.refresh-token-ttl` | `REFRESH_TOKEN_TTL` |
| `campus.security.refresh-cookie.name` | `REFRESH_COOKIE_NAME` |
| `campus.security.refresh-cookie.secure` | `REFRESH_COOKIE_SECURE` |
| `campus.security.refresh-cookie.same-site` | `REFRESH_COOKIE_SAME_SITE` |
| `campus.security.refresh-cookie.path` | `REFRESH_COOKIE_PATH` |
| `campus.security.allowed-origins` | `ALLOWED_ORIGINS` |

`.env.example` will contain placeholders only. Production secrets come from external environment or secret management.

## Test Matrix

- Unit: password verification, JWT signing and validation, issuer/audience/expiry validation, account eligibility, opaque token generation and hashing, rotation, reuse detection, logout, and clock-controlled expiration.
- HTTP integration: successful login, uniform invalid credentials, public-route restrictions, valid/invalid/expired access token behavior, valid and anonymous `/me`, response secret absence, and cookie attributes.
- PostgreSQL Testcontainers: login persistence, refresh rotation, prior-token rejection, expiry and revocation rejection, session-family revocation on reuse, logout revocation, and V1-V4 Flyway validation.

## Documentation Updates

- Record ADR 0003.
- Update `.env.example` with placeholder-only approved settings during implementation.
- Update implementation-status documentation only after the Slice 2 code and tests pass.

## Explicit Exclusions

- Public registration and administrator user management.
- Password reset, verification email, social login, OAuth, MFA, and Redis token blacklists.
- Student, faculty, and other business-domain endpoints.
- Keycloak, API gateways, microservices, and deployment.

## Implementation Order

1. Add approved security dependencies and typed configuration.
2. Implement clock, password verification, opaque-token, and JWT infrastructure.
3. Add required transactional Identity port operations.
4. Implement application use cases.
5. Configure Spring Security and authenticated principal handling.
6. Implement HTTP endpoints, cookie behavior, and error responses.
7. Add unit, HTTP, and Testcontainers integration coverage.
8. Update `.env.example` and directly affected documentation.
9. Run `./mvnw.cmd clean verify` and review the complete diff.

## Definition of Done

- Active-account login creates a session and returns a signed, expiring access token plus an HttpOnly refresh cookie.
- Login failures do not reveal account existence or status.
- Refresh tokens are opaque, digest-only at rest, rotated, and revoked.
- Refresh-token reuse revokes the complete session family.
- Logout is idempotent and clears the cookie.
- `/me` exposes only approved authenticated-user fields.
- Unapproved routes reject unauthenticated access.
- Authentication, security, and Testcontainers tests pass.
