param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$Database = $env:POSTGRES_DB,
    [string]$Username = $env:POSTGRES_USERNAME
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $BackupFile)) { throw "Backup file not found: $BackupFile" }
if (-not $Database -or -not $Username) {
    throw "Set POSTGRES_DB and POSTGRES_USERNAME or pass -Database/-Username."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required; this script restores into the Compose 'postgres' service."
}

Write-Host "This replaces all data in database '$Database' from $BackupFile."
$confirmation = Read-Host "Type RESTORE to continue"
if ($confirmation -ne "RESTORE") { throw "Restore cancelled." }

$remote = "/tmp/campus-restore.sql.gz"
docker compose cp $BackupFile "postgres:$remote"
if ($LASTEXITCODE -ne 0) { throw "Backup transfer failed." }

docker compose exec -T postgres sh -c "gunzip -c '$remote' | psql --set ON_ERROR_STOP=1 --username '$Username' --dbname '$Database'"
$restoreCode = $LASTEXITCODE
docker compose exec -T postgres rm -f $remote
if ($restoreCode -ne 0) { throw "Restore failed; the database may be partially restored. Inspect the psql output." }
Write-Host "Restore completed from $BackupFile"
