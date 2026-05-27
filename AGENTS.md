# AGENTS.md — MyMoney project cheatsheet

Read first when joining this codebase. Project state lives in `docs/implementation_plan/PROGRESS.md` — open it before doing anything else.

## What this project is

MyMoney is an Android money-tracking app, structurally a re-implementation of Monefy v1.0 with intentional improvements. **Authoritative spec**: `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (English prose + EN/RU string tables, 2409 lines). The spec is the source of truth — every design decision traces back to a TDD section.

The implementation is broken into **15 sequential phases** under `docs/implementation_plan/phases/`. The current phase is recorded in `docs/implementation_plan/PROGRESS.md`. Every Codex session follows the **session protocol** documented in `docs/implementation_plan/README.md` §2 (read PROGRESS → open active phase → re-read TDD anchor lines → tick checkboxes → run verification → update PROGRESS → stop).

## Project glossary

| Token | Meaning |
|---|---|
| **TDD** | `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` — authoritative spec. |
| **Sxx** | Screen ID, e.g. `S01` = main dashboard day-period. Inventory: TDD §3.1, lines 265–298. |
| **BR-x** | Business rule, e.g. `BR-7` = "calculator: dot allowed once per operand". Full list: TDD §5, lines 1172–1207. |
| **AS-x** | Resolved decision (formerly assumption), e.g. `AS-12` = "Pick a date opens range picker". Full list: TDD §14.1, lines 2727–2750. |
| **OQ-x** | DevOps prerequisite still open, e.g. `OQ-1` = "create Sentry project, get new DSN". TDD §14.2, lines 2751–2763. Tracked in `PROGRESS.md` under "Deferred work". |
| **UDF** | Unidirectional Data Flow — State / Event / Action pattern. TDD §2.3, lines 181–228. |

## Intentional Monefy deviations (locked decisions)

These are **not bugs** — never "fix" them back to Monefy v1.0 behaviour:

- **AS-12**: "Pick a date" opens a **two-date range picker** (not a single-day picker). Adds `CustomRange(start, end)` mode.
- **AS-14**: Donut chart shows percentage labels on slices **≥3%** (not Monefy's ≥5%). Busier chart, more information density.

## Package and namespace

- Application ID: `com.kshavrin.mymoney` (locked, see TDD OQ-3).
- All `:core:*` modules: `com.kshavrin.mymoney.core.<name>`. All `:feature:*` modules: `com.kshavrin.mymoney.feature.<name>`.
- The current Android Studio template namespace `com.example.mymoney` will be migrated in PHASE_01.

## Stack & Versions (locked by TDD §2.1 + §8)

| Component | Version |
|---|---|
| Gradle | 8.10+ |
| Kotlin | 2.0.21 + `org.jetbrains.kotlin.plugin.compose` |
| AGP | 8.7+ |
| KSP | matching Kotlin (no kapt — Hilt + Room both via KSP) |
| Compose | BoM 2024.10+ with Material 3 |
| Hilt | 2.52 + hilt-navigation-compose |
| Room | 2.6.1 |
| DataStore Preferences | 1.1.1 |
| EncryptedSharedPreferences | 1.1.0-alpha07 |
| androidx.navigation-compose | 2.8.4 |
| kotlinx-coroutines | 1.9 |
| kotlinx.serialization | 1.7 |
| WorkManager | 2.10 |
| Retrofit / OkHttp | 2.11 / 4.12 |
| Sentry | (DSN from `BuildConfig.SENTRY_DSN` — OQ-1) |
| **SDK targets** | `minSdk: 31`, `targetSdk: 36`, `compileSdk: 36` |
| **JVM** | JDK 21 (JBR recommended on Windows) |

Plugins declared in `gradle/libs.versions.toml` only — never literal versions in module `build.gradle.kts`.

## Module structure (PHASE_01 target, TDD §2.2)

```
:app
:core:ui            :core:designsystem    :core:database
:core:datastore     :core:network         :core:sync
:core:domain        :core:common          :core:testing
:feature:onboarding         :feature:dashboard
:feature:transaction        :feature:transactionslist
:feature:settings           :feature:dictionaries
:feature:cloudsync          :feature:lockscreen
```

Rule: `:feature:*` may depend on `:core:*` and `:domain`. Never `:feature:*` → `:feature:*`.

## Architecture pattern (TDD §2.3, lines 181–228)

MVVM + Unidirectional Data Flow.

- ViewModels expose `StateFlow<S>` where `S` is an immutable `UiState` data class.
- Actions are a `SharedFlow` with `replay = 0` (one-shot events: navigation, snackbar, dialog).
- Events are user gestures → ViewModel methods.
- Domain ops return `kotlin.Result<T>`. Repository implementations catch and remap to `SyncException(SyncError)`. ViewModels translate to `state.errorBanner` string-res. All throwables flow to Sentry.

## Data conventions

| Concern | Convention |
|---|---|
| Money in domain | `BigDecimal` (never `Double` outside Room) |
| Money in Room | `Double` (TypeConverter at DAO boundary) |
| Time in domain | `LocalDate` / `Instant` |
| Time in Room | `Long` epoch-millis (TypeConverter) |
| Strings | English default (`res/values/strings.xml`), Russian translation in `res/values-ru/strings.xml`. No hardcoded user-facing strings. |
| Identifiers | English only. |

## Hilt DI conventions

- `@Singleton` for repositories and SDK wrappers.
- `@HiltViewModel` for ViewModels.
- `@Named` `CoroutineDispatcher` providers — **never** use `Dispatchers.IO` directly inside a class.

## Persistence

- Room 2.6.1 for transactional data; KSP-generated.
- DataStore Preferences 1.1.1 for `AppSettings`.
- EncryptedSharedPreferences 1.1.0-alpha07 for secrets (Dropbox token, GDrive email, PIN hash).

## Build commands

```bash
./gradlew :app:assembleDebug                   # full debug build
./gradlew :app:kspDebugKotlin                  # rerun KSP after Room/Hilt annotation changes
./gradlew :app:testDebugUnitTest               # unit tests
./gradlew :app:connectedDebugAndroidTest       # instrumentation (real device/emulator)
```

`JAVA_HOME` must point to a JDK 21 runtime. On Windows under Git Bash, prefer Android Studio's bundled JBR (see `~/.bashrc` snippet at end of this file).

## Emulator access from the VirtualBox guest

Codex runs against this checkout from a Windows VirtualBox guest. Nested virtualization
is not available in that guest, so start the emulator in Android Studio on the primary
Windows host, not inside the guest.

Verified on 2026-05-27:

- Use host AVD `Pixel_5_API_34` (`Pixel 5`, Android 14 / API 34).
- For manual ADB/screenshot commands, reach the primary Windows host through the
  VirtualBox NAT gateway with `adb connect 10.0.2.2:5555`; the guest reports
  that attachment under serial `10.0.2.2:5555`.
- For Gradle `connected*AndroidTest`, do not use the remote serial directly.
  AGP 8.7.3 UTP attempts to write a profile filename containing that serial and
  fails on Windows with `java.io.FileNotFoundException: Invalid file path`.
  Use `scripts/run_connected_test_on_host_avd.ps1`, which proxies localhost ADB
  to the host ADB server so UTP sees safe serial `emulator-5554`.
- Setting `ADB_SERVER_SOCKET` alone is insufficient for Gradle: the CLI sees
  `emulator-5554`, but UTP/DDMLib still selects the guest ADB server.
- Do not use `Pixel 10 Pro XL API 37` for current Compose instrumentation: the
  Espresso input path fails on API 37 with an `InputManager.getInstance` lookup error.

From Codex / PowerShell in the guest:

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

For a visual review, load the pulled PNG with the local image viewer tool. If a
manual ADB command has no device, first confirm that `Pixel_5_API_34` is booted
on the primary Windows host, then repeat the guest `adb kill-server` /
`adb start-server` / `adb connect 10.0.2.2:5555` sequence. For Gradle
instrumented tests, use the helper instead of that remote attachment.

## Testing stack (TDD §12, lines 2553–2661)

- JUnit 4 + Turbine + `kotlinx-coroutines-test`.
- KSP `room-testing` for DB instrumentation tests.
- Compose UI testing for screens.
- **Fakes only at repository boundary.** No mocking framework. Real Room (in-memory for unit, on-device for instrumentation).

## Comments policy

Default to **zero comments**. Only add when WHY is non-obvious — a hidden constraint, subtle invariant, workaround for a specific bug. Never narrate WHAT (well-named identifiers do that). Don't reference the current task/PR/issue — those belong in commit messages.

## Project state files — important note

This project has both CMP's iteration model (STATE / ROADMAP / DOCUMENTATION) and the implementation plan's phase model (PROGRESS). **PROGRESS.md is the sole writer of project state.** The three CMP root files are one-line stub redirects pointing at the implementation plan — see `STATE.md`, `ROADMAP.md`, `DOCUMENTATION.md`.

The `cmp-docs` agent is **inert** in this project (`.Codex/agents/cmp-docs.md` body has been replaced with a notice). The `/cmp` orchestrator has been patched to skip its docs-update step and to add two new flags — `--phase` (automates the session protocol) and `--check` (read-only state consistency validator).

## Where to find things

| Need | Location |
|---|---|
| What to work on right now | `docs/implementation_plan/PROGRESS.md` (active row) |
| How to run a session | `docs/implementation_plan/README.md` §2 |
| Phase task lists | `docs/implementation_plan/phases/PHASE_NN_*.md` |
| Cross-phase reference table | `docs/implementation_plan/00_overview.md` |
| Authoritative spec | `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (cite line ranges, never paraphrase) |
| MyMoney-specific agent guidance | `.Codex/cmp-mymoney/{developer,reviewer,tester}-extras.md` |
| Cross-session memory | `C:\Users\desvi\.Codex\projects\C--Pet-MyMoney\memory\` (MEMORY.md is the index, auto-loaded) |

## JBR auto-detect snippet (Git Bash on Windows)

Add to `~/.bash_profile`:

```bash
for c in \
    "$HOME"/.jbr/jbr_jcef-21* \
    "/c/Program Files/Android/Android Studio/jbr" \
    "$LOCALAPPDATA/Programs/Android Studio/jbr"; do
  if [ -x "$c/bin/java" ] || [ -x "$c/bin/java.exe" ]; then
    export JAVA_HOME="$c"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done
```

## Native Codex Pipeline

- Invoke `$cmp --phase`, `$cmp --feature <description>`, `$cmp --bugfix <description>`,
  `$cmp --discuss <topic>`, or `$cmp --check`.
- `$cmp` is a project-local Codex skill in `.agents/skills/cmp/SKILL.md`. It follows the
  customized canonical workflow in `.claude/commands/cmp.md`, including the MyMoney-only
  `--phase` and `--check` modes.
- When subagents are requested, use native specialists from `.codex/agents/*.toml`.
- Keep `docs/implementation_plan/PROGRESS.md` as the only project-state writer. The
  `cmp-docs` step remains disabled for this project.
- Push only after tested files are committed and the user explicitly approves the final gate.
