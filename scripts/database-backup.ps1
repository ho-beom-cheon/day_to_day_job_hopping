param(
    [ValidateSet('Public', 'QA')]
    [string]$Target = 'Public'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backupDirectory = Join-Path $projectRoot 'backups'
$composeArguments = if ($Target -eq 'Public') {
    @('-f', 'compose.yaml', '-f', 'compose.public.yaml')
} else {
    @('--env-file', '.env.course-qa.local', '-f', 'compose.course-qa.yaml')
}
$requiredSettings = if ($Target -eq 'Public') { '.env' } else { '.env.course-qa.local' }
$backupName = 'daily-career-{0}-{1}-{2}.dump' -f $Target.ToLowerInvariant(), (Get-Date -Format 'yyyyMMddTHHmmss'), ([guid]::NewGuid().ToString('N').Substring(0, 8))
$containerFile = "/tmp/$backupName"
$backupFile = Join-Path $backupDirectory $backupName
$partialFile = "$backupFile.partial"
$sourceContainer = $null

if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $requiredSettings))) {
    throw "Required local settings file is missing: $requiredSettings"
}
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
if (Test-Path -LiteralPath $backupFile) { throw "Backup file already exists: $backupFile" }

Push-Location $projectRoot
try {
    $sourceContainers = @(& docker compose @composeArguments ps -q postgres)
    $composeExit = $LASTEXITCODE
    $sourceContainer = if ($sourceContainers.Count -gt 0) { [string]$sourceContainers[0] } else { '' }
    if ($composeExit -ne 0 -or [string]::IsNullOrWhiteSpace($sourceContainer)) {
        throw "The $Target PostgreSQL container is not running."
    }
    $sourceContainer = $sourceContainer.Trim()
    $dumpCommand = 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f "$BACKUP_FILE"'
    & docker exec --env "BACKUP_FILE=$containerFile" $sourceContainer sh -ec $dumpCommand
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
    & docker exec $sourceContainer pg_restore --list $containerFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The archive catalog could not be read.' }

    & docker cp "${sourceContainer}:$containerFile" $partialFile
    if ($LASTEXITCODE -ne 0) { throw 'Copying the archive to the local backup directory failed.' }
    $containerHashLine = & docker exec $sourceContainer sha256sum $containerFile
    if ($LASTEXITCODE -ne 0) { throw 'Container archive hash verification failed.' }
    $containerHash = ($containerHashLine -split '\s+')[0]
    $localHash = (Get-FileHash -LiteralPath $partialFile -Algorithm SHA256).Hash
    if (-not $localHash.Equals($containerHash, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The copied archive hash differs from the source archive.'
    }
    Move-Item -LiteralPath $partialFile -Destination $backupFile
    @{
        target = $Target
        createdAt = (Get-Date).ToString('o')
        sha256 = $localHash
        bytes = (Get-Item -LiteralPath $backupFile).Length
    } | ConvertTo-Json | Set-Content -LiteralPath "$backupFile.json" -Encoding UTF8
    Write-Host "Backup verified: $backupFile"
    Write-Host "SHA-256: $localHash"
    Write-Output $backupFile
} finally {
    if ($sourceContainer) {
        & docker exec $sourceContainer rm -f -- $containerFile | Out-Null
    }
    if (Test-Path -LiteralPath $partialFile) {
        $resolvedPartial = [IO.Path]::GetFullPath($partialFile)
        $resolvedDirectory = [IO.Path]::GetFullPath($backupDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedPartial.StartsWith($resolvedDirectory, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a partial archive outside the local backup directory.'
        }
        Remove-Item -LiteralPath $partialFile -Force
    }
    Pop-Location
}
