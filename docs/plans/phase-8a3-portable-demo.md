# Phase 8A3 — Portable Docker demo and substantial fixtures

## Prerequisite and scope

8A1 bootstrap and 8A2 functional permissions passed. Continue the approved Phase 8 plan; whole Phase 8 remains incomplete. Keep campus-service and campus-client as sibling repositories.

## Implementation plan

1. Add a dedicated demo Compose configuration for PostgreSQL, backend and frontend. Use a separate persistent volume and explicit loopback ports (web 3300, API 28080) so the existing native database/app are preserved. The frontend image builds with pinned npm dependencies and proxies existing owner APIs. Keep native Maven/Vite commands.
2. Add an explicit PowerShell runner requiring Docker/Git, not host Java/Node. Generate ignored per-installation configuration and private account credentials, validate readiness with bounded checks, provision the first ADMIN through the existing offline command, then create other accounts through the normal ADMIN API. Normal web startup remains non-provisioning. Stop/restart must retain all data; no reset, Flyway repair, database or volume deletion.
3. Add a resumable fixture loader through existing owner APIs, using each functional operator for its owner mutations. Bind the loader to the approved demo configuration/credential manifest and authenticate the actual target before writing. Keep identifiers/codes deterministic, journal progress privately, validate existing records before reuse, and never overwrite unrelated data or passwords. Use bounded requests and no automatic replay of business writes on ambiguous outcomes.
4. Create two global ADMIN, eleven scoped/viewer accounts and approximately 200 Students with linked USER accounts, plus the approved substantial cross-domain inventory. Use fictional Vietnamese names and example.test emails, eligibility/capacity/version rules and meaningful lifecycle/history diversity. Keep manifests and passwords ignored, out of Docker build contexts and logs. Also make the existing native local installation usable with the same fixture plan without deleting/replacing its database.
5. Verify actual persisted counts and owner relationships through API/read-only checks, repeat-run preservation, role boundaries and failure/resume behavior. Record actual totals rather than assuming target counts were achieved. Verify Docker build/start/restart/proxy/cookies and preserve native data. Review source/diff/docs before PASS and before dependent 8B/8C work.

## Required inventory targets

Use the [approved inventory](phase-8-9-local-demo.md): about 80 personnel/16 units, 12 programs/60 courses, three terms/90 offerings/150 sections/800 retained enrollments, four buildings/80 rooms/240 beds/100 current stays plus releases, roughly 600 charges/450 receipts, 24 notices, 16 events/400 memberships and 120 titles/360 copies/~200 loans. Adjust only when an actual owner contract prevents a target and explain the evidence; do not invent domain fields or policies.

## Gate

Implemented and [reviewed PASS locally](../reviews/phase-8a3-demo-review.md), 2026-10-06. Both native and Docker installations have the approved substantial inventory, with repeat runs producing zero owner writes. Clean-source image builds, exact stop/restart preservation, safe audited resume and real proxy/browser checks passed. 8B acceptance/source review and 8C full regression remain incomplete; whole Phase 8 is not PASS yet.
