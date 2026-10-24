param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backupDirectory = [IO.Path]::GetFullPath((Join-Path $projectRoot 'backups')).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$archive = (Resolve-Path -LiteralPath $BackupFile).Path
$archive = [IO.Path]::GetFullPath($archive)
if (-not $archive.StartsWith($backupDirectory, [StringComparison]::OrdinalIgnoreCase) -or
        -not $archive.EndsWith('.dump', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The restore drill accepts only a .dump archive inside this workspace backups directory.'
}
$manifestFile = "$archive.json"
if (-not (Test-Path -LiteralPath $manifestFile)) { throw 'Backup manifest is missing.' }
$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
$actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
if (-not $actualHash.Equals([string]$manifest.sha256, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Backup archive SHA-256 does not match the manifest.'
}

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$containerName = "daily-career-restore-drill-$suffix"
$volumeName = "daily-career-restore-drill-$suffix"
$postgresImage = 'postgres:17.11-alpine3.23@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6'
$volumeCreated = $false
$containerCreated = $false
$previousPassword = [Environment]::GetEnvironmentVariable('POSTGRES_PASSWORD')

try {
    & docker volume create $volumeName | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Creating the isolated restore volume failed.' }
    $volumeCreated = $true

    $secretBytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($secretBytes) } finally { $random.Dispose() }
    $env:POSTGRES_PASSWORD = [Convert]::ToBase64String($secretBytes)
    $runArguments = @(
        'run', '--detach', '--rm', '--name', $containerName, '--network', 'none',
        '--env', 'POSTGRES_DB=restore_check', '--env', 'POSTGRES_USER=restore_check',
        '--env', 'POSTGRES_PASSWORD', '--volume', "${volumeName}:/var/lib/postgresql/data", $postgresImage
    )
    & docker @runArguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Starting the isolated restore container failed.' }
    $containerCreated = $true
    [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', $previousPassword)

    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        & docker exec $containerName pg_isready -U restore_check -d restore_check | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated PostgreSQL did not become ready.' }

    & docker cp $archive "${containerName}:/tmp/restore.dump"
    if ($LASTEXITCODE -ne 0) { throw 'Copying the archive into the isolated container failed.' }
    & docker exec $containerName pg_restore -U restore_check -d restore_check --exit-on-error --no-owner --no-privileges /tmp/restore.dump
    if ($LASTEXITCODE -ne 0) { throw 'Restoring the archive into the isolated database failed.' }
    $result = & docker exec $containerName psql -U restore_check -d restore_check -At -c "select (select count(*) from public.flyway_schema_history where success), (select count(*) from daily_career.app_user), (select count(*) from daily_career.user_curriculum), (select count(*) from daily_career.learning_day_item)"
    if ($LASTEXITCODE -ne 0 -or -not $result) { throw 'Restored database verification query failed.' }
    Write-Host "Restore drill passed (Flyway, users, courses, items): $result"
} finally {
    [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', $previousPassword)
    if ($containerCreated) { & docker stop $containerName | Out-Null }
    if ($volumeCreated) { & docker volume rm $volumeName | Out-Null }
}
