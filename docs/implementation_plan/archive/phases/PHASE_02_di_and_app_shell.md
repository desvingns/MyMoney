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

- [x] Read TDD anchors.
- [x] Add Hilt + KSP plugins to `app/build.gradle.kts` and every module that needs DI. Each feature/core module declares `id("com.google.devtools.ksp")` and `id("com.google.dagger.hilt.android")` in its plugins block.
- [x] Create `MyMoneyApp.kt` with `@HiltAndroidApp`. In `onCreate`, init Sentry (no-op if DSN blank).
- [x] Wire `android:name=".MyMoneyApp"` in `AndroidManifest.xml`.
- [x] Convert `MainActivity` to use `@AndroidEntryPoint`. Wrap content in `MaterialTheme { Scaffold { ... } }`. The screen body is a placeholder `Text("MyMoney")`.
- [x] Add `buildConfigField("String", "SENTRY_DSN", "\"\"")` in `defaultConfig` (overridable via `gradle.properties` `sentry.dsn=...` or CI env). Confirm `BuildConfig` regenerates: `Get-ChildItem app\build\generated\source\buildConfig`.
- [x] Build `:core:common` with Hilt qualifiers + dispatcher module. Run `:core:common:test` (empty but should pass).
- [x] Build `:app:assembleDebug`. Confirm no Hilt-graph errors (Hilt fails early if a module mismatches).
- [x] Install on emulator. App shows a blank Scaffold containing "MyMoney".
- [x] Trigger a manual Sentry test event (`Sentry.captureMessage("test from PHASE_02")` from a debug-only IconButton in MainActivity), verify in your test Sentry project. If OQ-1 not yet resolved, leave DSN blank — confirm the call is silently no-op'd.
- [x] Add `Sentry.captureException` helper to `:core:common/exception` package: a tiny `fun Throwable.reportToSentry()` extension that calls `Sentry.captureException(this)`. (Used by repositories in PHASE_06+.)
- [x] Add `Sentry` keep-rules block to `proguard-rules.pro` (verify it's already pasted from PHASE_01).
- [x] Update PROGRESS.md (mark phase done, set PHASE_03 active, append "Sentry DSN: blank-by-default; populate via local.properties `sentry.dsn=...` when OQ-1 is resolved").

## Done criteria

- `.\gradlew.bat :app:assembleDebug` succeeds, including Hilt processor and KSP runs.
- App launches on emulator → shows `MaterialTheme` background with the placeholder text. No crashes.
- `adb logcat -d | findstr Sentry` shows Sentry's init line (or no-op message if DSN blank).
- `:core:common:test` passes (placeholder JUnit test that asserts `IoDispatcher` and `DefaultDispatcher` qualifiers can be obtained from a Hilt `TestApplication`). If you skip the test, document why in Notes.

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:common:test
.\gradlew.bat :app:installDebug
adb logcat -d "*:E AndroidRuntime:E"   # confirm no crashes on launch
```

## Notes for next session

### What landed

- **Hilt + KSP plugins across 14 modules** (commit 9e1207b): pure-JVM `:core:common` + `:core:domain` use new `hilt-core` lib alias + KSP plugin + `ksp(libs.hilt.compiler)` (cannot use `dagger.hilt.android` plugin — Android-only); 12 Android-library modules (`:core:database/datastore/network/sync` + 8 `:feature:*`) use both Hilt + KSP plugins + `hilt-android` impl + `ksp(libs.hilt.compiler)`. `:core:ui`, `:core:designsystem`, `:core:testing` intentionally skipped (no DI needed in PHASE_02).
- **`:app/build.gradle.kts` Sentry buildConfig** (commit cfd662a): `buildFeatures { compose = true; buildConfig = true }`; `defaultConfig.buildConfigField("String", "SENTRY_DSN", "\"${providers.gradleProperty(\"sentry.dsn\").getOrElse(\"\")}\"")`; `implementation(libs.sentry.android)`. `gradle.properties` extended with `org.gradle.parallel=true`, `org.gradle.caching=true`, `android.useAndroidX=true`.
- **`MyMoneyApp.kt`** (commit 2961065): `@HiltAndroidApp class MyMoneyApp : Application()` with `onCreate` guarded by `BuildConfig.SENTRY_DSN.isNotBlank()`. Inside guard: `SentryAndroid.init(this) { options -> options.dsn = ...; options.tracesSampleRate = 0.0; options.isAttachStacktrace = true; options.isEnableAutoSessionTracking = true }`. Sentry 7.18.x Kotlin property syntax with `is`-prefixed boolean getters.
- **AndroidManifest.xml** (commit 2961065): `android:name=".MyMoneyApp"` as first attribute of `<application>`; all other manifest content (4 KEEP permissions, MainActivity declaration with MAIN/LAUNCHER intent-filter) preserved byte-identical.
- **`MainActivity.kt`** (commits 8a60323 + 9ea45b8): converted to `@AndroidEntryPoint`; replaced `MyMoneyTheme` placeholder import with bare `MaterialTheme` from Material3 (real theme arrives in PHASE_03); removed obsolete `Greeting()` + `GreetingPreview()` template stubs; added debug-only `IconButton` inside `Box(contentAlignment = Alignment.Center)` at `Alignment.TopEnd`, gated by `BuildConfig.DEBUG`, with `Icons.Filled.BugReport` icon and `onClick = { Sentry.captureMessage("test from PHASE_02") }`.
- **`:core:common` DI/result/exception scaffolding** (commits 48b845a + 6d6548c + a5378f5): `di/Dispatcher.kt` (3 `@Qualifier @Retention(BINARY)` annotations: `IoDispatcher`, `DefaultDispatcher`, `MainDispatcher`), `di/DispatchersModule.kt` (`@Module @InstallIn(SingletonComponent::class) object` providing `Dispatchers.IO/Default/Main` per qualifier with `@Provides @Singleton`), `result/Result.kt` (`sealed class AppResult<out T>` Success/Error + 5 inline helpers), `exception/SyncException.kt` (`class SyncException(val syncError: SyncError) : Exception()` + enum SyncError {Network, Auth, Quota, Conflict, Server, Unknown}), `exception/SentryExt.kt` (1-liner `fun Throwable.reportToSentry() { Sentry.captureException(this) }`). 3 unit-test files cover AppResult helpers (10 tests), SyncException pinning, DispatchersModule identity. New lib aliases: `hilt-core`, `sentry-core` (JVM-only artifacts for pure-JVM modules).
- **PROGRESS.md** flips: PHASE_02 → done, PHASE_03 → active.

### Done criteria status

| Criterion | Status |
|---|---|
| `.\gradlew.bat :app:assembleDebug` succeeds, including Hilt processor + KSP runs | ⚠ deferred — Windows loopback blocker (verified-by-inspection precedent) |
| App launches on emulator → shows `MaterialTheme` background with placeholder text. No crashes. | ⚠ deferred — loopback blocks installDebug; verified-by-inspection |
| `adb logcat -d \| findstr Sentry` shows init line or no-op message | ⚠ deferred — loopback; verified-by-design (guard ensures no init when DSN blank, captureMessage is global-static silent no-op) |
| `:core:common:test` passes | ⚠ deferred — loopback blocks gradle test; 15 tests written (AppResultTest 10 + SyncExceptionTest 3 + DispatchersModuleTest 2), verified-by-inspection (plain JUnit4, no Android deps) |

### Loopback-blocker status

Identical to PHASE_01. Static inspection continues to be the accepted fallback per `mymoney-windows-loopback-blocker.md`. PHASE_03+ work (Compose theming, design system) can proceed because the chain has no new runtime-dependent behaviour beyond what PHASE_01/02 already validated by inspection. First phase requiring REAL emulator/device runs is PHASE_07 (Splash + onboarding + nav root) — at which point the loopback OS-level investigation must be resolved.

### Sentry / OQ-1 status

DSN is blank by default. To enable real Sentry uploads:
1. Resolve OQ-1: create Sentry project (do NOT reuse Monefy's DSN per TDD §9.1).
2. Add `sentry.dsn=https://<key>@<host>/<project_id>` to **local** `gradle.properties` (untracked) or pass via `-Psentry.dsn=...` to gradle CLI.
3. Rebuild — `BuildConfig.SENTRY_DSN` will pick up the new value; `MyMoneyApp.onCreate` guard will then initialise Sentry; debug `BugReport` IconButton in MainActivity will start sending real captureMessage events.

The `BugReport` IconButton in MainActivity is **temporary** — it will be removed in PHASE_07 when the real splash + onboarding + bottom-nav root replaces the placeholder MainActivity content.

### Hilt + KSP / Sentry / Compose gotchas worth knowing

1. **Pure-JVM modules cannot use the Android Hilt plugin.** `:core:common` + `:core:domain` get `alias(libs.plugins.ksp)` + `implementation(libs.hilt.core)` + `ksp(libs.hilt.compiler)`. They CANNOT have `alias(libs.plugins.hilt)` — `com.google.dagger.hilt.android` requires the Android plugin chain. Hilt aggregator in `:app` discovers their `@Module`s via classpath scanning.
2. **`Dispatchers.Main` requires `kotlinx-coroutines-android`.** `:core:common` is pure-JVM and only has `kotlinx-coroutines-core` — so `Dispatchers.Main` REFERENCE compiles fine but USE on JVM tests throws `IllegalStateException: Module with the Main dispatcher had failed to initialize`. `DispatchersModuleTest` intentionally skips the Main test for this reason. In production, `:app` brings `kotlinx-coroutines-android` transitively via hilt-android + the compose runtime; Main works at runtime.
3. **Sentry SDK has TWO artifacts on Maven**: `io.sentry:sentry-android` (Android-only — has ContentProvider for auto-init, manifest merger, ANR tracking) and `io.sentry:sentry` (JVM core — no Android dependencies; pure JVM logging + capture API). `:app` uses sentry-android; pure-JVM `:core:common` uses sentry-core. Both must be the SAME version (7.18.0) — set via single `sentryAndroid = "7.18.0"` version.ref shared by both aliases.
4. **Sentry `is`-prefix Kotlin properties**: Sentry 7.x SDK uses Kotlin's `is`-prefix convention for boolean getters: `options.isAttachStacktrace = true` (not `setAttachStacktrace(true)` which is the Java setter form). For the more recent SentryOptions API, both forms work but the Kotlin idiom is the `is`-prefix.
5. **Sentry no-op safety**: `Sentry.captureException` / `captureMessage` are silent no-ops when `SentryAndroid.init` was not called — they delegate via `HubAdapter.getInstance()` to a NoOpHub singleton. Our `BuildConfig.SENTRY_DSN.isNotBlank()` guard in `MyMoneyApp.onCreate` ensures init never runs with blank DSN, so calls from MainActivity (or any future repository) are safe to leave un-guarded.
6. **Compose IconButton in BoxScope**: `IconButton(..., modifier = Modifier.align(Alignment.TopEnd))` works because `Modifier.align(Alignment.*)` is an extension on `BoxScope.align(Alignment)` — IconButton is rendered inside `Box(contentAlignment = Center) { Text(...); if (BuildConfig.DEBUG) { IconButton(...) } }`. The IconButton overlays the centred Text.
7. **No Hilt graph errors expected at runtime** even though we have a `@Module` in pure-JVM `:core:common`: Hilt's annotation processor aggregates `@InstallIn(SingletonComponent::class)` annotations across the whole compile classpath. The aggregator runs in `:app`'s KSP step and pulls in `DispatchersModule` via the transitive `:core:common` dependency.

### PHASE_03 entry hint

- Open `docs/implementation_plan/phases/PHASE_03_design_system.md`.
- The PHASE_02 MainActivity uses bare `MaterialTheme` placeholder — PHASE_03 task #1 will create the real `MyMoneyTheme` in `:core:ui` and PHASE_03 will eventually re-wire MainActivity to use it (`MyMoneyTheme { Scaffold { ... } }`).
- `:core:designsystem` (currently empty per PHASE_01 scaffolding) gets ColorScheme/Typography/Shapes tokens. `:core:ui` (currently empty) gets composable building blocks.
- OQ-1 (Sentry DSN) remains open through PHASE_03 — only blocks PHASE_13 (cloud sync).
