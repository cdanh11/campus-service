# Phase 4B1 — Finance Obligations

Base: reviewed 4A2, 8182597. Branch feature/finance-obligations depends on feature/dormitory-foundation until the user merges its PR. No automatic merge or history rewrite. V1–V20 immutable; V21 is new.

## Contract and scope

- Fee definitions: UUID, globally unique normalized code (2–32 Unicode characters), name (2–160), positive exact amount, currency, ACTIVE/INACTIVE, version and timestamps. ADMIN create/get/list/PUT with expectedVersion. Six-character boundary whitespace and ROOT uppercase code normalization follow existing modules; SQL independently enforces text bounds and case-insensitive uniqueness.
- User selected VND and manual payments on 2026-10-04. Integer amounts from 1 to 9999999999999999999 use BigDecimal and PostgreSQL NUMERIC with explicit integer/range CHECKs. Unconstrained NUMERIC avoids silently rounding a fractional direct SQL input before the CHECK; application rejects fractions too. No float, gateway, FX or tax engine.
- Charge: UUID, globally unique administrator-supplied charge number (2–32, normalized), immutable Student/fee UUID, fee code/name/currency/amount snapshot, required dueDate, OPEN/CANCELLED, version and timestamps. Create from ACTIVE fee and ACTIVE Student through Student application contract. No amount override; changing a fee does not change existing charges. Past due dates are allowed for recording existing obligations; no automatic overdue status.
- POST creates OPEN. PUT only cancels using expectedVersion; CANCELLED is terminal. Student/fee deactivation does not rewrite history or prevent cancellation. No charge editing, deletion, automatic Academic/Dormitory billing, ledger, discount, installment or payment implementation in this slice. Payment safety will extend cancellation under the same charge lock in 4B2.
- Lock fee before snapshotting/create or fee mutation; charge lock for cancellation. Duplicate charge number protected by database unique index. Refresh locks and stored timestamps to avoid stale cached entities/precision drift. Conservative fee lock serializes admissions using one definition.
- All supported mutations require trusted actor and synchronous Finance audit in the same transaction: FEE CREATED/UPDATED, CHARGE CREATED/CANCELLED. Audit stores resource UUID/type, action, resulting version, timestamp and status-only object JSONB; actor FK, no polymorphic target FK or JSON length cap.
- ADMIN /api/v1/admin/finance/fees and /charges; POST, GET/list, GET/{id}, PUT/{id}. Bounded page/size/offset, literal q on code/name or charge number/snapshot name, status and Student/fee filters for charges, allowlisted sorts with UUID tie-breaker. Same error envelope, safe 400/401/403/404/409/500 codes and unique OpenAPI schema names.

## Implementation/review gate

1. Domain/ports, exact amount and normalization boundaries, V21 owned schema and historic V20 entity scan.
2. Persistence/application contract, locking and atomic audit; admin API and documentation.
3. Tests for amounts including fractional rejection/maximum, Unicode/whitespace/expansion, lifecycle/snapshot immutability, authorization on all operations, validation/reference/duplicate/stale rollback, atomic audit failure for every mutation, query bounds/filter/sorts/ties, competing updates and fee close versus charge creation, bounded production lock SQLSTATE 55P03 and post-release success.
4. Genuine V20→only V21; complete legacy table/history preservation, exact new columns/types/defaults/nullability/PK/FKs/uniqueness/CHECK/indexes and SQLSTATE boundary writes. Hibernate ddl-auto=validate with Flyway disabled against that same upgraded schema and all production entities; historic V20 retains 21 entities.
5. Focused test, full clean verify, source/module/schema/diff review, PASS/FAIL evidence, docs/local roadmap and scoped commit/push. Finance payments and Phase 4C remain incomplete after this gate.

Status: PASS on 2026-10-04. Focused BUILD SUCCESS, 23 cases in 1m17s; final clean verify BUILD SUCCESS, 280 tests/46 suites in 6m37s, no failures/errors/skips. All 24 production entities validate on exact V20→V21 schema with Flyway disabled. See [final review](../reviews/phase-4b1-final-review.md), including the retained suite teardown follow-up. 4B2 and 4C remain incomplete.
