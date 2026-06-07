# SteakRush

SteakRush is a portrait Android restaurant-management game prototype.

The player manages several steak pans at the same time, matching each
customer's requested doneness before patience runs out. The first build uses
procedural pixel-block visuals, generated looping music, and Android
TextToSpeech voice prompts so the APK can be produced without external art or
audio assets.

All bootstrap dependencies are intended to live under `.deps/` in this
workspace.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File scripts\bootstrap-deps.ps1
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1 -Clean
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To install on a connected Android phone with USB debugging enabled:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-apk.ps1
```

To test on a local Android emulator stored under `.deps/`, using the default
AOSP system image to avoid Pixel Launcher ANR popups:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-emulator-test.ps1
```

If the emulator window is too large for the desktop, pass a smaller scale:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-emulator-test.ps1 -Scale 0.32
```
