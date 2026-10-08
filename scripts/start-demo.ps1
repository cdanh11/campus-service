param([switch]$Seed, [switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Use PowerShell 7 or later for UTF-8 demo setup.' }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskClient = Join-Path (Split-Path -Parent $taskRoot) 'campus-client'
$taskPrivate = Join-Path $taskRoot '.demo/docker'
$taskConfig = Join-Path $taskPrivate 'config.json'
$taskAccounts = Join-Path $taskPrivate 'accounts.json'
$taskEnvironment = Join-Path $taskPrivate 'config.env'
$taskCompose = @('compose', '--project-name', 'campus-platform-demo', '--env-file', $taskEnvironment, '-f', (Join-Path $taskRoot 'compose.demo.yaml'))
if (-not (Test-Path -LiteralPath (Join-Path $taskClient 'Dockerfile'))) { throw 'Clone campus-client beside campus-service, including its Dockerfile.' }
& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker engine must be running.' }

function New-DemoSecret {
    $taskBytes = New-Object byte[] 32
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes); return [Convert]::ToBase64String($taskBytes) }
    finally { $taskRandom.Dispose(); [Array]::Clear($taskBytes, 0, $taskBytes.Length) }
}
function Write-PrivateJson($taskPath, $taskValue) {
    $taskValue | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $taskPath -Encoding utf8
}
function Invoke-DemoCompose([string[]]$taskArguments) {
    & docker @taskCompose @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'Demo Compose command failed. Inspect service logs without sharing credentials.' }
}

if (-not (Test-Path -LiteralPath $taskConfig)) {
    $taskVolumes = & docker volume ls --filter name=campus-platform-demo_campus-demo-data --format '{{.Name}}'
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect existing demo volumes; configuration generation refused.' }
    if ($taskVolumes -contains 'campus-platform-demo_campus-demo-data') { throw 'An existing demo volume has no matching private config. Restore .demo/docker; do not regenerate credentials or delete the volume.' }
    New-Item -ItemType Directory -Path $taskPrivate -Force | Out-Null
    if ($env:OS -eq 'Windows_NT') {
        $taskPrincipal = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls $taskPrivate /inheritance:r /grant:r "${taskPrincipal}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the private demo directory.' }
    }
    $taskSettings = @{ baseUrl = 'http://backend:8080'; issuer = "campus-demo-$([Guid]::NewGuid())"; audience = 'campus-demo-client'; jwtSecret = (New-DemoSecret); databasePassword = (New-DemoSecret); fixtureVersion = 1 }
    Write-PrivateJson $taskConfig $taskSettings
    Write-PrivateJson $taskAccounts @{ accounts = @(@{ email = 'admin.primary@example.test'; password = (New-DemoSecret); roles = @('ADMIN'); displayName = 'Quản trị hệ thống 1' }) }
}
if (-not (Test-Path -LiteralPath $taskAccounts)) { throw 'Restore the private account manifest before starting this installation.' }
$taskSettings = Get-Content -LiteralPath $taskConfig -Raw | ConvertFrom-Json
@("DEMO_DB_PASSWORD=$($taskSettings.databasePassword)", "DEMO_JWT_SECRET=$($taskSettings.jwtSecret)", "DEMO_JWT_ISSUER=$($taskSettings.issuer)") | Set-Content -LiteralPath $taskEnvironment -Encoding utf8
Invoke-DemoCompose -taskArguments @('config', '--quiet')
if (-not $SkipBuild) { Invoke-DemoCompose -taskArguments @('build') }
Invoke-DemoCompose -taskArguments @('up', '-d', '--wait', '--wait-timeout', '120', 'postgres')
if (-not (Test-Path -LiteralPath (Join-Path $taskPrivate 'bootstrap.complete'))) {
    $taskPrimary = (Get-Content -LiteralPath $taskAccounts -Raw | ConvertFrom-Json).accounts[0]
    try {
        $env:CAMPUS_BOOTSTRAP_EMAIL = $taskPrimary.email
        $env:CAMPUS_BOOTSTRAP_DISPLAY_NAME = $taskPrimary.displayName
        $env:CAMPUS_BOOTSTRAP_PASSWORD = $taskPrimary.password
        & docker @taskCompose run --rm --no-deps -e CAMPUS_BOOTSTRAP_EMAIL -e CAMPUS_BOOTSTRAP_DISPLAY_NAME -e CAMPUS_BOOTSTRAP_PASSWORD backend --bootstrap-admin
        # A crash after account creation can leave the marker absent. Authenticate the
        # saved account below before accepting either a success or an existing-admin refusal.
    }
    finally {
        Remove-Item Env:CAMPUS_BOOTSTRAP_EMAIL, Env:CAMPUS_BOOTSTRAP_DISPLAY_NAME, Env:CAMPUS_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
    }
}
Invoke-DemoCompose -taskArguments @('up', '-d', '--wait', '--wait-timeout', '240', 'backend', 'frontend')
$taskPrimary = (Get-Content -LiteralPath $taskAccounts -Raw | ConvertFrom-Json).accounts[0]
try {
    $taskLogin = Invoke-RestMethod 'http://127.0.0.1:28080/api/v1/auth/login' -Method Post -ContentType 'application/json' -TimeoutSec 30 -Body (@{email=$taskPrimary.email; password=$taskPrimary.password} | ConvertTo-Json)
    if ($taskLogin.user.roles -notcontains 'ADMIN') { throw 'Saved account is not a global administrator.' }
    Set-Content -LiteralPath (Join-Path $taskPrivate 'bootstrap.complete') -Value 'Verified through normal login' -Encoding utf8
}
finally { $taskLogin = $null; $taskPrimary = $null }
if ($Seed) {
    & docker run --rm --network campus-platform-demo_default --mount "type=bind,source=$taskPrivate,target=/state" --mount "type=bind,source=$PSScriptRoot,target=/scripts,readonly" node:24.13.0-alpine node /scripts/seed-demo.mjs --state-dir /state
    if ($LASTEXITCODE -ne 0) { throw 'Demo fixtures are incomplete. Follow the reported safe resume instructions; do not reset the database.' }
}
Write-Host 'Demo ready: http://localhost:3300. Private login credentials: .demo/docker/accounts.json.'
Write-Host 'Normal restart preserves data. Do not share the private files.'
