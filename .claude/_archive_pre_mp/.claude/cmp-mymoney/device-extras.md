# cmp device-verification extras — MyMoney

Read this for any **on-device** (instrumented, `connectedDebugAndroidTest`) work: the `--device`
flow, the `cmp-runner-instrumented-android` agent, and any time you write or run a Compose-UI test on
the `Pixel_5_API_34` emulator. The full step-by-step is `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md`
(the runbook); progress lives in `docs/DEVICE_VERIFICATION_PROGRESS.md` (the tracker). These notes are
the short, load-bearing rules.

## A connected test device is mandatory (read this first)

There is no "dry run" — **never** run, or claim to run, an instrumented test without a connected,
booted test device. Before every device run:

1. Take the device connection from the `mymoney-device-connection` memory memo (Claude auto-memory) /
   the `AGENTS.md` "Emulator access" section (Codex) — the verified default is host AVD
   `Pixel_5_API_34` via `adb connect 10.0.2.2:5555`.
2. Confirm it with the preflight (runbook §3). A device must show in `adb devices` AND report
   `Pixel_5_API_34` AND `sys.boot_completed = 1`.
3. **If there is no connection** (empty `adb devices`, wrong AVD, or the attachment was lost
   mid-session): STOP and **ask the user** where/how the test device is connected now
   (address / serial / connection method). **Record their answer to the `mymoney-device-connection`
   memo** so it is never asked again while it keeps working. Then retry the preflight.
4. Only proceed once a device is confirmed. Never silently skip the device run, never fake a report.

(The `cmp-runner-instrumented-android` agent cannot prompt — if it finds no device it returns
`pass:false` with a "no device connected" error; the orchestrator/main session does the asking and
memo update.)

## The loop (non-negotiable)

Write **one** test → run it on `Pixel_5_API_34` → read the parsed report → if green, update the
tracker → **STOP**. Never write a batch of device tests before running them. This exists because the
original mistake was building screens without ever running them on a device.

## Missing-seam policy (the key rule for a less-capable model)

A control with no testable hook is **not** a license to invent. You may add ONLY one of:
`Modifier.testTag("…")`, a `contentDescription`, or changing a `*Content` composable to `public`.
You may **not** add UI, events, ViewModel methods, navigation, or features to make a test pass. If the
control/state genuinely does not exist in production, **SKIP** it and write one line in the tracker
notes (`<control> — no production seam, escalated`). Never weaken a test (`@Ignore`,
`assertTrue(true)`, deleted assertions) to force green.

## How to run on the device

`cmp-runner-android` (JVM unit tests) does **not** run device tests. Use the
`cmp-runner-instrumented-android` agent, which calls the only sanctioned PowerShell command in this
otherwise Bash-only pipeline:

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run_connected_test_on_host_avd.ps1 -TestClass '<FQN>'
```

Never use a bare `./gradlew connectedDebugAndroidTest` — AGP 8.7.3 UTP fails on this Windows host's
remote serial. **Trust the parsed report, not "BUILD SUCCESSFUL":** read
`app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` and require the targeted class ran
with `failures="0"`, `errors="0"`, `skipped="0"`. The known `HardwareRenderer` teardown watchdog flake
in an unchanged previously-green class may be re-run **once**; nothing else gets a retry.

## Pattern B is the workhorse

Render the public `<Screen>Content(state, onEvent)` directly with `createComposeRule()` inside
`MyMoneyTheme { }`, capture events into a list, assert with `runOnIdle { assertEquals(...) }`. The
canonical, copy-paste template is the runbook §5 (taken from the green
`app/src/androidTest/.../dashboard/DashboardContentUiTest.kt`). Prefer matching on
`contentDescription` / visible text over test tags. Look up every string via
`InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.…)` — never a literal
(the app ships EN + RU). Per screen aim for happy / empty / error (TDD §12.4); log any variant the
screen genuinely lacks.

## Keep these OFF in instrumented runs

Sentry, Firebase, and cloud sync are gated off by default — do not enable them. Pattern A (E2E
through real Hilt + Room) needs the Slice-0 infra (`HiltTestRunner`, `TestDatabaseModule`,
`TestDataStoreModule`, catalog + `app/build.gradle.kts` wiring) — build and green-gate that before any
E2E test (runbook §8 Slice 0).

## Known production quirk

The edit screens S22/S24/S26 currently emit `SaveClicked` from the TopAppBar back-arrow (see memory
`mymoney-edit-screen-backarrow-quirk`). Do not assert that as correct behaviour — cover the
unambiguous controls and escalate the back-arrow as a separate `/cmp --bugfix` decision.
