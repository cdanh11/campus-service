# Database backup and restore

These procedures cover the local Compose PostgreSQL volume used by the demo and development environments. They are logical `pg_dump`/`psql` procedures, not a production ha/PITR plan; a production deployment still needs scheduled external storage, retention and restore rehearsal.

## Prerequisites

- Docker Desktop with the Compose `postgres` service running (`docker compose ps postgres`).
- `POSTGRES_DB` and `POSTGRES_USERNAME` exported for the current shell, or passed as `-Database`/`-Username`.
- The backup directory is outside version control (`backups/`), because dumps contain personal data. Never commit a dump, attach it to an issue, or paste its contents into a log.

## Backup

```powershell
$env:POSTGRES_DB = "campus_service"
$env:POSTGRES_USERNAME = "campus_service"
.\scripts\backup-database.ps1
# or: .\scripts\backup-database.ps1 -Database campus_service -Username campus_service -OutputDirectory D:\backups
```

The script runs `pg_dump --no-owner --clean` inside the container, compresses it to `backups/<database>-<timestamp>.sql.gz`, and copies it to the host. A failed dump leaves no partial host file. Verify that the file is non-empty and record its timestamp and schema version (the `flyway_schema_history` table is included in the dump).

## Restore

Restore is destructive: it replaces the target database data. Announce downtime, stop application writers, and take a fresh backup before restoring.

```powershell
.\scripts\restore-database.ps1 -BackupFile .\backups\campus_service-20261110-101500.sql.gz
```

The script asks for the literal confirmation `RESTORE`; anything else cancels. It then runs `gunzip -c ... | psql --set ON_ERROR_STOP=1` inside the container and fails on the first SQL error. A failed restore may leave a partially restored database — keep the pre-restore backup and investigate the `psql` output before retrying.

After a successful restore:

1. Start the backend and confirm `GET /actuator/health` reports `UP`.
2. Confirm Flyway validates the restored schema (`flyway_schema_history` matches the expected released version); do not edit or repair migration history to force a match.
3. Sign in with a known account and open one list page per restored module that matters to the environment.

## Notes and limits

- The dump includes audit tables and personal data (student/personnel contact fields). Handle it under the same access rules as the database.
- Passwords, JWT secrets and environment files are not stored in the database; restoring does not restore secrets.
- `--clean` drops and recreates objects owned by the dumped schema. It does not remove unrelated schemas or a database created by a different Compose project.
- This procedure does not provide point-in-time recovery, logical replication, or an off-site copy. Add those only with an approved operational requirement.
