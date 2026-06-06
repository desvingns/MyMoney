# CLAUDE.md — MyMoney project cheatsheet

Read first when joining this codebase. Project state lives in `docs/implementation_plan/PROGRESS.md` — open it before doing anything else.

## What this project is

MyMoney is an Android money-tracking app, structurally a re-implementation of Monefy v1.0 with intentional improvements. **Authoritative spec**: `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (English prose + EN/RU string tables, 2409 lines). The spec is the source of truth — every design decision traces back to a TDD section.

The implementation is broken into **15 sequential phases** under `docs/implementation_plan/phases/`. The current phase is recorded in `docs/implementation_plan/PROGRESS.md`. Every Claude session follows the **session protocol** documented in `docs/implementation_plan/README.md` §2 (read PROGRESS → open active phase → re-read TDD anchor lines → tick checkboxes → run verification → update PROGRESS → stop).

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

## On-device / emulator testing

Single machine: run the emulator in Android Studio (or attach a device over USB),
then call the instrumentation task directly — no NAT bridge, proxy, or helper script.

```bash
adb devices                                    # confirm the emulator/device is listed
./gradlew :app:connectedDebugAndroidTest
./gradlew :core:designsystem:connectedDebugAndroidTest
./gradlew :core:database:connectedDebugAndroidTest
./gradlew :core:datastore:connectedDebugAndroidTest
```

A connected, booted device is **mandatory** before any on-device run — never fake or
skip it. If `adb devices` is empty, start the AVD and retry.

## Testing stack (TDD §12, lines 2553–2661)

- JUnit 4 + Turbine + `kotlinx-coroutines-test`.
- KSP `room-testing` for DB instrumentation tests.
- Compose UI testing for screens.
- **Fakes only at repository boundary.** No mocking framework. Real Room (in-memory for unit, on-device for instrumentation).

## Comments policy

Default to **zero comments**. Only add when WHY is non-obvious — a hidden constraint, subtle invariant, workaround for a specific bug. Never narrate WHAT (well-named identifiers do that). Don't reference the current task/PR/issue — those belong in commit messages.

## File deletion — archive, never delete

Never delete files. When running the `/mp` skill (or any task), any file that should be removed — a stale/redundant SPEC, a superseded duplicate on the `.claude/specs/` board, dead artifacts — is **moved to `archive/`** (repo root, git-ignored) instead of being deleted, then reported to the user for manual deletion. Preserve a recognizable name (add a `.<reason>` suffix, e.g. `.backlog-stale`, when the name would otherwise collide). The user empties `archive/` by hand.

## Token-efficient Claude workflow

Keep sessions cheap without lowering rigor:

- **Code search → `Explore` subagent.** When a question means sweeping many files/dirs (find a symbol, trace a convention, locate call-sites), delegate to `Explore` and keep only its conclusion — don't fan raw file dumps into the main context. Use direct `Grep`/`Read` only for a known file or a single targeted lookup.
- **Model routing.** Mechanical work (renames, small/boilerplate edits, formatting) → Sonnet/Haiku. Reserve Opus for architecture, ambiguous design, and hard debugging.
- `/clear` between unrelated tasks; run Gradle with `--console=plain` and grep logs instead of dumping full build output into context.

## Project state files — important note

This project has both CMP's iteration model (STATE / ROADMAP / DOCUMENTATION) and the implementation plan's phase model (PROGRESS). **PROGRESS.md is the sole writer of project state.** The three CMP root files are one-line stub redirects pointing at the implementation plan — see `STATE.md`, `ROADMAP.md`, `DOCUMENTATION.md`.

This project uses the `/mp` marketplace plugin (`mobile-pipeline/mp-dev`), not the legacy `/cmp` fork — `/cmp` and its Codex `$cmp` mirror were archived under `.claude/_archive_pre_mp/` on 2026-06-03 (reversible; see that folder's README). `/mp` provides `--phase` and `--check` natively. The `mp-docs` agent is made **inert** here via `.claude/mp/extras/mp-docs.md` (it returns `{"committed":false}` and writes nothing), so `PROGRESS.md` stays the sole writer of project state.

## /mp auto-push policy (project override)

When a `/mp` pipeline run completes **successfully** — Reviewer pass, Runner green (or a verified-manual pass when the runner script throws its known false negative), and Verifier `pass:true` — **push to `main` automatically, without the Step 4.5 `y/N` gate.** Do NOT stop to ask "Ready to push?"; just print the manual checklist for the record and push. This overrides the orchestrator's default push-confirmation prompt for `--feature` (and the equivalent point in `--bugfix`). The gate is only re-introduced if Verifier returns `pass:false`, the run did not pass cleanly, or the user explicitly asks to hold a given run.

## Where to find things

| Need | Location |
|---|---|
| What to work on right now | `docs/implementation_plan/PROGRESS.md` (active row) |
| How to run a session | `docs/implementation_plan/README.md` §2 |
| Phase task lists | `docs/implementation_plan/phases/PHASE_NN_*.md` |
| Cross-phase reference table | `docs/implementation_plan/00_overview.md` |
| Authoritative spec | `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (cite line ranges, never paraphrase) |
| MyMoney-specific agent guidance | `.claude/mp/extras/` — `mp-{developer,reviewer,tester,runner-instrumented}-android.md` + `mp-docs.md` |
| Cross-session memory | `C:\Users\desvi\.claude\projects\C--Pet-MyMoney\memory\` (MEMORY.md is the index, auto-loaded) |

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

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
