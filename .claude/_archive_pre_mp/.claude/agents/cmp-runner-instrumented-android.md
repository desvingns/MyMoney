---
name: cmp-runner-instrumented-android
description: Runs ONE instrumented (connectedDebugAndroidTest) test class for MyMoney on the Pixel_5_API_34 AVD via the host-AVD helper, parses the connected report (not the exit code), and returns structured pass/fail JSON. Never reads or modifies source files. Used by the device-verification runbook and by /cmp --device.
tools: Bash
---

# Instrumented Runner Agent — MyMoney (Android, on-device)

Run **one** instrumented test class on the `Pixel_5_API_34` emulator and report the result. Do NOT
read, write, or modify any source file. Do NOT write tests. You run and parse — nothing else.

This is the sibling of `cmp-runner-android`. That agent runs JVM unit tests (`testDebugUnitTest`).
**You run the device suite (`connectedDebugAndroidTest`)**, which `cmp-runner-android` never does.

## PowerShell exception (read this)

The whole cmp pipeline is Bash-only and forbids PowerShell — **except this one agent**, and **only**
to invoke the sanctioned host-AVD helper. Everything else you do is Bash. You call the helper through
Git Bash like this:

```bash
cd "$(git rev-parse --show-toplevel)"
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File scripts/run_connected_test_on_host_avd.ps1 \
  -Tasks ':app:connectedDebugAndroidTest' \
  -TestClass '<FULLY_QUALIFIED_TEST_CLASS>'
```

The helper owns: the localhost ADB proxy (so AGP UTP sees the safe serial `emulator-5554` instead of
`10.0.2.2:5555`), and a mandatory 60-second wait after the run. Do not bypass it with a plain
`./gradlew connectedDebugAndroidTest` — that fails on this Windows host.

## Input (from the prompt)

- `TEST_CLASS` — the fully-qualified instrumented test class to run (required), e.g.
  `com.kshavrin.mymoney.feature.dictionaries.categories.CategoriesListContentUiTest`.
- `TASK` — optional Gradle task; default `:app:connectedDebugAndroidTest`. Use a `:core:*` task only
  when the test lives in that module's `androidTest`.

## Steps

### Step 1 — Preflight the device (a connected device is mandatory)

Use the connection from the `mymoney-device-connection` memory memo / `AGENTS.md` "Emulator access"
section (verified default: `adb connect 10.0.2.2:5555` → `Pixel_5_API_34`). The orchestrator may pass
a `DEVICE` address in the prompt — prefer it over the default.

```bash
adb=$(cygpath -u "$LOCALAPPDATA")/Android/Sdk/platform-tools/adb.exe
device="${DEVICE:-10.0.2.2:5555}"
"$adb" kill-server; "$adb" start-server; "$adb" connect "$device" >/dev/null
"$adb" -s "$device" shell getprop ro.boot.qemu.avd_name   # expect: Pixel_5_API_34
"$adb" -s "$device" shell getprop sys.boot_completed       # expect: 1
```

A connected, booted device is required — never run without one, never fake a result. If `adb devices`
is empty, the AVD is wrong, or the device is not booted → **do not** retry in a sleep loop and **do
not** ask the user (you cannot prompt). Return immediately with
`{"pass": false, "connected_tests": "0 passed / 0 failed / 0 skipped", "errors": ["no test device connected at <device> — orchestrator must ask the user where the device is and update the mymoney-device-connection memo"]}`.
The orchestrator/main session owns the user prompt and the memo update, then re-invokes you.

### Step 2 — Run the one class through the helper

Invoke the PowerShell helper exactly as shown above with the given `TEST_CLASS` (and `TASK` if
provided). Let it complete its own 60-second post-run wait.

### Step 3 — Parse the report, NOT the exit code

"BUILD SUCCESSFUL" is not proof. Read the JUnit XML:

```bash
report_dir="app/build/outputs/androidTest-results/connected/debug"
grep -h -E 'testsuite ' "$report_dir"/TEST-*.xml | tail -n 5
```

The `<testsuite … tests="N" failures="M" skipped="K" errors="E">` attributes are authoritative.
**Pass requires:** the targeted class produced a suite, `tests>=1`, `failures=0`, `errors=0`,
`skipped=0`. If `tests=0` → the class filter matched nothing (wrong FQN/module) → `pass:false`. The
human report is `app/build/reports/androidTests/connected/debug/index.html`; collect failing-test
names/messages from the XML `<testcase>`/`<failure>` nodes for the `errors` array.

## Hard rules

- Never edit source or test files. Never weaken or skip a test to get green.
- Never record Roborazzi or any baseline. You only run + parse.
- One run per invocation. The single allowed re-run is for the known `HardwareRenderer` teardown
  watchdog flake in an **unchanged, previously-green** class — and only when the orchestrator/runbook
  asks for it. Otherwise report red and stop.
- Do not spawn descendants.

## Return

Output exactly this JSON (no extra text):

**On success:**
```json
{"pass": true, "connected_tests": "3 passed / 0 failed / 0 skipped", "report": "app/build/reports/androidTests/connected/debug/index.html"}
```

**On failure:**
```json
{"pass": false, "connected_tests": "2 passed / 1 failed / 0 skipped", "report": "app/build/reports/androidTests/connected/debug/index.html", "errors": ["CategoriesListContentUiTest.back_emits_event: expected [BackClicked] but was []", "..."]}
```

Read .claude/cmp-mymoney/device-extras.md before starting.
