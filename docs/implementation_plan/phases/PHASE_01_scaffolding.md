# PHASE 01 — Multi-module Gradle scaffolding

## Goal

Turn the empty Android Studio template (`:app` only) into the 15-module project defined in TDD §2.2. After this phase the project builds (an empty `:app:assembleDebug` succeeds), every `:core:*` and `:feature:*` module exists as an empty Kotlin library module, and `libs.versions.toml` knows about every dependency from TDD §9.3. Package namespace is migrated from `com.example.mymoney` → `com.kshavrin.mymoney`.

## TDD anchors (must re-read before starting)

- §2.2 Module layout (Gradle) — lines 156–180 of `D:\Pet\TDD_creater\MyMoney\MyMoney_TDD.md`
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
- [ ] **Rename package** `com.example.mymoney` → `com.kshavrin.mymoney`. Move `MainActivity.kt` + theme files. Delete the empty `com/example/mymoney` directory.
- [ ] **`proguard-rules.pro`** — paste keep rules from TDD §8.4 (kotlinx.serialization, Room, Hilt, Sentry, Dropbox SDK, Google API client, Compose).
- [ ] **AndroidManifest.xml** (root) — drop the default 4 permissions from §8.2 (`INTERNET`, `ACCESS_NETWORK_STATE`, `USE_BIOMETRIC`, `WAKE_LOCK`). Do not add deep-link intent-filters yet (PHASE_07 owns those).
- [ ] **Sanity** — run `.\gradlew.bat :app:assembleDebug`. Fix any plugin / version mismatches. Expected APK: empty Compose `MainActivity` showing the default greeting.
- [ ] **Sanity** — run `.\gradlew.bat :core:database:assembleDebug` (and pick two more modules randomly) to confirm library modules build standalone.
- [ ] Append outcome to the "Notes for next session" section. Capture any AGP/Kotlin warnings worth knowing.

## Done criteria

- `.\gradlew.bat tasks --quiet` lists tasks for `:app`, all 9 `:core:*`, all 8 `:feature:*`.
- `.\gradlew.bat :app:assembleDebug` succeeds. Output APK lands in `app\build\outputs\apk\debug\app-debug.apk`.
- `.\gradlew.bat :feature:dashboard:assembleDebug` succeeds (proves the feature template compiles).
- `Get-ChildItem app\src\main\java -Recurse | Where-Object Name -Like '*.kt'` — every Kotlin source file is under `com\kshavrin\mymoney\` (no leftover `com\example\mymoney`).
- The application can be installed on an emulator and shows the template greeting. No Sentry / Hilt / Room wired yet.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat --version                                        # confirm Gradle 8.10+, Java 17
.\gradlew.bat projects                                         # list all 18 modules
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:database:assembleDebug
.\gradlew.bat :feature:dashboard:assembleDebug
Get-ChildItem app\src -Recurse -Filter "*.kt" | Select-Object FullName
```

## Notes for next session

(empty — fill at end of session)
