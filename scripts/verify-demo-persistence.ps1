$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskPrivate = Join-Path $taskRoot '.demo/docker'
$taskCompose = @('compose', '--project-name', 'campus-platform-demo', '--env-file', (Join-Path $taskPrivate 'config.env'), '-f', (Join-Path $taskRoot 'compose.demo.yaml'))
function Read-DemoSql([string]$taskSql) {
    $taskRows = & docker exec -e 'PGOPTIONS=-c statement_timeout=10000' campus-platform-demo-postgres-1 psql -U campus_demo -d campus_demo -t -A -v ON_ERROR_STOP=1 -c $taskSql
    if ($LASTEXITCODE -ne 0) { throw 'Read-only demo persistence query failed.' }
    # Keep a singleton row as an array. Otherwise [0] would select the first
    # character of a PowerShell scalar string instead of the complete digest.
    return ,@($taskRows | Where-Object { $_ -ne '' })
}
function Read-SnapshotValue([string]$taskSql) {
    $taskValues = Read-DemoSql $taskSql
    if ($taskValues.Count -ne 1 -or $taskValues[0] -notmatch '^\d+:[0-9a-f]{32}$') { throw 'Expected a complete count/digest snapshot.' }
    return $taskValues[0]
}
function Get-DemoSnapshot {
    $taskSnapshot = [ordered]@{}
    $taskVolume = & docker volume inspect campus-platform-demo_campus-demo-data --format '{{.CreatedAt}}'
    if ($LASTEXITCODE -ne 0) { throw 'Demo volume not found.' }
    $taskSnapshot.volumeCreatedAt = $taskVolume
    # Authentication session rotation is expected. Business identity/status/version
    # and all retained audit records must survive without any schema repair.
    $taskTables = Read-DemoSql "SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='row_version' AND table_name IN (SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='status') ORDER BY table_name"
    foreach ($taskTable in $taskTables) {
        if ($taskTable -notmatch '^[a-z_]+$') { throw 'Unexpected database identifier.' }
        $taskSnapshot[$taskTable] = Read-SnapshotValue "SELECT count(*) || ':' || md5(coalesce(string_agg(id::text || ':' || row_version::text || ':' || status::text, '|' ORDER BY id), '')) FROM $taskTable"
    }
    foreach ($taskTable in 'identity_admin_audit_events','people_registry_audit_events','academic_audit_events','dormitory_audit_events','finance_audit_events','notification_audit_events','event_audit_events','library_audit_events') {
        $taskSnapshot[$taskTable] = Read-SnapshotValue "SELECT count(*) || ':' || md5(coalesce(string_agg(id::text, '|' ORDER BY id), '')) FROM $taskTable"
    }
    $taskSnapshot.migrations = Read-SnapshotValue "SELECT count(*) || ':' || md5(string_agg(concat_ws(':', installed_rank, version, checksum, success), '|' ORDER BY installed_rank)) FROM flyway_schema_history"
    $taskSnapshot.roles = Read-SnapshotValue "SELECT count(*) || ':' || md5(string_agg(id::text || ':' || code, '|' ORDER BY id)) FROM identity_roles"
    $taskSnapshot.memberships = Read-SnapshotValue "SELECT count(*) || ':' || md5(string_agg(user_id::text || ':' || role_id::text, '|' ORDER BY user_id, role_id)) FROM identity_user_roles"
    return $taskSnapshot
}
$taskBefore = Get-DemoSnapshot
& docker @taskCompose stop
if ($LASTEXITCODE -ne 0) { throw 'Demo stop failed.' }
# Restart web services directly: start-demo also verifies credentials by logging
# in, which correctly updates Identity's last-login metadata and row version.
# Comparing before that separate login keeps this persistence assertion exact.
& docker @taskCompose up -d --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0) { throw 'Demo restart failed.' }
$taskAfter = Get-DemoSnapshot
@{ before = $taskBefore; after = $taskAfter } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskPrivate 'persistence-snapshots.json') -Encoding utf8
$taskDifferences = @($taskBefore.Keys | Where-Object { $taskBefore[$_] -cne $taskAfter[$_] })
if ($taskDifferences.Count -or $taskBefore.Count -ne $taskAfter.Count) { throw "Business/schema persistence changed across stop/restart: $($taskDifferences -join ', ')." }
@{ verifiedAt = [DateTime]::UtcNow.ToString('o'); snapshotsMatch = $true; businessTables = @($taskBefore.Keys | Where-Object { $_ -notmatch 'audit|volume|migrations|roles|memberships' }).Count; snapshot = $taskAfter } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskPrivate 'persistence-review.json') -Encoding utf8
Write-Host 'Demo stop/restart PASS: persistent volume, business UUID/status/version, role assignments, audit IDs and Flyway history preserved.'
