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
```
