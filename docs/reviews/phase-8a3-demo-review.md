# Phase 8A3 — Portable demo and substantial fixtures review

## Gate

PASS locally, 2026-10-06. Docker packaging, first-account setup, substantial owner-API fixtures, safe resume, exact persistence and representative browser/proxy checks passed. This does not close Phase 8; 8B acceptance/source review and 8C full regression remain required.

## Verified inventory

Both the native 3000/8080 installation and the independent Docker 3300/28080 installation contain the following main fixtures, verified through paginated owner APIs rather than script exit alone:

| Inventory | Actual fixtures |
| --- | ---: |
| Global ADMIN / functional or viewer accounts / linked Student USER | 2 / 11 / 200 |
| Organization units / personnel | 16 / 80 |
| Programs / courses / terms / offerings / sections | 12 / 60 / 3 / 90 / 150 |
| Retained enrollments | 800: 740 ENROLLED, 60 WITHDRAWN |
| Buildings / rooms / beds | 4 / 80 / 240 |
| Assignments | 100 ASSIGNED, 20 RELEASED |
| Fees / charges | 6 / 600: 580 OPEN, 20 CANCELLED |
| Retained receipts | 450: 420 RECORDED, 30 REVERSED |
| Templates / published notices / deliveries | 24 / 24 / 1,200: 40 READ, 1,160 UNREAD |
| Events / retained memberships | 16 / 400: 256 REGISTERED, 64 CANCELLED, 80 ATTENDED |
| Titles / copies / retained loans | 120 / 360 / 200: 120 OPEN, 80 RETURNED |

Docker also retains one separate `CD-VERIFY-RESUME` title used for controlled recovery verification, so its actual title-table total is 121. The inventory report distinguishes fixture count from total target count; no unrelated record is removed to force totals.

References, Student account uniqueness, enrollment/Event capacities, current-bed uniqueness, loan-copy uniqueness/default 14-day due interval, all 600 charge balances against effective receipts and every Student's owned inbox were checked. Nine operators have retained creation/mutation audits covering every main owner fixture target. AUDIT_VIEWER reads the evidence and REPORTING_VIEWER reads all eight dashboard groups.

## Execution evidence

- First Docker build/start and explicit offline bootstrap succeeded. An initial healthcheck incorrectly required the HTTP reason phrase `200 OK`; Tomcat returns a valid 200 without that phrase. Fixed to check the numeric status and UP body, then rebuilt/retested.
- Both images were additionally built from Git archives of backend 234dd22 and client 687683e plus the exact new Docker files/health script. This excluded the user's two uncommitted backend configuration changes. Both clean-source image builds succeeded; normal Flyway/Hibernate startup against the existing V26 demo schema succeeded.
- Docker first seed succeeded with 5,020 owner writes. Native first seed succeeded with 5,008 owner writes because its secondary/global and eleven scoped accounts already existed and were authenticated/adopted rather than replaced. Passwords, keys and manifests were not printed.
- Native repeat succeeded at 2026-10-05T17:16:28Z with zero owner writes. Final clean-image Docker repeat succeeded at 2026-10-05T17:23:04Z with zero owner writes; runner elapsed 79.20s. Normal authentication still records login metadata/sessions and can advance Identity account versions.
- Loader safety tests passed on host Node 24.13.1 and Docker Node 24.13.0: 10 tests, zero failures. Covers signature/issuer/audience/expiry, non-demo URLs, unresolved writes, unowned identifiers, different-actor rejection, controlled rejection/retry, completed-definition checks, lost create/mutation acknowledgements and unsafe versions.
- Real Library API recovery first committed one verification title, then deliberately lost the acknowledgement. Recovery found exactly one title and made zero duplicate POSTs. Final recovery additionally correlated the creation audit with the operator, recovered a deliberately lost PUT acknowledgement without replay and restored the verification title to ACTIVE through a normal fresh-version update. Wrong-signature target testing made zero owner writes. Final evidence: 2026-10-05T17:33:01Z.
- Final persistence test passed at 2026-10-05T17:29:03Z: exact count/digest comparison for 25 UUID/status/version tables, all eight audit tables, role catalog/memberships, volume creation identity and 26 Flyway history rows across stop/restart. No database/volume deletion or migration repair.
- Real Chromium acceptance against clean images passed at 2026-10-05T17:23:21Z: ADMIN login/seeded Student view, HttpOnly cookie/no browser token storage, reload/rotation, hostile-Origin rejection, cross-tab logout, Finance menu/API/direct-route limits, linked Student mobile inbox/Event view, no ADMIN requests and no page overflow. Desktop/mobile screenshots were inspected locally and remain private/ignored.
- PowerShell syntax checks and both repository `git diff --check` passed. Maven full regression is retained from 8A2 and will be rerun at 8C; no new full-suite totals are claimed here.

## Review findings and corrections

1. Fixed the healthcheck's reason-phrase assumption; the rebuilt container is healthy.
2. Fixed PowerShell singleton-array unwrapping in the snapshot verifier. The initial result stored only the first character; it was insufficient evidence and is superseded by complete count/digest checks.
3. The corrected snapshot initially detected Identity changes because the runner intentionally logs in, updating last-login/version metadata. The exact preservation test now restarts services directly and compares before that separate login. It still checks the entire Identity UUID/status/version snapshot; the assertion was not weakened by excluding Identity.
4. Pending-resource adoption now also requires the authenticated operator's creation audit. A different actor's matching record is refused. Both component and real API recovery checks passed.
5. Volume discovery must succeed before new configuration generation; a daemon error cannot be treated as an absent volume. Existing config/credential mismatches stop setup rather than regenerating secrets.

## Remaining whole-phase work

8B must complete the traceable acceptance matrix and broader source review. Visual review found raw ISO timestamps in the Student portal; resolve that display-quality finding before Phase 8 closure. Existing due-date rules prohibit fabricating past Library loans through the seed API. No per-department row isolation, payment gateway, unsupported Student self-service, production SLA or cross-browser certification is claimed.

8C must run the latest backend clean verify, frontend verify/mocked browser/isolated real browser suites and final diff/documentation review. The user configuration files and local roadmap remain excluded from commits.
