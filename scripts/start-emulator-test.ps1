param(
    [string]$AvdName = "SteakRushAospTest",
    [string]$Image = "system-images;android-35;default;x86_64",
    [double]$Scale = 0.42,
    [switch]$ColdBoot,
    [switch]$WipeData
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$deps = Join-Path $root ".deps"
$sdk = Join-Path $deps "android-sdk"
$jdk = Join-Path $deps "jdk-17"
$avdHome = Join-Path $deps "avd"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

$sdkManager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
$avdManager = Join-Path $sdk "cmdline-tools\latest\bin\avdmanager.bat"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$adb = Join-Path $sdk "platform-tools\adb.exe"

if (-not (Test-Path $sdkManager)) {
    throw "sdkmanager.bat not found. Run scripts\bootstrap-deps.ps1 first."
}
if (-not (Test-Path $jdk)) {
    throw "JDK 17 not found. Run scripts\bootstrap-deps.ps1 first."
}
if (-not (Test-Path $apk)) {
    throw "APK not found. Run scripts\build-apk.ps1 first."
}

New-Item -ItemType Directory -Force -Path $avdHome | Out-Null

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_AVD_HOME = $avdHome
$env:GRADLE_USER_HOME = Join-Path $deps "gradle-home"
$env:Path = (Join-Path $jdk "bin") + [IO.Path]::PathSeparator +
        (Join-Path $sdk "platform-tools") + [IO.Path]::PathSeparator +
        (Join-Path $sdk "emulator") + [IO.Path]::PathSeparator +
        $env:Path

Write-Host "Installing emulator packages under $sdk"
& $sdkManager --sdk_root=$sdk "emulator" $Image

$avdIni = Join-Path $avdHome "$AvdName.ini"
if (-not (Test-Path $avdIni)) {
    Write-Host "Creating AVD $AvdName in $avdHome"
    "no" | & $avdManager create avd -n $AvdName -k $Image --device "pixel_6" --force
} else {
    Write-Host "Using existing AVD $AvdName"
}

Write-Host "Starting emulator $AvdName"
$emulatorArgs = @(
    "-avd", $AvdName,
    "-netdelay", "none",
    "-netspeed", "full",
    "-gpu", "swiftshader",
    "-scale", ([string]::Format([Globalization.CultureInfo]::InvariantCulture, "{0:0.##}", $Scale)),
    "-no-boot-anim"
)
if ($ColdBoot -or $WipeData) {
    $emulatorArgs += @("-no-snapshot-load", "-no-snapshot-save")
}
if ($WipeData) {
    $emulatorArgs += "-wipe-data"
}
$process = Start-Process -FilePath $emulator -ArgumentList $emulatorArgs -PassThru

Write-Host "Waiting for emulator device"
$online = $false
for ($i = 0; $i -lt 180; $i++) {
    $devices = (& $adb devices 2>$null) -join "`n"
    if ($devices -match "emulator-\d+\s+device") {
        $online = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $online) {
    throw "Emulator did not become an online adb device within 6 minutes."
}

Write-Host "Waiting for Android boot completion"
$booted = $false
for ($i = 0; $i -lt 180; $i++) {
    $value = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
    if ($value -eq "1") {
        $booted = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $booted) {
    throw "Emulator started but Android did not finish booting within 6 minutes."
}

& $adb shell input keyevent 82 | Out-Null

Write-Host "Installing APK $apk"
& $adb install -r $apk

Write-Host "Launching Steak Rush"
& $adb shell monkey -p com.steakrush -c android.intent.category.LAUNCHER 1

Write-Host "Emulator PID: $($process.Id)"
Write-Host "Steak Rush is installed and launched on the emulator."
