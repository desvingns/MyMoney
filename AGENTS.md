# AGENTS.md — MyMoney project cheatsheet

Read first when joining this codebase. Project state lives in the compact head of `docs/implementation_plan/PROGRESS.md` — open it before doing anything else, and open `docs/implementation_plan/log/*.md` only on demand.

## What this project is

MyMoney is an Android money-tracking app, structurally a re-implementation of Monefy v1.0 with intentional improvements. **Authoritative spec**: `TDD/MyMoney/MyMoney_TDD.md` (repo-relative; English prose + EN/RU string tables, 2868 lines). The spec is the source of truth — every design decision traces back to a TDD section.

The implementation is broken into **15 sequential phases** under `docs/implementation_plan/phases/`. The current phase is recorded in `docs/implementation_plan/PROGRESS.md`. Every Codex session follows the **session protocol** documented in `docs/implementation_plan/README.md` §2 (read compact PROGRESS head → open active phase → re-read TDD anchor lines → tick checkboxes → run verification → update PROGRESS/archive old entries → stop).

## Project glossary

| Token | Meaning |
|---|---|
| **TDD** | `TDD/MyMoney/MyMoney_TDD.md` — authoritative spec. |
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

## Emulator access on the Windows host

VirtualBox is retired (user directive, 2026-07-12) — Windows host + local ADB only. Never use
`10.0.2.2:5555`, `ADB_SERVER_SOCKET`, or any VirtualBox NAT path.

**A connected device is mandatory for any `connectedDebugAndroidTest` run — never run, or claim to
run, on-device tests without one.** Target AVD: Pixel 5 / API 34 (id `Pixel_5` or `Pixel_5_API_34`,
SDK `34`, `sys.boot_completed=1`). **Resolve the serial by discovery, never hard-code it** — it has
drifted between `emulator-5554` and `emulator-5556` across sessions.

For Gradle instrumented runs use `scripts/run_connected_test_on_host_avd.ps1` (AGP 8.7.3 UTP writes
a profile filename from the serial and fails on Windows with an invalid-path error otherwise).

Full recipes — discovery loop, connected-verification commands, screenshot capture, and the retired
VirtualBox sequence — live in **`docs/DEVICE_SETUP.md`**. Read it only when actually attaching a
device; the gate below is what governs *when* that is required.

## Visual-change device gate

For `$mp --phase`, `$mp --feature`, `$mp --bugfix`, `$mp --device`, and `$mp --fit`, a
visual-surface task that requires visual autotests has a hard pre-flight gate.
This applies only to explicitly visual work: UI fidelity, screenshot or
reference comparison, Compose UI/instrumented coverage, screen layout, theme,
animation, visual QA, or similar changes where a device-rendered result matters.

Before starting agents or claiming verification for such work, confirm the documented Pixel 5 API
34 connection and boot state (`ro.boot.qemu.avd_name` may be `Pixel_5_API_34` or `Pixel_5`, SDK must
be `34`, and `sys.boot_completed` must be `1`). If
the documented attach fails or hangs, run the local-host fallback discovery above
before declaring the device absent. If both paths fail, or the device is wrong,
offline, unauthorized, or loses attachment, STOP and ask the user to start/connect
the Pixel 5 API 34 AVD first. Correct development cannot proceed without visual testing:
do not continue blind, do not replace the device visual gate with JVM-only checks,
and do not claim visual tests passed.
The archived `$cmp` fallback (`.claude/_archive_pre_mp/`) must follow this same gate only if
the user explicitly restores it for fallback work.

## Testing stack (TDD §12, lines 2553–2661)

- JUnit 4 + Turbine + `kotlinx-coroutines-test`.
- KSP `room-testing` for DB instrumentation tests.
- Compose UI testing for screens.
- **Fakes only at repository boundary.** No mocking framework. Real Room (in-memory for unit, on-device for instrumentation).

## Comments policy

Default to **zero comments**. Only add when WHY is non-obvious — a hidden constraint, subtle invariant, workaround for a specific bug. Never narrate WHAT (well-named identifiers do that). Don't reference the current task/PR/issue — those belong in commit messages.

## File deletion — archive, never delete

Never delete files. When running the `/mp` / `$mp` skill (or any task), any file that should be removed — a stale/redundant SPEC, a superseded duplicate on the `.claude/specs/` board, dead artifacts — is **moved to `archive/`** (repo root, git-ignored) instead of being deleted, then reported to the user for manual deletion. Preserve a recognizable name (add a `.<reason>` suffix, e.g. `.backlog-stale`, when the name would otherwise collide). The user empties `archive/` by hand.

## Shared cross-tool workspace + second brain

- `.ai/memory/MEMORY.md` (git-tracked) is the durable memory BOTH tools share for this repo;
  `.ai/handoff.md` is the cross-tool session scratchpad. Phase/release state still lives ONLY
  in `docs/implementation_plan/PROGRESS.md` — the `.ai/` files never compete with it.
- Cross-project knowledge lives in the second brain: `D:\Pet\brain` on the host,
  `C:\Pet\brain` in the VirtualBox guest (clone of `github.com/desvingns/brain`; `git pull`
  first). Entry point `INDEX.md`; this repo's card: `brain/projects/mymoney.md`. Lessons that
  generalize beyond MyMoney go to `brain/inbox/` (human-gated promotion via `/brain promote`).

## Project state files — important note

This project now uses MP Dev's SPEC board (`.claude/specs/{backlog,active,done}/`) plus the implementation-plan phase model (PROGRESS). **PROGRESS.md is the sole writer of phase/release state.** `PROGRESS.md` is now a compact head; full historical session entries live under `docs/implementation_plan/log/YYYY-MM.md` and are read on demand only. The root `STATE.md`, `ROADMAP.md`, and `DOCUMENTATION.md` files are one-line stub redirects pointing at the implementation plan and the TDD.

The `mp-docs` agent is **inert** in this project via `.claude/mp/extras/mp-docs.md`; it must not rewrite `STATE.md`, `ROADMAP.md`, `DOCUMENTATION.md`, or `CLAUDE.md`. The old CMP docs behavior is legacy-only and must not be used for new Codex MP work.

## Where to find things

| Need | Location |
|---|---|
| What to work on right now | `docs/implementation_plan/PROGRESS.md` compact head (active row + last three entries) |
| Historical progress entries | `docs/implementation_plan/log/YYYY-MM.md` (open on demand only) |
| How to run a session | `docs/implementation_plan/README.md` §2 |
| Phase task lists | `docs/implementation_plan/phases/PHASE_NN_*.md` |
| Cross-phase reference table | `docs/implementation_plan/00_overview.md` |
| Authoritative spec | `TDD/MyMoney/MyMoney_TDD.md` (cite line ranges, never paraphrase) |
| MyMoney-specific MP agent guidance | `.claude/mp/extras/` (shared by Claude and Codex; read role-specific files on demand) |
| Device/emulator attach recipes | `docs/DEVICE_SETUP.md` (read only when attaching a device) |
| Codex `$mp` operating rules | `docs/CODEX_PIPELINE.md` |
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

Codex-side `$mp` invocation modes, agent shims, token-budget startup, and deterministic
reviewer/runner rules live in **`docs/CODEX_PIPELINE.md`**. Claude-side deltas are in `CLAUDE.md`.
Shared config for both surfaces: `.claude/mp/config.json`, `.claude/mp/extras/`,
`.claude/specs/{backlog,active,done}/`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, invoke the `skill` tool with `skill: "graphify"` before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
