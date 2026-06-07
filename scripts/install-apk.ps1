param(
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$adb = Join-Path $root ".deps\android-sdk\platform-tools\adb.exe"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $adb)) {
    throw "adb.exe not found. Run scripts\bootstrap-deps.ps1 first."
}

if (-not (Test-Path $apk)) {
    throw "APK not found. Run scripts\build-apk.ps1 first."
}

if ($Serial) {
    & $adb -s $Serial install -r $apk
} else {
    & $adb install -r $apk
}

