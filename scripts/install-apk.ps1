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

$installArgs = @()
if ($Serial) {
    $installArgs += "-s"
    $installArgs += $Serial
}
$installArgs += "install"
$installArgs += "-r"
$installArgs += $apk

$output = & $adb @installArgs 2>&1
$exitCode = $LASTEXITCODE
$output | ForEach-Object { Write-Host $_ }

if ($exitCode -ne 0) {
    $text = $output -join "`n"
    if ($text -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|UPDATE_INCOMPATIBLE") {
        Write-Host ""
        Write-Host "The installed com.steakrush package was signed with a different certificate."
        Write-Host "Android will not allow an in-place update across different signing keys."
        if ($Serial) {
            Write-Host "To replace it, run: $adb -s $Serial uninstall com.steakrush"
        } else {
            Write-Host "To replace it, run: $adb uninstall com.steakrush"
        }
        Write-Host "Then run this install script again."
    }
    throw "adb install failed with exit code $exitCode"
} else {
    Write-Host "Install completed."
}
