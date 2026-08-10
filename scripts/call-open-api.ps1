param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Method = "POST",
    [string]$Query = "",
    [string]$Body = "",
    [string]$BodyFile,
    [string]$AppKey = $env:INTERFACE_APP_KEY,
    [string]$AppSecret = $env:INTERFACE_APP_SECRET,
    [string]$ContentType = "application/json; charset=utf-8"
)

$ErrorActionPreference = "Stop"
if (-not $AppKey -or -not $AppSecret) {
    throw "Provide -AppKey/-AppSecret or set INTERFACE_APP_KEY/INTERFACE_APP_SECRET."
}

$normalizedPath = if ($Path.StartsWith("/")) { $Path } else { "/$Path" }
$normalizedMethod = $Method.ToUpperInvariant()
$bodyBytes = if ($BodyFile) {
    [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $BodyFile))
} else {
    [System.Text.Encoding]::UTF8.GetBytes($Body)
}

$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $bodyHash = ([System.BitConverter]::ToString($sha256.ComputeHash($bodyBytes))).Replace("-", "").ToLowerInvariant()
}
finally {
    $sha256.Dispose()
}

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$nonce = [Guid]::NewGuid().ToString("N")
$canonical = "$normalizedMethod`n$normalizedPath`n$Query`n$timestamp`n$nonce`n$bodyHash"
$hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($AppSecret))
try {
    $signature = ([System.BitConverter]::ToString(
        $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))
    )).Replace("-", "").ToLowerInvariant()
}
finally {
    $hmac.Dispose()
}

$uri = $BaseUrl.TrimEnd("/") + $normalizedPath
if ($Query) { $uri += "?$Query" }
$headers = @{
    "X-App-Key" = $AppKey
    "X-Timestamp" = $timestamp
    "X-Nonce" = $nonce
    "X-Signature" = $signature
}

try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -Method $normalizedMethod `
        -Headers $headers -ContentType $ContentType -Body $bodyBytes
    Write-Host "HTTP $([int]$response.StatusCode)"
    Write-Host "X-Trace-Id: $($response.Headers['X-Trace-Id'])"
    $response.Content
}
catch {
    if ($_.Exception.Response) {
        $errorResponse = $_.Exception.Response
        Write-Host "HTTP $([int]$errorResponse.StatusCode)"
        Write-Host "X-Trace-Id: $($errorResponse.Headers['X-Trace-Id'])"
        $reader = [System.IO.StreamReader]::new($errorResponse.GetResponseStream())
        try { $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    throw
}
