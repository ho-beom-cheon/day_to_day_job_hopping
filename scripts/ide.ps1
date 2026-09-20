param(
    [ValidateSet('Build', 'Up', 'Stop')]
    [string]$Action = 'Up'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed (exit $LASTEXITCODE). See the output above."
    }
}

Push-Location $projectRoot
try {
    if ($Action -eq 'Build') {
        Invoke-Checked -Command "$projectRoot/backend/mvnw.cmd" -Arguments @('-f', "$projectRoot/backend/pom.xml", '-B', '-ntp', 'verify')
        Push-Location "$projectRoot/frontend"
        try {
            Invoke-Checked -Command 'npm.cmd' -Arguments @('ci')
            Invoke-Checked -Command 'npm.cmd' -Arguments @('run', 'build')
        } finally {
            Pop-Location
        }
        Write-Host 'Backend and frontend builds passed.'
    } else {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            throw 'Docker CLI is missing. Install/start Docker Desktop (Linux containers), then restart IntelliJ IDEA.'
        }
        Invoke-Checked -Command 'docker' -Arguments @('info', '--format', '{{.ServerVersion}}')
        if ($Action -eq 'Up' -and -not (Test-Path '.env')) {
            # A new checkout gets an independent local secret; existing settings are preserved.
            $secretBytes = New-Object byte[] 32
            $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try { $random.GetBytes($secretBytes) } finally { $random.Dispose() }
            $localPassword = [BitConverter]::ToString($secretBytes).Replace('-', '').ToLowerInvariant()
            $template = [IO.File]::ReadAllText("$projectRoot/.env.example")
            [IO.File]::WriteAllText("$projectRoot/.env", $template.Replace('DATABASE_PASSWORD=', "DATABASE_PASSWORD=$localPassword"))
            Write-Host 'Created the Git-ignored .env with a generated local database password.'
        }
        Invoke-Checked -Command 'docker' -Arguments @('compose', 'config', '--quiet')
        if ($Action -eq 'Stop') {
            Invoke-Checked -Command 'docker' -Arguments @('compose', 'stop')
        } else {
            Invoke-Checked -Command 'docker' -Arguments @('compose', 'up', '--build', '-d', '--wait', '--wait-timeout', '180')
            Invoke-Checked -Command 'docker' -Arguments @('compose', 'ps')
            Write-Host 'Full stack is ready. Default URL: http://localhost:8080 (see .env for port overrides).'
            Write-Host 'Containers run in the background. Use Daily Career - Stop All to stop them.'
        }
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
