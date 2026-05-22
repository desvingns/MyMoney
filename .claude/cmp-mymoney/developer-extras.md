# cmp-developer-android — MyMoney extras

Read this **after** `.claude/agents/cmp-developer-android.md`. These rules are MyMoney-specific and override CMP defaults where they conflict.

## Source of truth

- **Authoritative spec**: `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md` (RU, 2 850 lines).
- **Cite TDD line ranges**, never paraphrase. Format: `TDD §6.2, lines 1234–1256`.
- **Phase plan**: `docs/implementation_plan/phases/PHASE_NN_*.md` enumerates what to do; TDD enumerates *how it must look*.
- If asked to implement something that contradicts the TDD, **stop and ask** before deviating. Don't silently "fix" Monefy v1.0 quirks — see AS-12 and AS-14 below.

## Locked decisions you must respect

AS-1…AS-15 are resolved decisions in TDD §14.1 (lines 2727–2750). Two are intentional deviations from Monefy v1.0 — **do not "fix" them back**:

- **AS-12**: "Pick a date" opens a **two-date range picker**, not a single-day picker. Mode = `CustomRange(start, end)`.
- **AS-14**: Donut chart shows percentage labels on slices **≥3 %** (Monefy is ≥5 %).

Other AS to keep in mind during implementation:
- **AS-5**: Biometric lock is a Composable **overlay over the NavHost**, not a separate navigation destination.
- **AS-6**: S03 Transfer auto-navigates to S27 Currency Rate if currencies mismatch and no `CurrencyRate` exists.
- **AS-7**: Transfer stored as **a single TransactionEntity row** (Approach B), not two linked rows.
- **AS-9**: Swipe-delete on S12 → Snackbar with UNDO (5-second window). Soft-delete in DB, hard-delete via worker after 30 days.
- **AS-11**: Recurring templates auto-create silently (no badge, toast, bottom-sheet).
- **AS-13**: Delete/archive account is blocked if the account has non-deleted transactions (no cascade).

## Package & module conventions

- **Application ID**: `com.kshavrin.mymoney`. The Android Studio template's `com.example.mymoney` must be migrated in PHASE_01 — never use it in new code.
- **`:core:*` packages**: `com.kshavrin.mymoney.core.<name>`.
- **`:feature:*` packages**: `com.kshavrin.mymoney.feature.<name>`.
- **Dependency rule**: `:feature:*` may depend on `:core:*` and `:core:domain`. **Never** `:feature:*` → `:feature:*`.

## Tech stack — locked by TDD §2.1 + §8

When generating Gradle blocks, use these versions (centralised in `gradle/libs.versions.toml`, referenced via `alias(libs.plugins.x)` / `implementation(libs.x)` — no version literals in module `build.gradle.kts`):

- Kotlin 2.0.21 + `org.jetbrains.kotlin.plugin.compose` (Compose compiler decoupled from Kotlin)
- AGP 8.7+; Gradle 8.10+
- KSP matching Kotlin — used for both Room and Hilt (no kapt anywhere)
- Compose BoM 2024.10+ with Material 3
- Hilt 2.52 + hilt-navigation-compose
- Room 2.6.1, DataStore Preferences 1.1.1, EncryptedSharedPreferences 1.1.0-alpha07
- androidx.navigation-compose 2.8.4
- kotlinx-coroutines 1.9, kotlinx.serialization 1.7
- WorkManager 2.10
- Retrofit 2.11 / OkHttp 4.12 (only where network is needed — most sync uses platform SDKs)
- `minSdk: 31`, `targetSdk: 36`, `compileSdk: 36`, JVM target = 21 (JBR recommended on Windows)

## MVVM + UDF pattern (TDD §2.3, lines 181–228)

- ViewModels expose `StateFlow<UiState>` with an immutable data class.
- One-shot events: `SharedFlow<Action>` with `replay = 0` (navigation, snackbar, dialog).
- Events from UI = method calls on the ViewModel.
- Don't put business logic in Composables — keep them dumb.

## Money & time conventions

| Concern | Convention |
|---|---|
| Money in domain | `BigDecimal`. Never `Double` outside Room. |
| Money in Room | `Double`. Convert at the TypeConverter / DAO boundary only. |
| Time in domain | `LocalDate` (calendar-day) or `Instant` (events). |
| Time in Room | `Long` epoch-millis via TypeConverter. |

## Errors & observability

- Domain operations return `kotlin.Result<T>`.
- Repository implementations catch and remap to `SyncException(SyncError)` for the sync flow; other exceptions bubble through `Result.failure`.
- ViewModels translate failures to `state.errorBanner = R.string.…`.
- All throwables → Sentry. DSN comes from `BuildConfig.SENTRY_DSN` (set in CI; never committed). One init in `MyMoneyApp` via auto-installed ContentProvider.

## Hilt DI conventions

- `@Singleton` for repositories and SDK wrappers.
- `@HiltViewModel` for ViewModels.
- **Never** use `Dispatchers.IO` directly inside a class. Inject `@Named("io") CoroutineDispatcher` (and `@Named("default")`, `@Named("main")` as needed) via a `DispatchersModule`.
- DI modules belong in `:app/di/` if app-scoped, or in the module they bind for that's `@InstallIn(SingletonComponent::class)`.

## Strings & localization

- User-facing strings live in `res/values/strings.xml` (English, default) and `res/values-ru/strings.xml` (Russian translation per TDD §10).
- Never hardcode user-facing strings in Kotlin. Always go through `stringResource(R.string.…)` or `context.getString(R.string.…)`.
- Identifiers (variables, functions, classes, files) are English-only.

## Comments policy

Default to **zero comments**. Only add when the WHY is non-obvious — a hidden constraint, a subtle invariant, a workaround for a specific bug, behaviour that would surprise a reader. **Never narrate WHAT** — let names do that. Don't reference the current PR/task/issue. One short line max; no multi-paragraph docstrings.

## Phase awareness

When invoked via `/cmp --phase`, the SPEC will include `CHANGED_HINT: docs/implementation_plan/phases/PHASE_NN_*.md`. **Open that phase file before any code changes** — it contains the task checklist, TDD anchor lines, file lists, and any phase-specific notes. The phase file is more specific than this extras file; respect it.

When invoked via `/cmp --feature` directly (not through `--phase`), there is no phase file in the SPEC. Still read `docs/implementation_plan/PROGRESS.md` first to know which phase is active — it constrains what the work in scope.

## Build & test commands

```bash
./gradlew :app:assembleDebug                   # compile check
./gradlew :app:kspDebugKotlin                  # rerun KSP after Room/Hilt annotation changes
./gradlew :app:testDebugUnitTest               # unit tests
./gradlew :app:connectedDebugAndroidTest       # instrumentation
./gradlew :core:database:testDebugUnitTest     # scoped to one module — fast iteration
```

JBR auto-detect snippet for Git Bash on Windows lives in `CLAUDE.md`.
