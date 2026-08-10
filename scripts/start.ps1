$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $repoRoot "server\target"

function Find-LatestApplicationJar {
    if (-not (Test-Path -LiteralPath $targetDirectory)) { return $null }
    return Get-ChildItem -LiteralPath $targetDirectory -Filter "interface-platform*.jar" -File |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

function Get-JavaVersionText([string]$javaPath) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        return (& $javaPath -version 2>&1 | Out-String)
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}

$jarPath = Find-LatestApplicationJar
if (-not $jarPath) {
    Write-Host "Executable JAR not found. Running a full build first." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "build.ps1")
    $jarPath = Find-LatestApplicationJar
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javaPath = if ($javaCommand) { $javaCommand.Source } else { $null }
$versionText = if ($javaPath) { Get-JavaVersionText $javaPath } else { "" }

if ($versionText -notmatch 'version "(17|18|19|20|21|22|23|24|25|26)') {
    $jdkRoot = Join-Path $env:USERPROFILE ".jdks"
    $javaPath = Get-ChildItem -LiteralPath $jdkRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { ((Get-JavaVersionText $_.FullName) -match 'version "(17|18|19|20|21|22|23|24|25|26)') } |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $javaPath) { throw "Java 17 or a newer JDK was not found." }

Push-Location $repoRoot
try {
    Write-Host "Starting interface platform: http://localhost:8080" -ForegroundColor Green
    Write-Host "Using JAR: $jarPath" -ForegroundColor Cyan
    & $javaPath -jar $jarPath @args
}
finally {
    Pop-Location
}
