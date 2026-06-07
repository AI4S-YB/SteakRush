param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$deps = Join-Path $root ".deps"
$downloads = Join-Path $deps "downloads"
$sdk = Join-Path $deps "android-sdk"
$cmdlineTools = Join-Path $sdk "cmdline-tools"
$cmdlineLatest = Join-Path $cmdlineTools "latest"
$jdkHome = Join-Path $deps "jdk-17"
$gradleHome = Join-Path $deps "gradle-8.7"
$gradleUserHome = Join-Path $deps "gradle-home"

New-Item -ItemType Directory -Force -Path $deps, $downloads, $sdk, $gradleUserHome | Out-Null

function Download-File {
    param(
        [Parameter(Mandatory=$true)][string]$Url,
        [Parameter(Mandatory=$true)][string]$OutFile
    )
    if ((Test-Path $OutFile) -and -not $Force) {
        return
    }
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $OutFile
}

function Expand-Clean {
    param(
        [Parameter(Mandatory=$true)][string]$Zip,
        [Parameter(Mandatory=$true)][string]$Destination
    )
    if ((Test-Path $Destination) -and $Force) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    if (-not (Test-Path $Destination)) {
        New-Item -ItemType Directory -Force -Path $Destination | Out-Null
        Expand-Archive -LiteralPath $Zip -DestinationPath $Destination -Force
    }
}

$jdkZip = Join-Path $downloads "temurin-jdk17.zip"
$gradleZip = Join-Path $downloads "gradle-8.7-bin.zip"
$cmdlineZip = Join-Path $downloads "commandlinetools-win.zip"

Download-File `
    -Url "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" `
    -OutFile $jdkZip
Download-File `
    -Url "https://services.gradle.org/distributions/gradle-8.7-bin.zip" `
    -OutFile $gradleZip
Download-File `
    -Url "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" `
    -OutFile $cmdlineZip

if ((-not (Test-Path $jdkHome)) -or $Force) {
    $jdkTemp = Join-Path $deps "jdk17-unpacked"
    if (Test-Path $jdkTemp) {
        Remove-Item -LiteralPath $jdkTemp -Recurse -Force
    }
    Expand-Clean -Zip $jdkZip -Destination $jdkTemp
    $jdkRoot = Get-ChildItem -LiteralPath $jdkTemp -Directory | Select-Object -First 1
    if (-not $jdkRoot) {
        throw "Could not find JDK root after extracting $jdkZip"
    }
    if (Test-Path $jdkHome) {
        Remove-Item -LiteralPath $jdkHome -Recurse -Force
    }
    Move-Item -LiteralPath $jdkRoot.FullName -Destination $jdkHome
    Remove-Item -LiteralPath $jdkTemp -Recurse -Force
}

if ((-not (Test-Path $gradleHome)) -or $Force) {
    $gradleTemp = Join-Path $deps "gradle-unpacked"
    if (Test-Path $gradleTemp) {
        Remove-Item -LiteralPath $gradleTemp -Recurse -Force
    }
    Expand-Clean -Zip $gradleZip -Destination $gradleTemp
    $gradleRoot = Join-Path $gradleTemp "gradle-8.7"
    if (-not (Test-Path $gradleRoot)) {
        throw "Could not find Gradle root after extracting $gradleZip"
    }
    if (Test-Path $gradleHome) {
        Remove-Item -LiteralPath $gradleHome -Recurse -Force
    }
    Move-Item -LiteralPath $gradleRoot -Destination $gradleHome
    Remove-Item -LiteralPath $gradleTemp -Recurse -Force
}

if ((-not (Test-Path $cmdlineLatest)) -or $Force) {
    $cmdTemp = Join-Path $deps "cmdline-tools-unpacked"
    if (Test-Path $cmdTemp) {
        Remove-Item -LiteralPath $cmdTemp -Recurse -Force
    }
    Expand-Clean -Zip $cmdlineZip -Destination $cmdTemp
    $cmdRoot = Join-Path $cmdTemp "cmdline-tools"
    if (-not (Test-Path $cmdRoot)) {
        throw "Could not find command-line tools root after extracting $cmdlineZip"
    }
    New-Item -ItemType Directory -Force -Path $cmdlineTools | Out-Null
    if (Test-Path $cmdlineLatest) {
        Remove-Item -LiteralPath $cmdlineLatest -Recurse -Force
    }
    Move-Item -LiteralPath $cmdRoot -Destination $cmdlineLatest
    Remove-Item -LiteralPath $cmdTemp -Recurse -Force
}

$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome "bin") + [IO.Path]::PathSeparator + $env:Path
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GRADLE_USER_HOME = $gradleUserHome

$sdkManager = Join-Path $cmdlineLatest "bin\sdkmanager.bat"
if (-not (Test-Path $sdkManager)) {
    throw "sdkmanager.bat was not found at $sdkManager"
}

$licenses = "y`ny`ny`ny`ny`ny`ny`ny`ny`ny`n"
$licenses | & $sdkManager --sdk_root=$sdk --licenses
& $sdkManager --sdk_root=$sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"

Write-Host "Dependencies are ready under $deps"
Write-Host "Run: powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1 -Clean"

