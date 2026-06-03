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

**A connected test device is mandatory for any instrumented (`connectedDebugAndroidTest`) run — never
run, or claim to run, on-device tests without one.** Use the connection recorded in this section (the
verified default below). If `adb devices` is empty, the AVD is wrong, or the attachment was lost
mid-session, STOP and ask the user where/how the test device is connected now (address / serial /
method), then update this section with their answer so it is not asked again while it keeps working.
(Claude keeps the same fact in its `mymoney-device-connection` memory memo.)

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

### Visual-change device gate

For `$mp --phase`, `$mp --feature`, `$mp --bugfix`, `$mp --device`, and `$mp --fit`, a
visual-surface task that requires visual autotests has a hard pre-flight gate.
This applies only to explicitly visual work: UI fidelity, screenshot or
reference comparison, Compose UI/instrumented coverage, screen layout, theme,
animation, visual QA, or similar changes where a device-rendered result matters.

Before starting agents or claiming verification for such work, confirm the
documented `Pixel_5_API_34` connection and boot state (`ro.boot.qemu.avd_name`
must be `Pixel_5_API_34`, SDK `34`, and `sys.boot_completed` must be `1`). If
the device is absent, wrong, offline, unauthorized, or loses attachment, STOP
and ask the user to start/connect `Pixel_5_API_34` first. Correct development
cannot proceed without visual testing: do not continue blind, do not replace the
device visual gate with JVM-only checks, and do not claim visual tests passed.
The archived `$cmp` fallback (`.claude/_archive_pre_mp/`) must follow this same gate only if
the user explicitly restores it for fallback work.

## Testing stack (TDD §12, lines 2553–2661)

- JUnit 4 + Turbine + `kotlinx-coroutines-test`.
- KSP `room-testing` for DB instrumentation tests.
- Compose UI testing for screens.
- **Fakes only at repository boundary.** No mocking framework. Real Room (in-memory for unit, on-device for instrumentation).

## Comments policy

Default to **zero comments**. Only add when WHY is non-obvious — a hidden constraint, subtle invariant, workaround for a specific bug. Never narrate WHAT (well-named identifiers do that). Don't reference the current task/PR/issue — those belong in commit messages.

## Project state files — important note

This project now uses MP Dev's SPEC board (`.claude/specs/{backlog,active,done}/`) plus the implementation-plan phase model (PROGRESS). **PROGRESS.md is the sole writer of phase/release state.** The root `STATE.md`, `ROADMAP.md`, and `DOCUMENTATION.md` files are one-line stub redirects pointing at the implementation plan and the TDD.

The `mp-docs` agent is **inert** in this project via `.claude/mp/extras/mp-docs.md`; it must not rewrite `STATE.md`, `ROADMAP.md`, `DOCUMENTATION.md`, or `CLAUDE.md`. The old CMP docs behavior is legacy-only and must not be used for new Codex MP work.

## Where to find things

| Need | Location |
|---|---|
| What to work on right now | `docs/implementation_plan/PROGRESS.md` (active row) |
| How to run a session | `docs/implementation_plan/README.md` §2 |
| Phase task lists | `docs/implementation_plan/phases/PHASE_NN_*.md` |
| Cross-phase reference table | `docs/implementation_plan/00_overview.md` |
| Authoritative spec | `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (cite line ranges, never paraphrase) |
| MyMoney-specific MP agent guidance | `.claude/mp/extras/*.md` (shared by Claude and Codex) |
| Archived CMP fallback | `.claude/_archive_pre_mp/` (Claude `/cmp` + Codex `$cmp`, archived 2026-06-03) |
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

- Invoke `$mp --feature <description>`, `$mp --feature --next`, `$mp --bugfix <description>`,
  `$mp --discuss <topic>`, `$mp --spec <description>`, `$mp --coverage`, `$mp --device <Sxx>`,
  `$mp --fit`, `$mp --plan`, `$mp --phase`, `$mp --check`, `$mp --improve`, or `$mp --reflect`.
- `$mp` is the primary project-local Codex skill in `.agents/skills/mp-dev/SKILL.md`. It is a thin
  Codex bridge over the canonical Claude `mp-dev` plugin at
  `C:\Users\k.shavrin\.claude\plugins\cache\mobile-pipeline\mp-dev\1.5.0`.
- Claude and Codex share the same project configuration and overrides:
  `.claude/mp/config.json`, `.claude/mp/extras/*.md`, and `.claude/specs/{backlog,active,done}/`.
  Put project-specific skill/agent improvements in `.claude/mp/extras/*` first so both surfaces stay
  synchronized. Use `$mp --improve` / `$mp --reflect` only for plugin-level improvements.
- Claude and Codex may both use the MP Dev board, but only one active SPEC should be implemented at a
  time unless the work is explicitly split into disjoint backlog SPECs. Before starting implementation,
  check `.claude/specs/active/` and avoid racing another agent on the same SPEC.
- `$mp --device <Sxx>` runs one on-device instrumented-test slice for a single control: it reads
  `docs/DEVICE_VERIFICATION_PROGRESS.md` and `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md`, writes one
  Compose-UI test, runs it on `Pixel_5_API_34` via `mp-runner-instrumented-android` +
  `scripts/run_connected_test_on_host_avd.ps1`, then updates the tracker. One control per run; never
  pushes.
- `$mp --phase`, `$mp --feature`, `$mp --bugfix`, `$mp --device`, and `$mp --fit` must run the
  visual-change device gate above before any agent work when the task is explicitly visual and needs
  visual/device autotests.
- Native specialists live in `.codex/agents/mp-*.toml`. They read the matching canonical Claude
  `mp-dev` agent body and then `.claude/mp/extras/<agent>.md` if present. `mp-runner-instrumented-android`
  is the only MP specialist allowed to invoke PowerShell, and only for the host-AVD helper.
- Codex Bash compatibility: the current environment may not have `bash` in `PATH`. If Bash is absent,
  do not require the Claude deterministic `.sh` scripts; use native Codex `mp-reviewer-android` and
  `mp-runner-android` agent fallback paths instead.
- Keep `docs/implementation_plan/PROGRESS.md` as the only phase/release state writer. The MP docs step
  remains inert via `.claude/mp/extras/mp-docs.md`.
- `$cmp` is an archived fallback (`.claude/_archive_pre_mp/`, archived 2026-06-03 — not deleted). Do not
  use it for new Codex work; restore it from the archive only if the user explicitly asks for historical CMP behavior.
- Push only after tested files are committed and the user explicitly approves the final gate.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, invoke the `skill` tool with `skill: "graphify"` before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
