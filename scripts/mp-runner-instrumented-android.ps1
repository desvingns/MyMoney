param(
    [Parameter(Mandatory = $true)]
    [string] $TestClass,

    [string[]] $Tasks = @(':app:connectedDebugAndroidTest')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$helper = Join-Path $PSScriptRoot 'run_connected_test_on_host_avd.ps1'
$report = 'app/build/reports/androidTests/connected/debug/index.html'
$result = [ordered]@{
    pass = $false
    connected_tests = '0 passed / 0 failed / 0 skipped'
    report = $report
    xml = $null
    errors = @()
}

try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $helper -Tasks $Tasks -TestClass $TestClass
    $gradleExit = $LASTEXITCODE
} catch {
    $gradleExit = 1
    $result.errors += $_.Exception.Message
}

$xmlRoot = Join-Path $repoRoot 'app/build/outputs/androidTest-results/connected'
$xmlFiles = @()
if (Test-Path $xmlRoot) {
    $xmlFiles = Get-ChildItem -Path $xmlRoot -Recurse -Filter 'TEST-*.xml' |
        Sort-Object LastWriteTime -Descending
}

if ($xmlFiles.Count -eq 0) {
    $result.errors += "No connected test XML found under $xmlRoot"
} else {
    $latest = $xmlFiles[0]
    $result.xml = Resolve-Path -Relative $latest.FullName
    try {
        [xml] $doc = Get-Content -LiteralPath $latest.FullName
        $suite = $doc.testsuite
        $tests = [int] $suite.tests
        $failures = [int] $suite.failures
        $errors = [int] $suite.errors
        $skipped = [int] $suite.skipped
        $passed = $tests - $failures - $errors - $skipped
        $result.connected_tests = "$passed passed / $failures failed / $skipped skipped"
        $result.pass = ($gradleExit -eq 0 -and $tests -gt 0 -and $failures -eq 0 -and $errors -eq 0 -and $skipped -eq 0)
    } catch {
        $result.errors += "Could not parse $($latest.FullName): $($_.Exception.Message)"
    }
}

$json = $result | ConvertTo-Json -Compress -Depth 5
Write-Output $json
if ($result.pass) { exit 0 }
exit 1
