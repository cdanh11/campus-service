param(
    [string]$Database = $env:POSTGRES_DB,
    [string]$Username = $env:POSTGRES_USERNAME,
    [string]$OutputDirectory = "backups"
)

$ErrorActionPreference = "Stop"
if (-not $Database -or -not $Username) {
    throw "Set POSTGRES_DB and POSTGRES_USERNAME or pass -Database/-Username."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required; this script dumps from the Compose 'postgres' service."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$file = Join-Path $OutputDirectory ("{0}-{1}.sql.gz" -f $Database, (Get-Date -Format "yyyyMMdd-HHmmss"))
$remote = "/tmp/campus-backup.sql.gz"

docker compose exec -T postgres sh -c "pg_dump --username '$Username' --dbname '$Database' --no-owner --clean | gzip > '$remote'"
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed." }

docker compose cp "postgres:$remote" $file
docker compose exec -T postgres rm -f $remote
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $file)) {
    Remove-Item $file -Force -ErrorAction SilentlyContinue
    throw "Backup transfer failed."
}
Write-Host "Backup written to $file"
