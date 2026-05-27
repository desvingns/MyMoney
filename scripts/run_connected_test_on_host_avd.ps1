param(
    [string[]] $Tasks = @(':app:connectedDebugAndroidTest'),
    [string] $TestClass
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"
Remove-Item Env:ADB_SERVER_SOCKET -ErrorAction SilentlyContinue

$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
$node = (Get-Command node -ErrorAction Stop).Source
$existingListener = Get-NetTCPConnection -LocalPort 5037 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($existingListener) {
    $listenerProcess = Get-Process -Id $existingListener.OwningProcess -ErrorAction SilentlyContinue
    if ($listenerProcess -and $listenerProcess.ProcessName -eq 'adb') {
        Stop-Process -Id $existingListener.OwningProcess -Force
    } else {
        throw "Port 5037 is already in use by PID $($existingListener.OwningProcess)."
    }
}

$proxyScript = 'const n=require("net"),s=n.createServer(c=>{const u=n.connect(5037,"10.0.2.2");c.pipe(u);u.pipe(c);c.on("error",()=>u.destroy());u.on("error",()=>c.destroy())});s.listen(5037,"127.0.0.1")'
$proxyArg = '"' + $proxyScript.Replace('"', '\"') + '"'
$proxy = Start-Process -FilePath $node -ArgumentList @('-e', $proxyArg) -WindowStyle Hidden -PassThru
$testStarted = $false
$exitCode = 1

try {
    Start-Sleep -Seconds 1
    $devices = (& $adb devices -l | Out-String)
    if ($devices -notmatch 'emulator-5554\s+device') {
        throw "Host AVD is not reachable through the local ADB proxy.`n$devices"
    }
    if ((& $adb -s emulator-5554 shell getprop ro.boot.qemu.avd_name).Trim() -ne 'Pixel_5_API_34') {
        throw 'Connected emulator is not Pixel_5_API_34.'
    }
    if ((& $adb -s emulator-5554 shell getprop sys.boot_completed).Trim() -ne '1') {
        throw 'Pixel_5_API_34 is not boot-complete.'
    }

    $gradleArgs = @('--no-daemon') + $Tasks
    if ($TestClass) {
        $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
    }
    $gradleArgs += '--console=plain'

    $testStarted = $true
    Push-Location $repoRoot
    try {
        & '.\gradlew.bat' @gradleArgs
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    if ($testStarted) {
        Start-Sleep -Seconds 60
    }
    Stop-Process -Id $proxy.Id -Force -ErrorAction SilentlyContinue
}

exit $exitCode
