# Pre-flight device health gate for full instrumented test runs.
# Run this BEFORE any full :app connected run. It catches the failure mode that wrecked the
# 2026-05-30 session: a 2-FPS emulator (cold-boot/GPU/snapshot regression) that turns every
# Compose `waitUntil` into a ComposeTimeoutException and can crash instrumentation
# (INSTRUMENTATION_ABORTED). Exit code 0 = HEALTHY, 1 = UNHEALTHY (do not start the run).
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight_device_health.ps1
#   ... -Serial emulator-5554           (default)
#   ... -Serial <real-device-serial>    (real device; GL/AVD-name checks auto-relax)
param(
    [string] $Serial = 'emulator-5554',
    [int]    $SmokeTimeoutSec = 240
)
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$helper = Join-Path $repo 'scripts\run_connected_test_on_host_avd.ps1'
$resultsDir = Join-Path $repo 'app\build\outputs\androidTest-results\connected'
$reasons = @()

# 1. device present
$dev = (& $adb devices -l | Out-String)
if($dev -notmatch "$([regex]::Escape($Serial))\s+device"){ $reasons += "device '$Serial' not listed by adb" }

# 2. boot complete
$bc = (& $adb -s $Serial shell getprop sys.boot_completed)
if("$bc".Trim() -ne '1'){ $reasons += "sys.boot_completed != 1" }

# 3. GL renderer must be hardware (SwiftShader = software = the 2-FPS trap)
$gl = ((& $adb -s $Serial shell dumpsys SurfaceFlinger) -split "`n" | Select-String 'GLES:') -join ' '
Write-Host ("GL: " + $gl.Trim())
if($gl -match 'SwiftShader|Software'){ $reasons += "software GL (SwiftShader) -> GPU acceleration is OFF" }

# 4. timed launch smoke: MainActivityLaunchTest must run green and settle in time
$start = Get-Date
$job = Start-Job -ScriptBlock {
    param($h,$c,$l)
    & powershell -NoProfile -ExecutionPolicy Bypass -File $h -TestClass $c *>&1 | Out-File -FilePath $l -Encoding utf8
} -ArgumentList $helper,'com.kshavrin.mymoney.MainActivityLaunchTest','D:\gradletmp\health_launch.txt'
$fin = Wait-Job $job -Timeout $SmokeTimeoutSec
$elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 0)
if(-not $fin){
    Stop-Job $job -ErrorAction SilentlyContinue
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'gradletmp' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    $reasons += "launch smoke TIMEOUT > ${SmokeTimeoutSec}s (device too slow / hung)"
}
Remove-Job $job -Force -ErrorAction SilentlyContinue
$xml = Get-ChildItem -Recurse $resultsDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -ge $start } | Select-Object -First 1
$t=0;$f=0
if($xml){ [xml]$x = Get-Content -LiteralPath $xml.FullName -Raw; foreach($ts in $x.SelectNodes('//testsuite')){ $t+=[int]$ts.tests; $f+=[int]$ts.failures + [int]$ts.errors } }
if($t -lt 1 -or $f -gt 0){ $reasons += "launch smoke not green (tests=$t fail/err=$f)" }
Write-Host ("launch smoke: tests=$t fail/err=$f elapsed=${elapsed}s")

if($reasons.Count -eq 0){ Write-Host "HEALTH: OK"; exit 0 }
else { Write-Host ("HEALTH: FAIL :: " + ($reasons -join '; ')); exit 1 }
