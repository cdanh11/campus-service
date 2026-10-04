# ADR 0011: In-application Notification Ownership and Publication

## Status

Accepted within the user-approved Phase 5 scope on 2026-10-04; verification remains a separate slice gate.

## Context

The local personal project needs useful communication APIs before selecting an external email/SMS provider. Identity accounts and Student profiles are independent; a Student may have no login. Notification delivery is therefore addressed to explicit Identity account UUIDs, rather than treating Student contact data as an implicit destination.

## Decision

Notification owns templates, draft/published notices, recipient deliveries and synchronous status-only audit. An ACTIVE template is snapshotted into a DRAFT notice. ADMIN may edit a draft with expectedVersion, then publish once to a bounded explicit set of 1–100 distinct ACTIVE accounts. Published content is immutable. Delivery rows and publication/audit share one transaction; failure leaves no partial recipient batch. Template deactivation or edits do not rewrite a retained notice snapshot.

Identity provides active-account eligibility through its application-facing directory. Its adapter uses a database existence/status projection, avoiding cached entity status and loading account credentials into the notification module. Eligibility is checked at the operation decision; no cross-module lock freezes subsequent account status.

Only the recipient authenticated principal can list/read/acknowledge a delivery. No request UUID can select another inbox. Missing and foreign-owned delivery IDs both return 404. Read acknowledgement refresh-locks the delivery, verifies expectedVersion for UNREAD, and atomically writes READ/time/version/audit. Repeated acknowledgement of READ is idempotent, retaining its original time and adding no second event. Inbox content comes from immutable notice snapshots, loaded in a bounded batch rather than one notice query per delivery.

For this scope publishing is the local delivery itself; there is no SMTP/SMS transport or background retry queue. A rolled-back publication can be retried with its unchanged expectedVersion. Audit stores actor/resource/target/action/resulting version/time and status-only object metadata, not title/body or recipient lists. No physical deletion, automated retention expiry or cross-domain auto-send is exposed.

## Consequences and alternatives

- The feature runs locally with PostgreSQL and current JWT authentication, without provider secrets or delivery fees.
- Owner filtering and transaction locks are explicit and tested with real PostgreSQL failures/concurrency.
- Direct SQL is not an audited mutation interface; unsupported updates can bypass application immutability.
- Broadcast enumeration, executable templates, automatic domain subscriptions and a broker are deferred until there is an approved use case. External transport will require durable attempt/retry semantics and its own review.

## Review conditions

Revisit for external channels, scheduled/broadcast delivery, template variables, stronger database immutability or retention requirements. Preserve ownership isolation, atomicity and historical migration validation.
