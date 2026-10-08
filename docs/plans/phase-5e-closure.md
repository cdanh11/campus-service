# Phase 5E — Supporting-services closure

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Approved umbrella gate in phase-5-supporting-services.md; no extra features. All 5A–5D have separate slice PASS. **5E and whole Phase 5 backend review PASS** on 2026-10-04; every gate below is evidenced in ../reviews/phase-5-final-review.md. Final clean verify BUILD SUCCESS, 393 tests/67 suites, zero failures/errors/skips, 7m36s, finished 2026-10-04T17:14:15+07:00; all 44 pools close. Source/schema/security/query/docs/diff review completed; PR/merge remains user's responsibility.

## Requirements to close

1. Notification delivers in-app only: active explicit recipients, retained snapshots, owner inbox/read, idempotence, optimistic locking, atomic audit and bounded queries.
2. Event delivers approved catalog/status/capacity, ACTIVE Student admission, current linked-account self-service and ADMIN, retained same-record restore with expectedVersion, OPEN-only manual closure, cancellation frees a seat, ADMIN attendance consumes capacity, atomic audit.
3. Library delivers titles/physical copies, ADMIN borrowing/return, ACTIVE references, one OPEN loan per copy, server 14-day due time, retained history/new UUID on next loan, atomic audit.
4. ADMIN audit viewing reads all eight sources through owner application contracts with safe metadata, genuine recorded nullable versions, bounded filters/time/sort/pages, correct source ownership and snapshot consistency; no writes/expiry.
5. Preserve V1–V22 from merged Phase 4. Genuine populated V22→V23, V23→V24 and V24→V25 tests preserve old data/history and actual schema checks. Exact upgraded Hibernate validation with Flyway disabled validates 29/32/36 entities; historical scans exclude later entities.
6. Source/query/security/audit/concurrency review and full clean verify revalidate Phase 1–5. No skipped tests, no unsupported coverage/production readiness claim. Retain actual failures and fixes in reviews.
7. README/architecture/development/testing/API/ADRs and per-slice/final review reflect actual completion. Local roadmap updated but never staged/committed/pushed. Scoped commits/push after PASS; user owns PR/merge. No deployment, infrastructure cost, new Phase 6 feature, Flyway repair or developer data deletion.

Evidence belongs in ../reviews/phase-5-final-review.md. Production runtime provisioning/deployment and frontend remain out of scope; local backend PASS does not certify them.
