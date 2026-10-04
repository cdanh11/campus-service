# Phase 7 prerequisite — reliable OpenAPI owner schemas

Frontend Phase 7 is approved in the separate campus-client repository. Its 7A foundation/authentication gate passed. Before 7B1 typed business forms, production OpenAPI must represent the owner DTOs accurately.

## Finding and scope

Production nested records named Request, UpdateRequest, Response and PageResponse collide across controller classes. A regression reproduced Academic Program update containing Student fields, and frontend inspection showed Organization and Faculty/Staff create referencing Student Request. Authentication DTO names used by 7A were unaffected.

Enable springdoc.use-fqn in the production base configuration. Explicit existing @Schema names stay in use; implicit names include their owner class. Do not rename Java/JSON fields, routes, role policies, entities or migrations. No new endpoint, feature, database cleanup or deployment.

## Verification and review

The new OpenApiOwnerContractIntegrationTest reads the actual /v3/api-docs and production Spring handler signatures. It checks distinct record ownership, exact component fields and nested collections/generic page references, normalizing only Spring regex path parameters as OpenAPI does. It exports the production document to target for inspection; never hand-edit the exported specification to hide a source defect.

Initial regression: BUILD FAILURE with the expected Program/Student field mismatch. Corrected focused command: `./mvnw.cmd "-Dtest=OpenApiOwnerContractIntegrationTest" test` — BUILD SUCCESS, 104 distinct DTO references verified, 1 test, zero failure/error/skip, 50.130s, finished 2026-10-04T23:01:05+07:00.

Full clean verify finished BUILD SUCCESS: 417 tests/75 suites, zero failures/errors/skips, 10m35s, 2026-10-04T23:12:25+07:00. XML totals, packaged jar and 49 pool shutdowns verified. Correction reviewed PASS; see ../reviews/phase-7-api-contract-review.md. V1–V25 unchanged.

After PASS, frontend regenerates its snapshot/types against this exact backend commit and updates its pinned integration CI revision. User owns pull requests/merges in both repositories. Backend local project-roadmap.md stays untracked.
