[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$Subject
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path $PSScriptRoot '..\.env' }
$resolvedEnv = [IO.Path]::GetFullPath($EnvFile)
if (-not (Test-Path -LiteralPath $resolvedEnv -PathType Leaf)) {
    throw '설정 파일이 없습니다. 먼저 운영용 .env를 준비하세요.'
}

$content = [IO.File]::ReadAllText($resolvedEnv)
$names = @(
    'WEB_PUSH_PUBLIC_KEY',
    'WEB_PUSH_PRIVATE_KEY',
    'WEB_PUSH_SUBSCRIPTION_KEY',
    'WEB_PUSH_KEY_ID',
    'WEB_PUSH_SUBJECT'
)
$values = @{}
foreach ($name in $names) {
    $pattern = '(?m)^' + [regex]::Escape($name) + '=([^\r\n]*)'
    $matches = [regex]::Matches($content, $pattern)
    if ($matches.Count -gt 1) { throw "$name 항목이 중복되어 있습니다." }
    $values[$name] = if ($matches.Count -eq 1) { $matches[0].Groups[1].Value.Trim().Trim('"').Trim("'") } else { '' }
}

$existingKeys = @($names[0..3] | Where-Object { $values[$_] -ne '' })
if ($existingKeys.Count -eq 4) {
    if ($values['WEB_PUSH_SUBJECT'] -eq '') { throw '기존 키가 있지만 WEB_PUSH_SUBJECT가 없습니다. 연락처를 설정한 뒤 다시 실행하세요.' }
    Write-Output 'Web Push 키가 이미 설정되어 있습니다. 기존 값을 유지했습니다.'
    return
}
if ($existingKeys.Count -ne 0) {
    throw 'Web Push 키가 일부만 설정되어 있습니다. 기존 구독을 보호하기 위해 자동 변경하지 않습니다.'
}

if ([string]::IsNullOrWhiteSpace($Subject)) {
    $origin = [regex]::Match($content, '(?m)^APP_ORIGIN=([^\r\n]*)').Groups[1].Value.Trim().Trim('"').Trim("'")
    $uri = $null
    if (-not [Uri]::TryCreate($origin, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https') {
        throw 'HTTPS APP_ORIGIN이 없습니다. -Subject로 https:// 연락처 URL 또는 mailto: 주소를 지정하세요.'
    }
    $Subject = $uri.GetLeftPart([UriPartial]::Authority)
}
if (-not ($Subject.StartsWith('https://') -or $Subject.StartsWith('mailto:'))) {
    throw 'WEB_PUSH_SUBJECT는 https:// 또는 mailto: 연락처여야 합니다.'
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$key = [Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve]::CreateFromFriendlyName('nistP256'))
try {
    $parameters = $key.ExportParameters($true)
} finally {
    $key.Dispose()
}
if ($parameters.D.Length -ne 32 -or $parameters.Q.X.Length -ne 32 -or $parameters.Q.Y.Length -ne 32) {
    throw 'P-256 키 길이가 올바르지 않습니다.'
}
$public = New-Object byte[] 65
$public[0] = 4
[Array]::Copy($parameters.Q.X, 0, $public, 1, 32)
[Array]::Copy($parameters.Q.Y, 0, $public, 33, 32)
$subscriptionKey = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($subscriptionKey) } finally { $rng.Dispose() }

$replacement = @{
    WEB_PUSH_PUBLIC_KEY = ConvertTo-Base64Url $public
    WEB_PUSH_PRIVATE_KEY = ConvertTo-Base64Url $parameters.D
    WEB_PUSH_SUBSCRIPTION_KEY = ConvertTo-Base64Url $subscriptionKey
    WEB_PUSH_KEY_ID = 'web-push-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    WEB_PUSH_SUBJECT = $Subject
}
foreach ($name in $names) {
    $pattern = '(?m)^' + [regex]::Escape($name) + '=[^\r\n]*'
    $line = $name + '=' + $replacement[$name]
    if ([regex]::IsMatch($content, $pattern)) { $content = [regex]::Replace($content, $pattern, $line) }
    else { $content = $content.TrimEnd() + "`r`n" + $line + "`r`n" }
}
[IO.File]::WriteAllText($resolvedEnv, $content, [Text.UTF8Encoding]::new($false))
Write-Output 'Web Push VAPID 및 구독 암호화 키를 설정했습니다. 키 값은 출력하지 않았습니다.'
Write-Output '기존 브라우저 구독이 생긴 뒤에는 이 키를 교체하지 마세요. 설정 파일을 비밀 저장소에 백업하세요.'
