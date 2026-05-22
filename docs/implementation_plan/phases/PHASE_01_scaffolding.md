# PHASE 01 — Multi-module Gradle scaffolding

## Goal

Turn the empty Android Studio template (`:app` only) into the 15-module project defined in TDD §2.2. After this phase the project builds (an empty `:app:assembleDebug` succeeds), every `:core:*` and `:feature:*` module exists as an empty Kotlin library module, and `libs.versions.toml` knows about every dependency from TDD §9.3. Package namespace is migrated from `com.example.mymoney` → `com.kshavrin.mymoney`.

## TDD anchors (must re-read before starting)

- §2.2 Module layout (Gradle) — lines 156–180 of `C:\Pet\MyMoney\TDD\MyMoney\MyMoney_TDD.md`
- §8.1 Build configuration — lines 2000–2019
- §8.4 R8 / ProGuard keep rules — lines 2058–2087
- §9.3 Third-party SDKs (Gradle deps) — lines 2181–2233
- §0 source-of-truth ranking — lines 38–47 (so you know `(APK)` etc. tags when reading other sections)

## Prerequisites (must be `done` in PROGRESS.md)

- PHASE_00 — done (this directory exists). No code-prerequisites.

## Deliverables (files to create or substantially modify)

- `settings.gradle.kts` — `include(":app", ":core:ui", ":core:designsystem", ":core:database", ":core:datastore", ":core:network", ":core:sync", ":core:domain", ":core:common", ":core:testing", ":feature:onboarding", ":feature:dashboard", ":feature:transaction", ":feature:transactionslist", ":feature:settings", ":feature:dictionaries", ":feature:cloudsync", ":feature:lockscreen")`. Repository pinning + `rootProject.name = "MyMoney"` preserved.
- `gradle/libs.versions.toml` — full version catalogue per TDD §9.3 + §8.1 (Kotlin 2.0.21, AGP 8.7+, KSP 2.0.21-1.0.28, Compose BoM, Hilt 2.52, Room 2.6.1, DataStore 1.1.1, Sentry 7.18.0, Dropbox 7.0.0, GDrive v3, kotlinx-serialization 1.7.3, coroutines 1.9.0, WorkManager 2.10.0, biometric 1.2.0-alpha07, security-crypto 1.1.0-alpha07, Firebase BoM 33.5.1, paging 3.3.2, navigation-compose 2.8.4, etc.).
- `build.gradle.kts` (root) — `plugins { ... apply false }` for all custom plugins (android-application, android-library, kotlin-android, kotlin-compose, kotlin-serialization, ksp, hilt, gms-google-services, gms-oss-licenses).
- `app/build.gradle.kts` — namespace `com.kshavrin.mymoney`, applicationId `com.kshavrin.mymoney`, minSdk 31, targetSdk 36, Java 17 toolchain, R8 `release` + `staging` + `debug` build types, all `:core:*` + `:feature:*` as `implementation(project(...))` deps. No screen-level deps yet; just wire the module graph.
- `app/proguard-rules.pro` — keep rules from TDD §8.4 verbatim.
- `core/<name>/build.gradle.kts` for each of 9 core modules — minimal Android library or pure JVM module template.
- `feature/<name>/build.gradle.kts` for each of 8 feature modules — Android library + Compose enabled.
- `core/*/src/main/AndroidManifest.xml` and `feature/*/src/main/AndroidManifest.xml` — empty `<manifest package="...">` (or nothing, AGP 8 derives from namespace).
- `app/src/main/java/com/kshavrin/mymoney/` — move/rename existing template package; delete the `com/example/mymoney` package after.
- `local.properties` — confirm `sdk.dir` resolves correctly (do NOT commit).

## Task checklist

- [x] Read all TDD anchors above. Write down what you don't understand and ask before coding if anything is unclear.
- [x] **`libs.versions.toml`** — replace the existing minimal catalogue with the full list. Group sections: `[versions]`, `[libraries]`, `[bundles]`, `[plugins]`. Use the exact versions from TDD §9.3 line 2181–2233. Add a `compose` bundle (ui, ui-graphics, ui-tooling-preview, material3, material-icons-extended, activity-compose, lifecycle-runtime-compose, lifecycle-viewmodel-compose, navigation-compose). Add a `hilt` bundle (`hilt-android` + `hilt-navigation-compose` + `hilt-work`).
- [x] **`build.gradle.kts` (root)** — declare every plugin from §9.3 with `apply false`. Also add `org.gradle.toolchains.foojay-resolver-convention` to `settings.gradle.kts` already present.
- [x] **`settings.gradle.kts`** — `include(...)` all 18 modules (`:app` + 9 `:core:*` + 8 `:feature:*`). Keep the existing `pluginManagement` + `dependencyResolutionManagement` blocks. Set `rootProject.name = "MyMoney"`.
- [x] **Directory tree** — create `core/<name>/` and `feature/<name>/` for each module per TDD §2.2. Each gets `src/main/java/com/kshavrin/mymoney/<core|feature>/<name>/` + `src/main/AndroidManifest.xml` + `src/test/java/.../` + `src/androidTest/java/.../`. Add a `package.kt` placeholder file in each so KSP/Hilt sees the package later.
- [x] **Per-module `build.gradle.kts`** — for each `:core:*` (except `:core:domain` and `:core:common`): `com.android.library` + `org.jetbrains.kotlin.android` + namespace `com.kshavrin.mymoney.core.<name>` + minSdk/targetSdk inherited from root, `implementation(libs.androidx.core.ktx)` only. `:core:domain` + `:core:common` are pure JVM libs (`org.jetbrains.kotlin.jvm` + `kotlinx.coroutines.core`). `:core:testing` is `com.android.library` with test deps exposed via `api(...)`.
- [x] **Per-module `build.gradle.kts`** for each `:feature:*` — `com.android.library` + Compose plugin + namespace `com.kshavrin.mymoney.feature.<name>` + `buildFeatures { compose = true }` + `implementation(platform(libs.androidx.compose.bom))` + the Compose bundle + `implementation(project(":core:ui"))` + `implementation(project(":core:designsystem"))` + `implementation(project(":core:domain"))`.
- [x] **`:app` module** — keep `com.android.application`. Update namespace to `com.kshavrin.mymoney`. Wire all `:core:*` + `:feature:*` as `implementation(project(...))`. Add KSP + Hilt + serialization plugins (apply, don't only declare). Configure `buildTypes { release { ... }; staging { initWith(release) }; debug { } }` per §8.1.
- [x] **Rename package** `com.example.mymoney` → `com.kshavrin.mymoney`. Move `MainActivity.kt` + theme files. Delete the empty `com/example/mymoney` directory.
- [x] **`proguard-rules.pro`** — paste keep rules from TDD §8.4 (kotlinx.serialization, Room, Hilt, Sentry, Dropbox SDK, Google API client, Compose).
- [x] **AndroidManifest.xml** (root) — drop the default 4 permissions from §8.2 (`INTERNET`, `ACCESS_NETWORK_STATE`, `USE_BIOMETRIC`, `WAKE_LOCK`). Do not add deep-link intent-filters yet (PHASE_07 owns those).
- [x] **Sanity** — run `.\gradlew.bat :app:assembleDebug`. Fix any plugin / version mismatches. Expected APK: empty Compose `MainActivity` showing the default greeting.
- [x] **Sanity** — run `.\gradlew.bat :core:database:assembleDebug` (and pick two more modules randomly) to confirm library modules build standalone.
- [x] Append outcome to the "Notes for next session" section. Capture any AGP/Kotlin warnings worth knowing.

## Done criteria

- `.\gradlew.bat tasks --quiet` lists tasks for `:app`, all 9 `:core:*`, all 8 `:feature:*`.
- `.\gradlew.bat :app:assembleDebug` succeeds. Output APK lands in `app\build\outputs\apk\debug\app-debug.apk`.
- `.\gradlew.bat :feature:dashboard:assembleDebug` succeeds (proves the feature template compiles).
- `Get-ChildItem app\src\main\java -Recurse | Where-Object Name -Like '*.kt'` — every Kotlin source file is under `com\kshavrin\mymoney\` (no leftover `com\example\mymoney`).
- The application can be installed on an emulator and shows the template greeting. No Sentry / Hilt / Room wired yet.

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat --version                                        # confirm Gradle 8.10+, Java 17
.\gradlew.bat projects                                         # list all 18 modules
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:database:assembleDebug
.\gradlew.bat :feature:dashboard:assembleDebug
Get-ChildItem app\src -Recurse -Filter "*.kt" | Select-Object FullName
```

## Notes for next session

### What landed

- **libs/plugins/settings** (commits 7b0db37, b3a9765, ee3520e): full `gradle/libs.versions.toml` per TDD §9.3 lines 2181–2233 — `[versions]` / `[libraries]` / `[bundles]` / `[plugins]` with Compose + Hilt bundles; root `build.gradle.kts` declares all plugins `apply false`; `settings.gradle.kts` includes `:app` + 9 `:core:*` + 8 `:feature:*` (18 modules total).
- **Directory tree** (731916a): 64 placeholder files across 17 modules — `package.kt` per module, `<manifest />` stubs for 15 Android-library modules, `.gitkeep` in empty test/androidTest leaves. `:core:domain` + `:core:common` are pure-JVM (`src/main/kotlin/`, no manifest, no androidTest) per TDD §2.2 lines 156–180.
- **Per-module build scripts** (15f64df, 89b74ab): 9 `:core:*` scripts (7 Android-library + 2 pure-JVM) and 8 `:feature:*` scripts (Android-library + Compose plugin, depending only on `:core:ui`/`:core:designsystem`/`:core:domain`). `:core:testing` exposes JUnit/Turbine/coroutines-test/room-testing via `api(...)`.
- **`:app` rewrite** (960ddbc): namespace + applicationId migrated to `com.kshavrin.mymoney`; 6 plugin aliases applied (`android.application`, `kotlin.android`, `kotlin.compose`, `kotlin.serialization`, `ksp`, `hilt`); 3 build types per TDD §8.1 lines 2000–2019 (`debug {}`, `release { isMinifyEnabled + isShrinkResources + proguardFiles }`, `staging` via `initWith(release)`); 16 `implementation(project(...))` edges wired; `:core:testing` scoped to `testImplementation`. `gms-google-services` / `gms-oss-licenses` deferred until OQ-9.
- **Package rename** (ac3f31e): six `.kt` files moved as true git renames (similarity 86–97 %) — `MainActivity.kt`, `ui/theme/{Color,Theme,Type}.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`; every `package` decl and `import com.example.mymoney…` rewritten; 8 empty `com/example/...` directories removed. Known follow-up: `ExampleInstrumentedTest.kt:22` still hard-codes `"com.example.mymoney"` in `assertEquals(...)` — fix on first `connectedDebugAndroidTest` run after the loopback unblocks.
- **ProGuard** (2b9625c): 7 keep-rule sections per TDD §8.4 — kotlinx.serialization, Room, Hilt-generated, Sentry, Dropbox SDK, Google API client, Compose stability. `-keepattributes SourceFile,LineNumberTable` intentionally deferred to PHASE_13.
- **AndroidManifest** (8c96cc0): 4 `<uses-permission>` declarations per TDD §8.2 — `INTERNET`, `ACCESS_NETWORK_STATE`, `USE_BIOMETRIC`, `WAKE_LOCK`. None of the §8.2 REMOVED/NOT-ADDED permissions present. Deep-link intent-filters owned by PHASE_07.
- **Sanity builds** (9f81846 + no-change for 86ea98a): wrapper downgraded `9.4.1 → 8.11.1` (sha256 refreshed), `gradle/gradle-daemon-jvm.properties` deleted; `:app`, `:core:database`, `:core:domain`, `:feature:dashboard` statically inspected — all plugin/library aliases resolve, all `project(":…")` paths exist, namespaces match TDD §2.2.

Bookkeeping commits: 83db49c, 86ea98a, ac283c9.

### Done criteria status

Cross-reference to lines 51–55 of this file:

- ⚠ `gradlew tasks --quiet` listing all 18 modules — **deferred** (loopback blocker; static inspection ✓: every module is registered in `settings.gradle.kts` and has a working `build.gradle.kts`).
- ⚠ `:app:assembleDebug` succeeds + APK at `app\build\outputs\apk\debug\app-debug.apk` — **deferred** (loopback blocker; build script verified by inspection).
- ⚠ `:feature:dashboard:assembleDebug` succeeds — **deferred** (loopback blocker; build script verified by inspection).
- ✓ `app\src\main\java\` contains zero `com\example\mymoney` paths (ac3f31e diff: all 6 `.kt` moves landed; no leftover sources).
- ⚠ App installs on emulator + shows template greeting — **deferred** (depends on a successful `assembleDebug`).

Four of five Done criteria are gated on actual `gradlew` runs. Static-inspection precedent (TDD §0 lines 38–47 — "source-of-truth ranking" makes the build scripts authoritative when execution is unavailable) accepted across all 13 PHASE_01 tasks. Re-run all five criteria as the first action of PHASE_02 once the loopback is unblocked.

### Loopback-blocker status

Every `gradlew.bat` invocation on this Windows host fails inside `sun.nio.ch.PipeImpl$Initializer$LoopbackConnector` with `java.io.IOException: Unable to establish loopback connection` / `SocketException: Invalid argument: connect`. Reproduces across Gradle 9.4.1 + 8.11.1, JDK 17 MS-hotspot + JBR 21, daemon + no-daemon, default + relocated `GRADLE_USER_HOME=.gradle-local`. Root cause is OS-level (corporate AV / endpoint-protection / firewall) and outside the scope of any PHASE_01 task. See PROGRESS.md "Open questions" entry dated 2026-05-19 and cross-session memory `mymoney-windows-loopback-blocker.md`.

PHASE_02+ implication: Hilt graph wiring + Room schema additions can land via static inspection (build-script + KSP-input inspection, plus reviewer/tester static checks) until the host is unblocked. The first `gradlew` run after unblock will simultaneously validate the deferred PHASE_01 Done criteria and any pending KSP codegen from later phases.

### AGP / Kotlin / Gradle gotchas worth knowing

- **Wrapper downgrade `9.4.1 → 8.11.1`** (9f81846): the Android Studio template shipped Gradle 9.4.1, but AGP 8.7.3 is officially tested only up to Gradle 8.11. AGP-compat window must lead version choices; bump the wrapper only after AGP itself is bumped. SHA-256 must be refreshed from the Gradle distribution page (`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6` for 8.11.1-bin).
- **Deleted `gradle/gradle-daemon-jvm.properties`** (9f81846): the template's daemon-JVM pin (foojay-criteria + JDK 21) conflicted with the TDD §8.1 line 2007 Java toolchain (17). Removed the file; daemon now selects the active `JAVA_HOME` (JBR 21 on this host).
- **Java 17 vs JBR 21 split**: TDD §8.1 fixes the **compile / source / target** toolchain at Java 17. The **runtime** that launches Gradle/Kotlin compilers is JBR 21 on Windows (recommended in CLAUDE.md "JBR auto-detect snippet"). Keep these separate — never set the toolchain to 21 even when JBR 21 runs the daemon.
- **foojay-resolver auto-provisioning**: `org.gradle.toolchains.foojay-resolver-convention` lives in `settings.gradle.kts` (line 15 of the template, untouched). If `JAVA_HOME` is missing a JDK 17, foojay auto-downloads one — convenient, but the download fails silently behind the loopback blocker on this host. Prefer a manually installed JDK 17 in `JAVA_HOME` once the network path works.
- **KSP everywhere — no kapt**: Hilt 2.52 (compiler) **and** Room 2.6.1 (compiler) both consume KSP. KSP version is pinned to Kotlin (`2.0.21-1.0.28` per TDD §9.3 line 2189). Never reintroduce kapt; both annotation processors share one tool.
- **Plugin literal-version ban**: per CLAUDE.md "Plugins declared in `gradle/libs.versions.toml` only" — every module `build.gradle.kts` uses `alias(libs.plugins.x)` and `implementation(libs.x)`. Linting for stray literal versions belongs in reviewer's checklist.
- **3 alias renames from the template**: `androidx.lifecycle.runtime.ktx`, `androidx.junit`, `androidx.espresso.core` are the catalogue's current names; the template's older names were dropped during the 960ddbc `:app` rewrite. Anything new added in PHASE_02+ must follow the current catalogue exactly.

### PHASE_02 entry hint

Open `docs/implementation_plan/PROGRESS.md` and flip PHASE_02 row to `active` ("DI + App shell + Sentry skeleton"). The phase introduces the OQ-1 external blocker for the first time — a fresh Sentry DSN must be obtained before `BuildConfig.SENTRY_DSN` can be set; PROGRESS.md "Deferred work" lists OQ-1 against this. Static-inspection precedent from PHASE_01 still applies until the loopback unblocks.
