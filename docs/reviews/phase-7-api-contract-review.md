# Phase 7 prerequisite — OpenAPI contract review

Result: **PASS**. Date: 2026-10-04. Branch: feature/api-contracts.

Production OpenAPI previously merged unrelated nested DTOs sharing Request/UpdateRequest/Response/PageResponse names. The red regression reproduced Academic Program update with Student fields. Base `springdoc.use-fqn: true` now gives implicit schemas owner-qualified names. Explicit approved @Schema names stay unchanged. JSON fields, routes, HTTP behavior, authorization, domain code, entities and V1–V25 are unchanged.

OpenApiOwnerContractIntegrationTest compares the actual document with Spring handler record signatures: distinct ownership, exact fields, nested collections and generic page references across **104 distinct DTO schemas**. It normalizes Spring regex path parameter syntax when finding a documented operation. Existing schema-specific domain tests also remain green. The document is exported to target only; it is not manually repaired.

Verification:
- Focused test: BUILD SUCCESS, 1 test, zero failure/error/skip, 50.130s, 2026-10-04T23:01:05+07:00.
- Full `./mvnw.cmd clean verify`: **BUILD SUCCESS, 417 tests/75 suites, zero failures/errors/skips, 10m35s**, finished **2026-10-04T23:12:25+07:00**. XML totals independently counted. Boot jar packaged; 49 pools close normally.
- Production property and new test reviewed; git diff --check passed; no migration or security-policy diff.

Consumers generating SDKs from component names must regenerate types/snapshot. Frontend should pin this backend commit in contracts/source.json and CI, then rerun its real backend browser harness. Backend PR/merge belongs to the user. No deployment, production provisioning, Flyway repair or database/volume deletion.
