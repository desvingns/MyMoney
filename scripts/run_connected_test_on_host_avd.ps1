param(
    [string[]] $Tasks = @(':app:connectedDebugAndroidTest'),
    [string] $TestClass
)

# Single-machine on-device test runner (VirtualBox NAT proxy retired 2026-05-29).
# The Pixel 5 API 34 AVD runs locally in Android Studio. Discover its current local serial so
# another booted emulator cannot be selected by position. Local emulator serials have no `:`, so
# the AGP 8.7.3 UTP profile-path bug does not apply — we run Gradle directly, no proxy.

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$env:JAVA_HOME = 'D:\For_work\AS\jbr'
# Loopback-blocker fix (2026-05-29): the JDK NIO selector self-pipe binds an AF_UNIX socket under
# java.io.tmpdir. The default %TEMP% (C:\Users\K020B~1.SHA\... — an 8.3 short name forced by the dot
# in the Windows username "k.shavrin") makes AF_UNIX connect() fail with "Invalid argument: connect",
# which surfaces as Gradle's "Unable to establish loopback connection" and blocked all local builds.
# Pointing TMP/TEMP at a clean short path fixes both the launcher and the forked daemon (the daemon
# inherits this environment). GRADLE_OPTS sets the same override explicitly for belt-and-suspenders.
$tmp = 'D:\gradletmp'
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:TMP = $tmp
$env:TEMP = $tmp
$env:GRADLE_OPTS = "-Djdk.net.unixdomain.tmpdir=$tmp"
$env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"
Remove-Item Env:ADB_SERVER_SOCKET -ErrorAction SilentlyContinue

$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
$serial = $null
$connectedSerials = @()
$devices = (& $adb devices -l | Out-String)
foreach ($line in (& $adb devices)) {
    if ($line -notmatch '^(?<serial>\S+)\tdevice$') { continue }
    $candidate = $Matches.serial
    $connectedSerials += $candidate
    $avdName = (& $adb -s $candidate shell getprop ro.boot.qemu.avd_name).Trim()
    $sdkVersion = (& $adb -s $candidate shell getprop ro.build.version.sdk).Trim()
    $bootCompleted = (& $adb -s $candidate shell getprop sys.boot_completed).Trim()
    $validAvdName = $avdName -eq 'Pixel_5_API_34' -or $avdName -eq 'Pixel_5'
    if ($null -eq $serial -and $validAvdName -and $sdkVersion -eq '34' -and $bootCompleted -eq '1') {
        $serial = $candidate
    }
}
if ($null -eq $serial) {
    throw "Pixel 5 API 34 AVD is not connected or boot-complete. Start it in Android Studio and retry.`n$devices"
}
$otherConnectedDevices = @($connectedSerials | Where-Object { $_ -ne $serial })
if ($otherConnectedDevices.Count -gt 0) {
    throw "Refusing to run connected tests because AGP may select every healthy ADB device. Stop or disconnect the non-gate devices first: $($otherConnectedDevices -join ', '). The only permitted device for this helper is $serial (Pixel 5 API 34)."
}
& $adb -s $serial shell input keyevent 82 | Out-Null   # dismiss keyguard

# Pure-JVM modules use kotlin jvmToolchain(17). Gradle's auto-detection does not find the local
# Microsoft JDK 17 and foojay auto-download fails on this host, so point it at the installed JDK 17
# explicitly and disable download. (The build runtime is JBR 21 via JAVA_HOME above.)
$jdk17 = 'C:\Users\k.shavrin\AppData\Local\Programs\Microsoft\jdk-17.0.10.7-hotspot'
$gradleArgs = @('--no-daemon') + $Tasks
if ($TestClass) {
    $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
}
$gradleArgs += "-Porg.gradle.java.installations.paths=$jdk17"
$gradleArgs += '-Porg.gradle.java.installations.auto-download=false'
$gradleArgs += '--console=plain'

$exitCode = 1
Push-Location $repoRoot
try {
    & '.\gradlew.bat' @gradleArgs
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $exitCode
