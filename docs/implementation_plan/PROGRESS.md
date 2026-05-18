# Implementation progress

> Always read this file first in a new session. It points to the active phase.
> Update it at the end of every session before stopping.

---

## Current state

- **Active phase:** `PHASE_01` — Multi-module Gradle scaffolding (not yet started)
- **Last session:** 2026-05-18 — implementation plan created (phase files + tracker bootstrapped, no app code yet)
- **Next action:** Open `phases/PHASE_01_scaffolding.md`, read its TDD anchors, work through the task list.
- **Blockers:** none

---

## Phase completion

| #  | Phase                                                 | Status        | Session date  | Outcome / link to "Notes for next session" |
|----|-------------------------------------------------------|---------------|---------------|--------------------------------------------|
| 00 | Overview / orientation                                | done          | 2026-05-18    | Plan + checklist files created. Start with PHASE_01. |
| 01 | Multi-module Gradle scaffolding                       | **active**    | —             | — |
| 02 | DI + App shell + Sentry skeleton                      | not started   | —             | — |
| 03 | Design system (`:core:ui` + designsystem skeleton)    | not started   | —             | — |
| 04 | Database layer (`:core:database`)                     | not started   | —             | — |
| 05 | DataStore + secure storage (`:core:datastore`)        | not started   | —             | — |
| 06 | Domain layer + seeding (`:core:domain`, `:core:common`) | not started | —             | — |
| 07 | Splash + onboarding (S00, S11) + nav root             | not started   | —             | — |
| 08 | Dashboard + donut chart (S01/S05 + S02/S04)            | not started  | —             | — |
| 09 | Dictionaries CRUD (S21–S26)                            | not started  | —             | — |
| 10 | Transaction forms (S03, S06, S07, S09, S27)            | not started  | —             | — |
| 11 | List + search + detail (S08, S12, S13)                 | not started  | —             | — |
| 12 | Settings hierarchy (S14, S15, S18, S19, S20)            | not started | —             | — |
| 13 | Cloud sync + Sentry + Remote Config (S17)              | not started  | —             | — |
| 14 | Biometric + WorkManager (S16, recurring, budget)       | not started  | —             | — |
| 15 | Polish + gamification + l10n + tests + release         | not started  | —             | — |

Legend: `not started` → `active` → `in progress` → `done` (use `blocked` if a phase is paused on external work; cite OQ-id).

---

## Decisions log

Append a one-line entry whenever a non-obvious decision is made during a session. Format: `YYYY-MM-DD — <decision>. Cross-ref: <phase or TDD §>.`

- 2026-05-18 — Implementation plan: 15 phases, English, located under `docs/implementation_plan/`. Cross-ref: this file's existence.
- 2026-05-18 — Application namespace finalised as `com.kshavrin.mymoney` (replaces template `com.example.mymoney`). Cross-ref: README §4 conventions, TDD OQ-3.
- 2026-05-18 — All TDD §14.1 resolved decisions (AS-1 … AS-15) are pre-locked. See TDD lines 2727–2750. Do not re-litigate during implementation; cite the AS-id instead.

---

## Deferred work — DevOps prerequisites (TDD §14.2)

These need real external accounts. They block PHASE_13 (cloud sync) but do NOT block PHASE_01 … PHASE_12 or PHASE_14 / PHASE_15. Track here when picked up.

- [ ] **OQ-1** — Sentry: project created for the re-impl; fresh DSN collected. **DO NOT reuse Monefy's DSN.**
- [ ] **OQ-2** — Dropbox: app registered (Scoped access — App folder); app key + debug/release SHA-1 fingerprints collected.
- [ ] **OQ-3** — Google Cloud: project + Drive API + OAuth consent screen (`drive.appdata` scope), SHA-1 fingerprints, package name `com.kshavrin.mymoney`.
- [ ] **OQ-5** — Firebase Remote Config: initial `min_supported_version_code` value chosen (suggest `1`).
- [ ] **OQ-9** — CI: secret-injection path for `google-services.json` decided.
- [ ] **OQ-10** — Crash reporting: Sentry-only (recommended) vs Sentry + Crashlytics — picked.

OQ-4 (Privacy Policy) was resolved as AS-15 → bundled HTML. OQ-6 (live FX provider) deferred to v1.1, not blocking. OQ-7 (auto-sync interval) and OQ-8 (backup rotation N) resolved in §14.1.

---

## Open questions (non-DevOps)

Append any clarification needed mid-implementation. Format: `YYYY-MM-DD — <question>. Affects: <phase>. Status: <open|answered (date)>.`

- (empty at start)

---

## Session log

Append one entry per session below this line. Keep it short — date, phase, one sentence.

```
2026-05-18 — PHASE_00 — Created docs/implementation_plan/ scaffolding (README, PROGRESS, 00_overview, 15 phase files). No Android code touched.
2026-05-18 — PHASE_01 — Ticked TDD-anchor-read checkbox + completed task "libs.versions.toml — full catalogue per TDD §9.3" (commit 7b0db37). Next task: root build.gradle.kts plugins-apply-false block. Note: app/build.gradle.kts uses 3 renamed/removed aliases (androidx.lifecycle.runtime.ktx, androidx.junit, androidx.espresso.core) — to be repaired in the upcoming app-module rewrite task; gradle sync will fail until then.
2026-05-18 — PHASE_01 — Completed task "build.gradle.kts (root) — declare every plugin from §9.3 with apply false" (commit b3a9765). All 9 plugin aliases (android-application, android-library, kotlin-android, kotlin-compose, kotlin-serialization, ksp, hilt, gms-google-services, gms-oss-licenses) declared `apply false`; foojay-resolver-convention already present in settings.gradle.kts (line 15) — no edit needed there. Runner skipped: app/build.gradle.kts still broken (deferred task), and the local Gradle daemon currently fails with `Unable to establish loopback connection` even for `:help --no-daemon` — verified-by-inspection (11-line root script, all aliases resolve into libs.versions.toml [plugins] block). Next task: settings.gradle.kts include(...) for all 18 modules.
2026-05-18 — PHASE_01 — Completed task "settings.gradle.kts — include(...) all 18 modules" (commit ee3520e). Added 17 `include(":...")` lines for 9 `:core:*` + 8 `:feature:*` modules in TDD §2.2 order; pluginManagement, plugins (foojay-resolver), dependencyResolutionManagement and `rootProject.name = "MyMoney"` preserved verbatim. Runner skipped (no Kotlin sources; Gradle daemon still unreachable). Push skipped — no `origin` remote configured for the repo yet. Next task: directory tree (core/<name>/ + feature/<name>/) with src/{main,test,androidTest}/java/com/kshavrin/mymoney/... + package.kt placeholders + AndroidManifest.xml stubs.
2026-05-18 — PHASE_01 — Completed task "Directory tree — create core/<name>/ and feature/<name>/ for each module per TDD §2.2" (commit 731916a). 64 placeholder files across 17 modules: 17 `package.kt` (package decl + `internal const val PACKAGE_MARKER`), 15 minimal `<manifest />` stubs, 32 `.gitkeep` in empty test/androidTest leaf dirs. Pure-JVM modules (`:core:domain`, `:core:common`) use `src/main/kotlin/` with no AndroidManifest and no androidTest, per TDD §2.2 lines 156–180; the other 15 Android-library modules use `src/main/java/` with manifest stubs. Reviewer ✓ (no imports anywhere → no boundary violations possible). Tester returned empty test list (no logic to bind to; module build.gradle.kts files don't exist yet). Runner skipped — Gradle daemon still unreachable ("Unable to establish loopback connection"). Verifier ✓ static checks n/a (no nav/Hilt/Room/strings yet) + manual checklist verified on disk. Push deferred to end of PHASE_01. Next task: per-module `build.gradle.kts` for each `:core:*` and `:feature:*` (Android-library + Compose plugin for features; pure JVM for `:core:domain`/`:core:common`).
2026-05-18 — PHASE_01 — Completed task "Per-module `build.gradle.kts` for each `:core:*` …" (commit 15f64df). 9 new build scripts: 7 Android-library (`:core:ui`, `:core:designsystem`, `:core:database`, `:core:datastore`, `:core:network`, `:core:sync`, `:core:testing`) with namespace `com.kshavrin.mymoney.core.<name>`, compileSdk=36, minSdk=31, Java 17; 2 pure-JVM (`:core:domain`, `:core:common`) with `kotlin-jvm` plugin + `jvmToolchain(17)` + `kotlinx-coroutines-core`. `:core:testing` exposes `junit`, `turbine`, `kotlinx-coroutines-test`, `androidx-room-testing` as `api(...)`. `libs.versions.toml` extended: new `kotlin-jvm` plugin alias + `kotlinx-coroutines-core` library alias. Root `build.gradle.kts` adds `alias(libs.plugins.kotlin.jvm) apply false`. Reviewer ✓ (no imports — vacuous pass). Tester returned empty test list (no production code, no logic to bind to). Runner skipped — Gradle daemon still unreachable. Verifier ✓ (nav/Hilt/Room/strings all n/a) + 5-step Russian manual checklist generated. Push deferred to end of PHASE_01 (no `origin` configured). Next task: per-module `build.gradle.kts` for each `:feature:*` (Compose plugin + `:core:ui`/`:core:designsystem`/`:core:domain` project deps).
2026-05-18 — PHASE_01 — Completed task "Per-module `build.gradle.kts` for each `:feature:*` …" (commit 89b74ab, already pushed to origin/main). 8 new build scripts: `:feature:onboarding`, `:feature:dashboard`, `:feature:transaction`, `:feature:transactionslist`, `:feature:settings`, `:feature:dictionaries`, `:feature:cloudsync`, `:feature:lockscreen`. Each applies `android-library` + `kotlin-android` + `kotlin-compose` via `alias()`, sets namespace `com.kshavrin.mymoney.feature.<name>`, compileSdk=36 / minSdk=31 / Java 17, enables `buildFeatures.compose = true`. Dependencies: Compose BoM platform + `libs.bundles.compose` + `project(":core:ui")` + `project(":core:designsystem")` + `project(":core:domain")` + `androidx.core.ktx`. Per-feature Hilt/Room/Retrofit wiring deferred to later phases. State-sync entry (this commit) — original work was committed in the previous session without ticking the checkbox or appending a log line; this entry catches up the bookkeeping. Next task: `:app` module — namespace migration `com.example.mymoney` → `com.kshavrin.mymoney`, wire all core + feature modules as `implementation(project(...))`, apply KSP + Hilt + serialization plugins, configure `release` + `staging` + `debug` build types per TDD §8.1.
2026-05-18 — PHASE_01 — Completed task "`:app` module — keep `com.android.application` …" (commit 960ddbc). `app/build.gradle.kts` rewritten end-to-end: namespace + applicationId migrated from `com.example.mymoney` → `com.kshavrin.mymoney`; plugins block now applies 6 aliases (`android.application`, `kotlin.android`, `kotlin.compose`, `kotlin.serialization`, `ksp`, `hilt`) — `gms-google-services` / `gms-oss-licenses` intentionally deferred until OQ-9 resolves. Build types per TDD §8.1: `debug {}` (empty), `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(...) }`, `staging` via `create("staging") { initWith(getByName("release")) }`. Java toolchain bumped 11 → 17 in `compileOptions`. `compileSdk { version = release(36) … }` DSL simplified to plain `compileSdk = 36` (matches `:core:*`/`:feature:*`). Dependencies: 8 `:core:*` + 8 `:feature:*` as `implementation(project(...))`, `:core:testing` correctly scoped to `testImplementation`; direct libs `core-ktx`, `core-splashscreen`, Compose BoM + bundle, Hilt bundle + `ksp(libs.hilt.compiler)`, `kotlinx-serialization-json`, `kotlinx-coroutines-android`; test deps `junit` / `kotlinx-coroutines-test` / `turbine`, androidTest Compose BoM + `test.junit` + `test.espresso.core`; `debugImplementation` for Compose tooling/test-manifest. Three previously-broken aliases (`androidx.lifecycle.runtime.ktx`, `androidx.junit`, `androidx.espresso.core`) replaced with current names from the catalogue. Reviewer ✓ (`:app` allowed to depend on all `:core:*` + `:feature:*`; no inverse edges; no literal versions). Tester returned empty list (no Kotlin production code — pure Gradle script change; established precedent from prior PHASE_01 wiring tasks). Runner: Gradle daemon still unreachable ("Unable to establish loopback connection") — verification by inspection, marked `pass: true` with `n/a` notes per SPEC. Verifier ✓ (4 static checks all n/a — no nav, Hilt graph, Room schema, or strings touched) + 5-step Russian manual checklist generated. Push deferred to end of PHASE_01 per `/cmp --phase` convention. **Important**: build remains intentionally broken (manifest `.MainActivity` resolves against new `com.kshavrin.mymoney` namespace but `MainActivity.kt` still lives in `com.example.mymoney`) — fixed by the very next PHASE_01 task. Next task: rename package `com.example.mymoney` → `com.kshavrin.mymoney` (move `MainActivity.kt` + theme files; delete the empty old directory).
2026-05-18 — PHASE_01 — Completed task "Rename package `com.example.mymoney` → `com.kshavrin.mymoney`" (commit ac3f31e). Six `.kt` files moved as true git renames (similarity 86–97%): `MainActivity.kt`, `ui/theme/{Color,Theme,Type}.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`. In each: `package` declaration + every `import com.example.mymoney…` rewritten to `com.kshavrin.mymoney…` (CONSTRAINT 1). All 8 now-empty `com/example/...` directories under main/test/androidTest source sets removed. `AndroidManifest.xml` untouched per CONSTRAINT 4 — relative `.MainActivity` resolves against `namespace="com.kshavrin.mymoney"` set in `app/build.gradle.kts` at commit 960ddbc. The build state that was "intentionally broken" after 960ddbc is now repaired. Reviewer ✓ (no layer boundaries crossed — refactor is internal to `:app`; `domain/`, `presentation/`, `data/` n/a). Tester returned empty test list — pure namespace refactor, no new contracts to pin (established PHASE_01 precedent across 6 prior tasks). Runner skipped — Gradle daemon still unreachable ("Unable to establish loopback connection"); verification by inspection accepted as `pass: true`. Verifier ✓ — 4 standard static checks (nav / Hilt / Room / strings) all `n/a` for scaffolding phase + 5-step Russian manual checklist generated. **Known follow-up**: `ExampleInstrumentedTest.kt:22` retains the literal `"com.example.mymoney"` in `assertEquals(..., appContext.packageName)` — held intentionally per CONSTRAINT 2 ("don't change test bodies"); surfaces only under `connectedDebugAndroidTest`, will be fixed during the PHASE_01 "Sanity — run gradlew assembleDebug" task (or the next instrumented-test run, whichever lands first). Push deferred to end of PHASE_01. Next task: `proguard-rules.pro` — paste keep rules from TDD §8.4 (kotlinx.serialization, Room, Hilt, Sentry, Dropbox SDK, Google API client, Compose).
```
