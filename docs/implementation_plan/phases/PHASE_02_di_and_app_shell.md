# PHASE 02 — DI graph + App shell + Sentry init

## Goal

Wire Hilt across all modules, create `MyMoneyApp` (the `@HiltAndroidApp` Application class), set up `MainActivity` as the single-activity NavHost host (with an empty `Scaffold` placeholder), install the Sentry SDK init (DSN read from `BuildConfig.SENTRY_DSN`, no-op if blank). After this phase the app launches into a blank themed Scaffold; the Hilt graph compiles; Sentry can capture a test event.

## TDD anchors

- §2.1 High-level architecture — lines 109–155
- §2.4 Persistence + sync strategy — lines 229–245
- §2.5 Threading + dispatchers — lines 246–252
- §2.6 Error handling — lines 253–261
- §9.1 (Sentry sub-section) — lines 2162–2170
- §8.4 R8 / ProGuard (Hilt + Sentry keep rules) — lines 2058–2087

## Prerequisites

- PHASE_01 — done

## Deliverables

- `app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt` — `@HiltAndroidApp class MyMoneyApp : Application()` with `onCreate` that initialises Sentry via `SentryAndroid.init(this) { it.dsn = BuildConfig.SENTRY_DSN; it.tracesSampleRate = 0.0; it.attachStacktrace = true; it.enableAutoSessionTracking = true }`. No-op if DSN is blank.
- `app/src/main/java/com/kshavrin/mymoney/MainActivity.kt` — `@AndroidEntryPoint class MainActivity : ComponentActivity()` with `setContent { MyMoneyTheme { Scaffold { Box(Modifier.padding(it)) } } }`. `MyMoneyTheme` is a placeholder from `:core:ui` (still empty in PHASE_02; the real theme arrives in PHASE_03 — for now wrap `MaterialTheme { ... }`).
- `app/src/main/AndroidManifest.xml` — add `<application android:name=".MyMoneyApp" ...>`; keep `MainActivity` declaration; no deep-links yet.
- `app/build.gradle.kts` — `BuildConfig.SENTRY_DSN = providers.gradleProperty("sentry.dsn").orNull ?: ""` via `defaultConfig.buildConfigField("String", "SENTRY_DSN", "\"${...}\"")`. Enable `buildFeatures { buildConfig = true }`. Add `io.sentry:sentry-android` dep.
- `core/common/src/main/java/com/kshavrin/mymoney/core/common/di/DispatchersModule.kt` — Hilt module providing `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` qualifier-tagged `CoroutineDispatcher` injectors per TDD §2.5.
- `core/common/src/main/java/com/kshavrin/mymoney/core/common/di/Dispatcher.kt` — `@Qualifier` annotations `IoDispatcher`, `DefaultDispatcher`, `MainDispatcher`.
- `core/common/src/main/java/com/kshavrin/mymoney/core/common/result/Result.kt` — `sealed class AppResult<out T>` (Success / Error) + extension helpers. (Wrapper around `kotlin.Result` for use-site clarity.)
- `core/common/src/main/java/com/kshavrin/mymoney/core/common/exception/SyncException.kt` — `class SyncException(val syncError: SyncError) : Exception()` + `enum class SyncError { Network, Auth, Quota, Conflict, Server, Unknown }`. Per §2.6.
- `core/common/build.gradle.kts` — add Hilt KSP + `kotlinx-coroutines-core` + `javax.inject`.
- `app/build.gradle.kts` — wire Hilt via `id("dagger.hilt.android.plugin")` (already declared in PHASE_01; now applied).
- Each `:core:*` and `:feature:*` `build.gradle.kts` — add Hilt + KSP plugins for any module that will hold `@Module` or `@Inject`. At minimum: `:core:database`, `:core:datastore`, `:core:domain`, `:core:network`, `:core:sync`, every `:feature:*`. (`:core:ui`, `:core:designsystem`, `:core:common`, `:core:testing` don't need Hilt yet.)
- `app/src/main/AndroidManifest.xml` — restate that Sentry's ContentProvider auto-installs (no manual registration needed in 7.18+).
- `gradle.properties` — add `org.gradle.parallel=true`, `org.gradle.caching=true`, `kotlin.code.style=official`, `android.useAndroidX=true` if missing.

## Task checklist

- [ ] Read TDD anchors.
- [ ] Add Hilt + KSP plugins to `app/build.gradle.kts` and every module that needs DI. Each feature/core module declares `id("com.google.devtools.ksp")` and `id("com.google.dagger.hilt.android")` in its plugins block.
- [ ] Create `MyMoneyApp.kt` with `@HiltAndroidApp`. In `onCreate`, init Sentry (no-op if DSN blank).
- [ ] Wire `android:name=".MyMoneyApp"` in `AndroidManifest.xml`.
- [ ] Convert `MainActivity` to use `@AndroidEntryPoint`. Wrap content in `MaterialTheme { Scaffold { ... } }`. The screen body is a placeholder `Text("MyMoney")`.
- [ ] Add `buildConfigField("String", "SENTRY_DSN", "\"\"")` in `defaultConfig` (overridable via `gradle.properties` `sentry.dsn=...` or CI env). Confirm `BuildConfig` regenerates: `Get-ChildItem app\build\generated\source\buildConfig`.
- [ ] Build `:core:common` with Hilt qualifiers + dispatcher module. Run `:core:common:test` (empty but should pass).
- [ ] Build `:app:assembleDebug`. Confirm no Hilt-graph errors (Hilt fails early if a module mismatches).
- [ ] Install on emulator. App shows a blank Scaffold containing "MyMoney".
- [ ] Trigger a manual Sentry test event (`Sentry.captureMessage("test from PHASE_02")` from a debug-only IconButton in MainActivity), verify in your test Sentry project. If OQ-1 not yet resolved, leave DSN blank — confirm the call is silently no-op'd.
- [ ] Add `Sentry.captureException` helper to `:core:common/exception` package: a tiny `fun Throwable.reportToSentry()` extension that calls `Sentry.captureException(this)`. (Used by repositories in PHASE_06+.)
- [ ] Add `Sentry` keep-rules block to `proguard-rules.pro` (verify it's already pasted from PHASE_01).
- [ ] Update PROGRESS.md (mark phase done, set PHASE_03 active, append "Sentry DSN: blank-by-default; populate via local.properties `sentry.dsn=...` when OQ-1 is resolved").

## Done criteria

- `.\gradlew.bat :app:assembleDebug` succeeds, including Hilt processor and KSP runs.
- App launches on emulator → shows `MaterialTheme` background with the placeholder text. No crashes.
- `adb logcat -d | findstr Sentry` shows Sentry's init line (or no-op message if DSN blank).
- `:core:common:test` passes (placeholder JUnit test that asserts `IoDispatcher` and `DefaultDispatcher` qualifiers can be obtained from a Hilt `TestApplication`). If you skip the test, document why in Notes.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:common:test
.\gradlew.bat :app:installDebug
adb logcat -d "*:E AndroidRuntime:E"   # confirm no crashes on launch
```

## Notes for next session

(empty — fill at end of session)
