param(
    [Parameter(Mandatory)][ValidateSet('Import','Validate','Publish')][string]$Operation,
    [string]$Manuscript = 'content/java-four-week-v1/course.json',
    [ValidatePattern('^[1-9][0-9]*$')][string]$TemplateId,
    [ValidatePattern('^[0-9a-f]{64}$')][string]$ExpectedDigest,
    [string]$ReviewNote,
    [string]$EnvFile = '.env',
    [string[]]$ComposeFiles = @('compose.yaml','compose.public.yaml'),
    [string]$JarPath = '/app/app.jar'
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Push-Location $repoPath
try {
    $composeArgs = @('compose','--env-file',$EnvFile)
    foreach ($file in $ComposeFiles) { $composeArgs += @('-f',$file) }
    $runArgs = @('run','--rm','--no-deps','--entrypoint','java')
    $appArgs = @('-jar',$JarPath,'--spring.profiles.include=catalog-ops','--server.port=0','--management.server.port=0')
    if ($Operation -eq 'Import') {
        $resolvedManuscript = (Resolve-Path -LiteralPath $Manuscript).Path
        if ((Get-Item -LiteralPath $resolvedManuscript).Length -gt 4000000) { throw 'Manuscript exceeds 4 MB.' }
        $runArgs += @('--volume',"${resolvedManuscript}:/catalog/course.json:ro")
        $appArgs += @('--catalog.operation=import','--catalog.file=/catalog/course.json')
    } else {
        if (-not $TemplateId -or -not $ExpectedDigest) { throw 'TemplateId and ExpectedDigest from the import result are required.' }
        $action = if ($Operation -eq 'Validate') { 'validate-import' } else { 'publish-import' }
        $appArgs += @("--catalog.operation=$action","--catalog.template-id=$TemplateId","--catalog.expected-digest=$ExpectedDigest")
        if ($Operation -eq 'Publish') {
            if (-not $ReviewNote -or $ReviewNote.Trim().Length -lt 20) { throw 'ReviewNote must identify verification evidence (at least 20 characters).' }
            $appArgs += "--catalog.review-note=$ReviewNote"
        }
    }
    & docker @composeArgs @runArgs backend @appArgs
    if ($LASTEXITCODE -ne 0) { throw "Catalog operation failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
