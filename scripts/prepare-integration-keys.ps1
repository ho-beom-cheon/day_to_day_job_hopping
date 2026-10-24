[CmdletBinding()]
param([string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'))
$ErrorActionPreference = 'Stop'
$resolvedEnv = [IO.Path]::GetFullPath($EnvFile)
if (-not (Test-Path -LiteralPath $resolvedEnv -PathType Leaf)) {
    throw '먼저 프로젝트 루트의 .env.example을 .env로 복사하고 DB 설정을 입력하세요.'
}
$content = [IO.File]::ReadAllText($resolvedEnv)
$changed = 0
foreach ($settingName in @('NOTION_CREDENTIAL_KEY', 'INTEGRATION_CREDENTIAL_KEY')) {
    $pattern = '(?m)^' + [regex]::Escape($settingName) + '=([^\r\n]*)'
    $existing = [regex]::Matches($content, $pattern)
    if ($existing.Count -gt 1) { throw "$settingName 항목이 중복되어 있습니다. 중복부터 정리하세요." }
    if ($existing.Count -eq 1 -and $existing[0].Groups[1].Value.Trim().Trim('"').Trim("'").Length -gt 0) {
        Write-Output "$settingName : 기존 값 유지"
        continue
    }
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $line = $settingName + '=' + [Convert]::ToBase64String($bytes)
    if ($existing.Count -eq 1) { $content = [regex]::Replace($content, $pattern, $line) }
    else { $content = $content.TrimEnd() + "`r`n" + $line + "`r`n" }
    $changed++
    Write-Output "$settingName : 신규 키 준비 (값은 출력하지 않음)"
}
if ($changed -gt 0) { [IO.File]::WriteAllText($resolvedEnv, $content, [Text.UTF8Encoding]::new($false)) }
Write-Output '완료. .env를 비밀 저장소에 백업하세요. 키를 바꾸면 기존 연결을 복호화할 수 없습니다.'
