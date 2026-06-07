param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$localSdk = Join-Path $root ".deps\android-sdk"
$localGradle = Join-Path $root ".deps\gradle-8.7\bin\gradle.bat"
$localJdk = Join-Path $root ".deps\jdk-17"
$localGradleHome = Join-Path $root ".deps\gradle-home"

if (Test-Path $localSdk) {
    $env:ANDROID_HOME = $localSdk
    $env:ANDROID_SDK_ROOT = $localSdk
}

if (Test-Path $localJdk) {
    $env:JAVA_HOME = $localJdk
    $env:Path = (Join-Path $localJdk "bin") + [IO.Path]::PathSeparator + $env:Path
}

$env:GRADLE_USER_HOME = $localGradleHome

if (Test-Path $localGradle) {
    $gradle = $localGradle
} elseif (Test-Path (Join-Path $root "gradlew.bat")) {
    $gradle = Join-Path $root "gradlew.bat"
} else {
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) {
        $gradle = $cmd.Source
    } else {
        throw "Gradle not found. Put Gradle 8.7 at .deps\gradle-8.7 or add gradle to PATH."
    }
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "Android SDK not found. Run scripts\bootstrap-deps.ps1 or put it at .deps\android-sdk."
}

$tasks = @()
if ($Clean) {
    $tasks += "clean"
}
$tasks += ":app:assembleDebug"

Push-Location $root
try {
    & $gradle @tasks
} finally {
    Pop-Location
}
