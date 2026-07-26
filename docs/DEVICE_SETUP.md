# Device / emulator setup — Windows host

Mechanical connection recipes, extracted from `AGENTS.md` on 2026-07-26 to keep the
always-loaded project cheatsheet small (it is injected into every `/mp` agent context).

**The load-bearing rule stayed in `AGENTS.md` → `## Visual-change device gate`.** This file is
the *how*; the gate is the *when*. Read this only when you actually need to attach a device.

**User directive, 2026-07-12:** VirtualBox is retired for this project. Use the primary Windows host
and local ADB only. Never try `10.0.2.2:5555`, `ADB_SERVER_SOCKET`, a VirtualBox NAT proxy, or any
other guest attach path by default. Treat the old guest instructions below as historical context
only; use them again only if the user explicitly restores VirtualBox in a future session.

**A connected test device is mandatory for any instrumented (`connectedDebugAndroidTest`) run — never
run, or claim to run, on-device tests without one.** Use the connection recorded in this section (the
verified default below). **Always inspect an already-listed local `device` serial before restarting
ADB.** Accept either AVD id `Pixel_5_API_34`, or its current
local alias `Pixel_5`, when SDK is `34` and boot is complete. Only if both local discovery and the
documented local attach fail, or the discovered device is
wrong/offline/unauthorized/lost, STOP and ask the user where/how the test device is connected now
(address / serial / method), then update this file with their answer so it is not asked again while
it keeps working. (Claude keeps the same fact in its `mymoney-device-connection` memory memo.)

Verified on 2026-07-12:

- The currently installed host AVD id is `Pixel_5` (`Pixel 5`, Android 14 / API 34), exposed locally
  as `emulator-5554`. Historical environments may report the equivalent id `Pixel_5_API_34`; both
  are valid only with SDK `34` and `sys.boot_completed=1`.
- **Serial drift warning:** a later session recorded Pixel 5 / API 34 on `emulator-5556`, with
  `emulator-5554` being a different AVD (Pixel 9 / API 37). Never hard-code the serial — always
  resolve it with the local-host discovery block below, which matches on AVD id + SDK + boot state.
- On the Windows host, use the existing local serial directly. Do not run `adb kill-server` or
  `adb connect 10.0.2.2:5555` when `adb devices -l` already lists a healthy emulator.
- Historical only: `10.0.2.2:5555` was the retired VirtualBox NAT route. Do not attempt it unless
  the user explicitly restores VirtualBox.
- For Gradle `connected*AndroidTest`, do not use the remote serial directly.
  AGP 8.7.3 UTP attempts to write a profile filename containing that serial and
  fails on Windows with `java.io.FileNotFoundException: Invalid file path`.
  Use `scripts/run_connected_test_on_host_avd.ps1`, which validates and uses a safe local serial;
  the old VirtualBox ADB proxy was retired on 2026-05-29.
- Setting `ADB_SERVER_SOCKET` alone is insufficient for Gradle: the CLI sees
  the local serial, but UTP/DDMLib still selects the guest ADB server.
- Do not use `Pixel 10 Pro XL API 37` for current Compose instrumentation: the
  Espresso input path fails on API 37 with an `InputManager.getInstance` lookup error.

Historical VirtualBox guest sequence (retired; do not run without explicit user instruction):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:ANDROID_SDK_ROOT\emulator;$env:PATH"
$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
$device = '10.0.2.2:5555'

& $adb kill-server
& $adb start-server
& $adb connect $device
& $adb devices -l
& $adb -s $device shell getprop ro.boot.qemu.avd_name   # Pixel_5_API_34
& $adb -s $device shell getprop ro.build.version.sdk     # 34
& $adb -s $device shell getprop sys.boot_completed       # 1
```

Local-host discovery (the default and only active connection path):

```powershell
& $adb devices -l
$device = $null
foreach ($serial in ((& $adb devices | Select-String "`tdevice$").Line | ForEach-Object { ($_ -split "`t")[0] })) {
  $avd = (& $adb -s $serial shell getprop ro.boot.qemu.avd_name).Trim()
  $sdk = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
  $boot = (& $adb -s $serial shell getprop sys.boot_completed).Trim()
  $validAvd = $avd -eq 'Pixel_5_API_34' -or $avd -eq 'Pixel_5'
  if ($validAvd -and $sdk -eq '34' -and $boot -eq '1') { $device = $serial; break }
}
if (-not $device) { throw 'Pixel 5 API 34 AVD not connected or not boot-complete' }
```

Connected verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':app:connectedDebugAndroidTest'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':core:designsystem:connectedDebugAndroidTest'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':core:database:connectedDebugAndroidTest'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':core:datastore:connectedDebugAndroidTest'
```

The `-ExecutionPolicy Bypass` applies to this signed-off local helper invocation
only; the machine policy otherwise blocks `.ps1` scripts. The helper waits 60
seconds after every Gradle instrumented-test run, as required by the current
device-remediation task.

Visual smoke check and screenshot capture:

```powershell
.\gradlew.bat --no-daemon :app:installDebug --console=plain
& $adb -s $device shell am force-stop com.kshavrin.mymoney
& $adb -s $device shell am start -W -n com.kshavrin.mymoney/.MainActivity
New-Item -ItemType Directory -Force -Path 'build\visual-check' | Out-Null
& $adb -s $device shell screencap -p /sdcard/mymoney-check.png
& $adb -s $device pull /sdcard/mymoney-check.png 'build\visual-check\mymoney-check.png'
```

For a visual review, load the pulled PNG with the local image viewer tool. If a manual ADB command
has no device, run local-host discovery. Do not fall back to the retired VirtualBox NAT sequence.
For Gradle instrumented tests, use the local helper.
