# Portable local Campus Platform demo

## Setup

Keep `campus-service` and `campus-client` as sibling directories. Install Git, Docker Desktop with its Linux engine running, and PowerShell 7 or later. The Docker route does not require host Java, Node or npm. Use compatible revisions from both repositories; the Phase 8 implementation is on `feature/local-demo-test-plan` until the owner merges it.

From `campus-service`:

```powershell
.\scripts\start-demo.ps1 -Seed
```

The first run downloads/builds the images, starts a dedicated PostgreSQL database, invokes the offline first-ADMIN CLI, starts the backend/frontend and loads substantial fictional fixtures through owner APIs. It can take several minutes. Open http://localhost:3300. API access for local diagnostics is http://127.0.0.1:28080; PostgreSQL has no host port. Both published ports bind only to loopback.

Each installation generates a private database password, JWT signing key and random account passwords. Open `.demo/docker/accounts.json` locally to obtain a login; do not copy it into chat, screenshots, Git or public logs. There are two global ADMIN, eleven functional/viewer accounts and 200 linked Student USER accounts. The role matrix is in [Permissions](../permissions.md). The Student portal provides its existing inbox/Event capabilities, not unimplemented Academic or Finance self-service.

## Persistence and restart

Native development ports 3000/8080 and its PostgreSQL volume remain separate. This installation uses the named `campus-platform-demo_campus-demo-data` volume. Restart without rebuilding:

```powershell
.\scripts\start-demo.ps1 -SkipBuild
```

Repeat fixture verification/loading with `-SkipBuild -Seed`. A successful repeat of an unchanged fixture inventory must report `ownerWritesThisRun: 0`; normal login records last-login metadata/sessions and can advance Identity account versions. Fixture identifiers and credentials remain stable. Stop containers without deleting data:

```powershell
docker compose --env-file .demo/docker/config.env -f compose.demo.yaml stop
```

Keep the private `.demo/docker` directory paired with its database volume. If a volume exists but its configuration is missing, the runner refuses to generate replacement credentials. Restore the matching private files. There is no reset, Flyway repair or volume-deletion operation in the demo scripts.

## Safe fixture loading

The loader verifies the target's JWT signature, issuer/audience and the saved administrator identity before owner writes. It uses the appropriate functional operator for each domain. Only normal Identity creation, domain mutation, lifecycle/version and audit behavior is used; it does not insert business rows directly or change released migrations.

`.demo/docker/journal.json` records completed operations and pending writes. `.demo/docker/inventory.json` records actual API inventory, lifecycle distributions, relationships, balances, recipient ownership and owner audit coverage. All files are ignored/private. A transport failure leaves an uncertain operation pending: the next run can reconcile one matching committed resource or a completed versioned state change; it does not blindly replay an unresolved write. Unowned matching identifiers stop the loader.

If a loader process was forcibly terminated, confirm that no seed container/process remains active before resolving a stale `seed.lock`. Inspect the pending journal and owner API first; never remove a pending operation just to force a retry. Regular exceptions release the loader's lock. Configuration or fixture-definition mismatches require investigation, not credential replacement or resetting the database.

Library due dates remain the actual default 14 days from borrowing; fixture loading cannot manufacture past loans through the approved API. Finance includes past due dates, partial/full/unpaid charges, receipt reversal and cancellation. Notification publication uses 50 recipients per notice, below the existing 100-recipient limit.

## Native development installation

The Maven/Vite route in [Local demo](local-demo.md) remains available. For an already configured local demo with its private account manifest in `.demo/accounts.json`, start the backend and then run:

```powershell
.\scripts\seed-local-demo.ps1
```

This route requires host Node and uses the native `.env` plus manifest. It verifies the live token against that configuration before writing and keeps a separate native journal. For a new machine, prefer the Docker command above; do not invent or reuse someone else's password manifest.

## Verification gate

See [8A3 plan](../plans/phase-8a3-portable-demo.md). Image startup alone is not whole Phase 8 PASS. Required evidence includes actual large inventory, repeat-run behavior, stop/restart preservation, proxy login/refresh/logout and subsequent acceptance/regression reviews.

The [8A3 review](../reviews/phase-8a3-demo-review.md) records local PASS. Maintainer checks include `node --test --test-isolation=none scripts/demo-client.test.mjs`, `scripts/verify-demo-persistence.ps1`, `node scripts/verify-demo-resume.mjs` and `node scripts/verify-demo-browser.mjs`. Browser verification additionally requires the sibling client's installed Playwright dependencies/Chromium. Its screenshots remain private. Recovery verification retains one separate title; it never deletes that record or the main fixtures.
