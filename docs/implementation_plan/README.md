# MyMoney — implementation plan

A resumable checklist that breaks the 2 850-line TDD into 15 sessions of work. Every new Claude session reads `PROGRESS.md`, opens the active phase file, executes its task list, and stops.

The authoritative spec is `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md`. This directory is the **how-to**, not the **what** — every `PHASE_NN_*.md` points back to the TDD sections that contain the source of truth.

---

## 1. Purpose

The TDD is too large to implement in one shot. Each session that tries to load the full TDD + design state into the context window runs out of budget before producing meaningful code. So we partition the work:

- **15 phases**, ~14–30 atomic tasks each.
- **One session = one phase.** Sessions don't bleed.
- **State lives on disk.** `PROGRESS.md` is the single source of truth for "where are we now".
- **Each phase has a runnable Done criterion.** A green `assembleDebug` or a passing test proves the phase landed.

---

## 2. Session protocol

Every new Claude session that picks up this project follows the same steps:

1. **Read** `C:\Pet\MyMoney\docs\implementation_plan\PROGRESS.md`. Find the row marked **active phase**.
2. **Open** the corresponding `phases\PHASE_NN_<slug>.md`.
3. **Re-read the TDD anchors** cited in that file (line ranges to `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md`). Do not read the whole TDD — the line ranges are precise on purpose.
4. **Work through the task checklist** in order, ticking `- [ ]` → `- [x]` in the phase file as items finish.
5. **Run verification commands** at the bottom of the phase file. Do not call the phase done unless they pass.
6. **Update PROGRESS.md**:
   - Move the phase row from `in progress` → `done`.
   - Set the next phase to `active`.
   - Append session date, brief outcome, any new decisions or open questions.
   - Fill out the "Notes for next session" section at the bottom of the just-finished phase file with surprises, deferred items, or required follow-up.
7. **Stop.** Do not begin the next phase in the same session even if there is context left over — handoff cleanliness matters more than session throughput.

If a phase takes more than one session: leave its status as `in progress`, expand "Notes for next session" with the resume point, then stop. The next session reads those notes first.

---

## 3. Token-budget guidance

Each phase is sized for a single ~200k-token Sonnet/Opus session including:
- Re-reading 200–400 lines of TDD anchors.
- Reading 5–20 existing files.
- Writing 10–25 new files.
- Running 2–4 gradle commands.

If a phase blows past that budget:
- **Do not** pull work forward from later phases.
- **Do** split the phase: append a `PHASE_NN_part2.md` and update PROGRESS.md so the next session resumes from where this one stopped. Use `addBlocks` semantics in the phase Prerequisites.

If a phase finishes early:
- **Do not** start the next phase. Spend the remaining budget on cleanup, documentation in that phase's "Notes" section, or doing a small refactor inside the phase's own scope.

---

## 4. Conventions (apply to every phase)

| Topic | Convention |
|---|---|
| Build system | Gradle 8.10+, Kotlin DSL (`build.gradle.kts`). No Groovy. |
| Plugins via | `libs.versions.toml` and `alias(libs.plugins.x)`. No version literals in module files. |
| Annotation processing | KSP, not kapt. Hilt + Room both use KSP. |
| Kotlin | 2.0.21 + `org.jetbrains.kotlin.plugin.compose` (Compose compiler is decoupled from Kotlin compiler). |
| Identifiers | English. Resource strings (user-facing) are EN default + RU translation per TDD §10. |
| UI | Jetpack Compose + Material 3. No XML layouts except `AndroidManifest.xml` + `res/values/*.xml`. |
| Architecture | MVVM + UDF (State / Event / Action) per TDD §2.3. ViewModels expose `StateFlow<S>`; actions are a `SharedFlow` with `replay = 0`. |
| DI | Hilt 2.52. `@Singleton` for repos and SDK wrappers, `@HiltViewModel` for ViewModels, `@Named` `CoroutineDispatcher` providers (never use `Dispatchers.IO` directly inside a class). |
| Persistence | Room 2.6.1 for transactional data; DataStore Preferences 1.1.1 for `AppSettings`; EncryptedSharedPreferences 1.1.0-alpha07 for secrets. |
| Money | `BigDecimal` in domain, `Double` in Room (TypeConverters at the boundary). |
| Time | `LocalDate` / `Instant` in domain, `Long` epoch-millis in Room. |
| Errors | Domain ops return `Result<T>`. Repository boundary catches and remaps to `SyncException(SyncError)`. ViewModels translate to `state.errorBanner` string-res. All throwables → Sentry. |
| Sentry | One init in `MyMoneyApp` via auto-installed ContentProvider. Manual breadcrumbs for sync flow. DSN comes from `BuildConfig.SENTRY_DSN` (set in CI; never committed). |
| Comments | Default to none. Only add a comment when the WHY is non-obvious. Never narrate WHAT — let names do that. |
| Tests | JUnit 4 for unit + integration; Compose UI testing for screens. KSP `room-testing` for DB tests. |
| Naming | Composables `PascalCase`, screens end in `Screen` (e.g. `DashboardScreen`); ViewModels end in `ViewModel`; UseCases end in `UseCase`; repository interfaces start with `I` are NOT used — interfaces use plain names like `TransactionRepository` and impls suffix with `Impl`. |
| Module prefix | All public types in `:core:*` are package-prefixed `com.kshavrin.mymoney.core.<name>`; in `:feature:*` they are `com.kshavrin.mymoney.feature.<name>`. The app namespace is `com.kshavrin.mymoney` (replacing the template's `com.example.mymoney`). |
| Application ID | `com.kshavrin.mymoney` (suggested per TDD OQ-3; finalise during PHASE_13). |

---

## 5. Glossary (quick lookup)

| Token | Meaning |
|---|---|
| **TDD** | `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` — the authoritative spec. |
| **Sxx** | Screen ID, e.g. `S01` = main dashboard day-period. Inventory: TDD §3.1, lines 265–298. |
| **BR-x** | Business rule, e.g. `BR-7` = "calculator: dot allowed once per operand". Full list: TDD §5, lines 1172–1207. |
| **AS-x** | Resolved decision (formerly assumption), e.g. `AS-12` = "Pick a date opens range picker". Full list: TDD §14.1, lines 2727–2750. |
| **OQ-x** | DevOps prerequisite still open, e.g. `OQ-1` = "create Sentry project, get new DSN". Full list: TDD §14.2, lines 2751–2763. Track in `PROGRESS.md` under "Deferred work". |
| **Q-Bx, Q-Cx, …** | Original Phase-0 user-answer references in the pipeline yaml files; cited inside TDD when context for a decision is needed. Don't need to read them — TDD already summarises. |
| **UDF** | Unidirectional Data Flow — State / Event / Action pattern. TDD §2.3, lines 181–228. |
| **APK** | Source-of-truth tag: extracted from base.apk. TDD §0 "Source-of-truth ranking", lines 38–47. |
| **APK-ru** | Extracted from `split_config.ru.apk` — Russian strings. |
| **(decision)** | Design choice by the analyst, not present in the original Monefy app. |
| **(assumption)** | Best-guess fill-in. All v1.0 assumptions are now resolved as AS-1…AS-15 (§14.1). |

---

## 6. Files in this directory

| File | Purpose |
|---|---|
| `README.md` | This file. Read first only if you have no idea what this is. |
| `PROGRESS.md` | **Always read first** in a new session. Tells you which phase is active. |
| `00_overview.md` | Phase map + dependency graph + TDD section index + AS cheatsheet. Read when you need to cross-reference. |
| `phases/PHASE_NN_*.md` | The 15 work units. Open the one named in PROGRESS.md. |
| `decisions.md` | Auto-created on first decision worth recording. Sticky notes about resolved blockers (e.g. "OQ-1: Sentry DSN added to local.properties as `SENTRY_DSN=...`"). |

---

## 7. What this directory is NOT

- It is **not** the spec. The spec is the TDD.
- It is **not** a Jira board. No assignees, story points, sprints. Just sequential phases.
- It is **not** a substitute for reading the TDD. Phase files give file lists and task checklists; the rules behind them live in the TDD.

If you find yourself wanting to copy spec content (entity field lists, screen layout specs, business rules) into a phase file — don't. Cite the TDD line range and re-read it on demand.
