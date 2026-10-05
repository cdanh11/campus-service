# Native development only. The Docker demo needs no host Java/Node and has its own state.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot '.demo'
$taskEnvironment = Join-Path $taskRoot '.env'
if (-not (Test-Path -LiteralPath (Join-Path $taskDirectory 'accounts.json'))) {
    throw 'A private local demo account manifest is required. Use start-demo.ps1 -Seed for a new portable installation.'
}
$taskValues = @{}
foreach ($taskLine in Get-Content -LiteralPath $taskEnvironment) {
    if ($taskLine -match '^\s*(?:#|$)') { continue }
    if ($taskLine -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { throw 'Unsupported .env line.' }
    $taskValues[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
}
if ($taskValues.SPRING_PROFILES_ACTIVE -ne 'local') { throw 'Only the configured native local demo is supported.' }
if (-not $taskValues.JWT_SECRET) { throw 'Missing native JWT configuration.' }
$taskPort = if ($taskValues.SERVER_PORT) { [int]$taskValues.SERVER_PORT } else { 8080 }
$taskIssuer = if ($taskValues.JWT_ISSUER) { $taskValues.JWT_ISSUER } else { 'campus-service' }
$taskAudience = if ($taskValues.JWT_AUDIENCE) { $taskValues.JWT_AUDIENCE } else { 'campus-service-clients' }
$taskConfiguration = @{ baseUrl = "http://127.0.0.1:$taskPort"; jwtSecret = $taskValues.JWT_SECRET; issuer = $taskIssuer; audience = $taskAudience; fixtureVersion = 1 }
$taskConfigPath = Join-Path $taskDirectory 'config.json'
if (Test-Path -LiteralPath $taskConfigPath) {
    $taskPrevious = Get-Content -LiteralPath $taskConfigPath -Raw | ConvertFrom-Json
    foreach ($taskField in 'baseUrl', 'jwtSecret', 'issuer', 'audience', 'fixtureVersion') {
        if ($taskPrevious.$taskField -cne $taskConfiguration[$taskField]) { throw 'Native demo configuration changed. Restore its matching configuration rather than reseeding a different target.' }
    }
}
else { $taskConfiguration | ConvertTo-Json | Set-Content -LiteralPath $taskConfigPath -Encoding utf8 }
$taskValues.Clear(); $taskConfiguration = $null; $taskPrevious = $null
& node (Join-Path $PSScriptRoot 'seed-demo.mjs') --state-dir $taskDirectory
if ($LASTEXITCODE -ne 0) { throw 'Native demo seed is incomplete; inspect the safe loader message and private journal.' }
