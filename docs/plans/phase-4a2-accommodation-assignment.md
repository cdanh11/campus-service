# Phase 4A2 — Accommodation Assignment

> Phase-specific checkpoint document. Scope and results below belong to the named phase; current project status is in [README](../../README.md) and the approved remaining work is in the [Phase 8–9 plan](../plans/phase-8-9-local-demo.md). Historical results are not new verification.

Base: reviewed/pushed 4A1 ba76a28 on feature/dormitory-foundation. Main currently contains Phase 3D; continue on this Dormitory branch without merging it ourselves. V1–V19 are immutable. Phase 4 remains active after this slice; Finance and 4C still require gates.

## Contract

- User approved one current place per Student and retained release history. Model UUID assignment, immutable studentId/bedId, ASSIGNED/RELEASED, rowVersion, assignedAt/releasedAt and createdAt/updatedAt. Only ASSIGNED is current. PostgreSQL partial unique indexes independently protect current student and bed membership, including races across different buildings.
- POST creates ASSIGNED for an ACTIVE Student and active building/room/bed. Student eligibility uses StudentManagementService, never Student persistence. Existing Student deactivation does not silently release history. No gender, future booking, interval overlap, transfer, billing or vacancy counter.
- PUT only releases an ASSIGNED record with expectedVersion. RELEASED is terminal; repeated or stale release is 409. Taking a place again requires a new POST/new history row, unlike Academic re-enrollment on the same record. Release remains possible after Student deactivation; references never change.
- Locks are building → room → bed → assignment. Bed deactivation refuses current assignment; ancestor deactivation is already blocked by active descendants. Release and assignment/deactivation use the same inventory locks. Different-bed admission for the same Student relies on the current-student unique index; reject the losing transaction atomically.
- Every supported application mutation requires actor and shares its transaction with Dormitory audit; extend V20 audit resource/action CHECKs for ASSIGNMENT + ASSIGNED/RELEASED, without weakening existing inventory action rules.
- ADMIN POST/GET/list/PUT at /api/v1/admin/dormitory/assignments; query studentId/bedId/status, bounded page/size/offset and allowlisted assignedAt/createdAt/updatedAt/status sort with UUID tie-breaker. Request schema names unique. No DELETE or self-service.

## Implementation and strict review gate

1. Add domain/ports/schema V20 and exact historical V19 entity scan.
2. Implement allocation persistence, Student contract checks, inventory lock/deactivation integration and atomic audit.
3. Add ADMIN API/error/OpenAPI contract; preserve existing inventory route behavior.
4. Test lifecycle/history/immutable IDs, authorization for every operation, validation/reference/query errors and complete-row rollback, stale/duplicate/released transitions, audit failure for assign/release, concurrent same-bed/different-bed-same-Student admission, stale release, release versus new admission, bed close versus assign and bounded production lock acquisition with SQLSTATE.
5. Genuine V19→only V20 fixtures and all prior table/history preservation, new columns/defaults/nullability/PK/FKs/CHECKs/partial uniqueness/indexes and valid/invalid SQLSTATE writes. Validate all 21 production entities on exact upgraded public schema with Flyway disabled; historical V19 keeps 20.
6. Focused, full clean verify, diff/schema/security/ownership review. No PASS claim until verified. Update docs/roadmap, scoped commits and push after PASS. No repair/deletion/deploy/merge.

Status: PASS on 2026-10-04. Focused 47 cases in 1m34s; final clean verify BUILD SUCCESS, 260 tests in 43 suites, zero failures/errors/skips, 6m29s. Exact V20 schema validates 21 production entities with Flyway disabled; V1–V19 unchanged. See [final review](../reviews/phase-4a2-final-review.md), including the non-blocking slow test-JVM shutdown observation. Phase 4B1/4B2/4C remain incomplete.
