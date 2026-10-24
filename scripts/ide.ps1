param(
    [ValidateSet('Build', 'Up', 'Stop')]
    [string]$Action = 'Up',
    [ValidateSet('Auto', 'Development', 'Public')]
    [string]$Mode = 'Auto'
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

function Get-LocalSetting {
    param([string]$Name)
    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if ($null -ne $processValue) { return $processValue }
    if (-not (Test-Path -LiteralPath '.env')) { return '' }
    $settingMatches = @(Select-String -LiteralPath '.env' -Pattern ('^\s*' + [regex]::Escape($Name) + '\s*=(.*)$'))
    if ($settingMatches.Count -eq 0) { return '' }
    return $settingMatches[-1].Matches[0].Groups[1].Value.Trim().Trim([char[]]@(34, 39))
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
        $appOrigin = Get-LocalSetting -Name 'APP_ORIGIN'
        $httpsHost = Get-LocalSetting -Name 'APP_HTTPS_HOST'
        $selectedMode = $Mode
        if ($selectedMode -eq 'Auto') {
            $selectedMode = if ($appOrigin -match '^https://' -or $httpsHost) { 'Public' } else { 'Development' }
        }
        $composeArguments = @('compose', '-f', 'compose.yaml')
        if ($selectedMode -eq 'Public') {
            if ($Action -eq 'Up') {
                $originUri = $null
                if (-not [Uri]::TryCreate($appOrigin, [UriKind]::Absolute, [ref]$originUri) -or
                    $originUri.Scheme -ne 'https' -or $originUri.Host -ne $httpsHost) {
                    throw 'Public mode requires matching HTTPS APP_ORIGIN and APP_HTTPS_HOST in the local environment.'
                }
            }
            $composeArguments += @('-f', 'compose.public.yaml')
        }
        Write-Host "Selected $selectedMode mode."
        Invoke-Checked -Command 'docker' -Arguments ($composeArguments + @('config', '--quiet'))
        if ($Action -eq 'Stop') {
            Invoke-Checked -Command 'docker' -Arguments ($composeArguments + @('stop'))
        } else {
            Invoke-Checked -Command 'docker' -Arguments ($composeArguments + @('up', '--build', '-d', '--wait', '--wait-timeout', '180'))
            # Refresh upstream addresses when a frontend/backend container was recreated.
            Invoke-Checked -Command 'docker' -Arguments ($composeArguments + @('exec', '-T', 'nginx', 'nginx', '-s', 'reload'))
            Invoke-Checked -Command 'docker' -Arguments ($composeArguments + @('exec', '-T', 'frontend', 'node', 'scripts/smoke.mjs'))
            Invoke-Checked -Command 'docker' -Arguments ($composeArguments + @('ps'))
            if ($selectedMode -eq 'Public') {
                Write-Host "Full stack is ready at $appOrigin/login (the existing HTTPS proxy must be running)."
            } else {
                Write-Host 'Full stack is ready. Default URL: http://localhost:8080/login (see .env for port overrides).'
            }
            Write-Host 'Containers run in the background. Use Daily Career - Stop All to stop them.'
        }
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
