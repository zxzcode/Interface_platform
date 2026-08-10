$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

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

function Find-Java17Home {
    if ($env:JAVA_HOME) {
        $currentJava = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path -LiteralPath $currentJava) {
            $versionText = Get-JavaVersionText $currentJava
            if ($versionText -match 'version "(17|18|19|20|21|22|23|24|25|26)') {
                return $env:JAVA_HOME
            }
        }
    }

    $jdkRoot = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path -LiteralPath $jdkRoot) {
        foreach ($jdk in Get-ChildItem -LiteralPath $jdkRoot -Directory) {
            $java = Join-Path $jdk.FullName "bin\java.exe"
            if (Test-Path -LiteralPath $java) {
                $versionText = Get-JavaVersionText $java
                if ($versionText -match 'version "(17|18|19|20|21|22|23|24|25|26)') {
                    return $jdk.FullName
                }
            }
        }
    }

    throw "Java 17 or a newer JDK was not found. Install JDK 17 first."
}

$env:JAVA_HOME = Find-Java17Home
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Push-Location $repoRoot
try {
    Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor Cyan
    & mvn clean package
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
    Write-Host "Build complete: server\target\interface-platform.jar" -ForegroundColor Green
}
finally {
    Pop-Location
}
