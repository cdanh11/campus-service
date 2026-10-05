param([switch]$SkipDatabase, [switch]$BootstrapAdmin)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvironment = Join-Path $taskRoot '.env'
if (-not (Test-Path -LiteralPath $taskEnvironment)) {
    throw 'Create an untracked .env from .env.example and configure local values first.'
}

foreach ($taskLine in Get-Content -LiteralPath $taskEnvironment) {
    if ($taskLine -match '^\s*(?:#|$)') { continue }
    if ($taskLine -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        throw 'Unsupported .env line. Use NAME=value, without shell expressions.'
    }
    $taskName = $Matches[1]
    $taskValue = $Matches[2].Trim()
    if ($taskValue.Length -ge 2 -and (
        ($taskValue.StartsWith('"') -and $taskValue.EndsWith('"')) -or
        ($taskValue.StartsWith("'") -and $taskValue.EndsWith("'")))) {
        $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($taskName, $taskValue, 'Process')
}
if ($env:SPRING_PROFILES_ACTIVE -ne 'local') { throw 'This script requires SPRING_PROFILES_ACTIVE=local.' }
foreach ($taskRequired in 'POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_USERNAME', 'POSTGRES_PASSWORD', 'JWT_SECRET') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskRequired, 'Process'))) {
        throw "Missing local setting: $taskRequired"
    }
}
try { $taskKey = [Convert]::FromBase64String($env:JWT_SECRET) }
catch { throw 'JWT_SECRET must be a configured Base64 key, not the example placeholder.' }
if ($taskKey.Length -lt 32) { throw 'JWT_SECRET must contain at least 32 random bytes.' }

Push-Location $taskRoot
try {
    if (-not $SkipDatabase) {
        & docker compose --env-file $taskEnvironment up -d --wait --wait-timeout 120 postgres
        if ($LASTEXITCODE -ne 0) { throw 'Local PostgreSQL startup failed.' }
    }
    if ($BootstrapAdmin) {
        $env:CAMPUS_BOOTSTRAP_EMAIL = Read-Host 'First administrator email'
        $env:CAMPUS_BOOTSTRAP_DISPLAY_NAME = Read-Host 'Display name'
        $taskPassword = Read-Host 'Password (input hidden)' -AsSecureString
        $taskConfirm = Read-Host 'Confirm password' -AsSecureString
        $taskPasswordPointer = [IntPtr]::Zero
        $taskConfirmPointer = [IntPtr]::Zero
        try {
            $taskPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskPassword)
            $taskConfirmPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskConfirm)
            $taskPlainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPasswordPointer)
            $taskPlainConfirm = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskConfirmPointer)
            if ($taskPlainPassword -cne $taskPlainConfirm) { throw 'Password confirmation does not match.' }
            $env:CAMPUS_BOOTSTRAP_PASSWORD = $taskPlainPassword
            & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--bootstrap-admin'
        }
        finally {
            Remove-Item Env:CAMPUS_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
            Remove-Item Env:CAMPUS_BOOTSTRAP_EMAIL -ErrorAction SilentlyContinue
            Remove-Item Env:CAMPUS_BOOTSTRAP_DISPLAY_NAME -ErrorAction SilentlyContinue
            if ($taskPasswordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPasswordPointer) }
            if ($taskConfirmPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskConfirmPointer) }
            $taskPlainPassword = $null
            $taskPlainConfirm = $null
            $taskPassword.Dispose()
            $taskConfirm.Dispose()
        }
    }
    else { & .\mvnw.cmd spring-boot:run }
    if ($LASTEXITCODE -ne 0) { throw 'Campus Service exited with an error.' }
}
finally { Pop-Location }
