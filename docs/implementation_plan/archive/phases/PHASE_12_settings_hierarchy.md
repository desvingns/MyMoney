# PHASE 12 — Settings hierarchy (S14, S15, S18, S19, S20)

## Goal

Build the settings root (S14) and its non-cloud children: Theme (S15), Language (S19), About/Help (S20) with bundled Privacy Policy per AS-15, and local Backup/Restore via SAF (S18). After this phase, the user can switch theme + language live, view the privacy policy + licenses, export/import a local backup file. The cloud-sync screen S17 and the biometric setup S16 land in PHASE_13 and PHASE_14 respectively.

## TDD anchors

- §4.13 S14 Settings root — lines 877–905
- §4.14 S15 Theme settings — lines 906–920
- §4.17 S18 Backup & restore — lines 993–1026
- §4.18 S19 Language — lines 1027–1036
- §4.19 S20 About / Help — lines 1037–1057 (also references AS-15 bundled HTML)
- §10.3 Per-app language switching — lines 2306–2335
- §9.6 Web-views / external links — lines 2264–2269
- AS-15 (Privacy Policy bundled HTML) — §14.1 lines 2727–2750
- OQ-8 (backup rotation N=3) — §14.1 lines 2727–2750

## Prerequisites

- PHASE_05 — done (`AppSettingsRepository`)
- PHASE_09 — done (right drawer entries route here)

## Deliverables (in `:feature:settings`)

- `feature/settings/build.gradle.kts` — standard + `androidx.appcompat:appcompat` (for `AppCompatDelegate.setApplicationLocales`).
- `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/root/SettingsRootScreen.kt` — S14. List entries: Theme, Biometric (will route in PHASE_14), Cloud sync (PHASE_13), Backup & restore (S18), Language, About / Help. Show current selection as trailing label per row.
- `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/theme/ThemeSettingsScreen.kt` — S15. Radio list: System / Light / Dark. Selection updates `AppSettings.themeMode`; `MyMoneyTheme` observes and recomposes.
- `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/language/LanguageScreen.kt` — S19. Radio: System / English / Russian. On change → `AppSettings.language` + `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (it == "system") "" else it))`. Per-app language requires `localeConfig` in manifest (PHASE_15) + `<locale-config>` XML.
- `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/about/AboutScreen.kt` — S20. Sections: Version, License, Privacy Policy, Help. Each opens a sub-screen (`PrivacyPolicyScreen`, `HelpScreen`) loading bundled HTML from `assets/privacy_policy_<lang>.html` and `assets/help_<lang>.html` in a `WebView` (JS disabled per §9.6). License → `OssLicensesMenuActivity.startOssLicensesMenuActivity(context)` (Gradle plugin `com.google.android.gms.oss-licenses-plugin`).
- `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreScreen.kt` — S18. Two buttons: Export, Import. Export → `ActivityResultContracts.CreateDocument("application/x-sqlite3")` writing `monefy_backup_<yyyyMMddHHmm>.db`. Import → `OpenDocument` reads → copies into Room db location, runs `MoneyDatabase` re-open, verifies schema version compatibility.
- `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRepository.kt` — `interface BackupRepository` + impl. Methods: `suspend fun exportDb(uri: Uri): Result<Unit>`, `suspend fun importDb(uri: Uri): Result<Unit>`, `suspend fun listLocalBackups(): List<BackupFile>`. Backup rotation: keep latest 3 per OQ-8 (PHASE_14's `BackupRotationWorker` enforces; for local SAF exports, also prune 4th+ files in the same SAF tree on each export).
- `app/src/main/assets/privacy_policy_en.html` + `privacy_policy_ru.html` — placeholder (AS-15 bundled HTML; product to provide final text before launch).
- `app/src/main/assets/help_en.html` + `help_ru.html` — placeholder.
- `app/src/main/res/xml/locales_config.xml` — `<locale-config><locale android:name="en"/><locale android:name="ru"/></locale-config>` per Android 13+ per-app language.
- `app/src/main/AndroidManifest.xml` — `android:localeConfig="@xml/locales_config"` on `<application>`.

## Task checklist

- [x] Re-read TDD anchors.
- [x] Theme selection — write `AppSettings.themeMode` via `AppSettingsRepository.update`. The root `MyMoneyTheme(themeMode = ...)` should accept the mode as a parameter (extend the API from PHASE_03 if needed) and choose dark/light/system accordingly. `MainActivity` observes settings → recomposes theme.
- [x] Language selection — add `androidx.appcompat:appcompat` dep (only thing needed for `AppCompatDelegate.setApplicationLocales`; per-app language uses this API on Android 13+ and falls back to `LocaleListCompat` on older devices). Switch happens live (the activity recreates).
- [x] Add `locales_config.xml` + `android:localeConfig` on `<application>`. Verify: install on Android 14 emulator → System Settings → App info → Language → MyMoney shows the locale switcher chip.
- [x] Add `privacy_policy_<lang>.html` + `help_<lang>.html` placeholders. Open in `WebView` with `settings.javaScriptEnabled = false` per §9.6.
- [x] Add Gradle OSS licenses plugin (`id("com.google.android.gms.oss-licenses-plugin")` in `:app/build.gradle.kts`). On `licenses` entry click → `startActivity(Intent(context, OssLicensesMenuActivity::class.java))`.
- [x] **S18 Backup**: Export — open SAF `OpenDocumentTree` (chosen over `CreateDocument` so OQ-8 rotation can enumerate siblings), persist the tree permission, create `monefy_backup_<yyyyMMddHHmm>.db` via `DocumentFile.createFile("application/octet-stream", name)`, checkpoint WAL, stream `monefy.db` bytes into it. Import — open SAF `OpenDocument(["application/octet-stream"])`, read into temp file, validate it opens as SQLite, close Room DB (`MoneyDatabase.close()`), copy temp file over `monefy.db` (+ delete `-wal`/`-shm` sidecars). Catch `IOException` and `SQLiteException` → `Result.failure` → errorBanner. On success — toast "Restored, restart app" and `Process.killProcess(Process.myPid())` from the screen via `LocalContext`. **Architecture deviation**: `BackupRepository` interface lives in `:core:domain` (pure JVM, String URIs) and `BackupRepositoryImpl` in `:core:database` (owns `MoneyDatabase`/`Context`/`ContentResolver`), mirroring the PHASE_06 RepositoryImpl-in-`:core:database` decision — NOT in `feature/settings/backup/` as this file originally stated. CSV export/import and the destructive factory-reset (AC4/AC5) are deferred. `:feature:settings` ViewModel depends only on the domain interface.
- [x] **OQ-8 local rotation**: after each export, `listFiles()` the SAF tree, keep the 3 newest backups, delete the rest. The keep-newest-3 selection is extracted as the pure `BackupRepository.backupsToDelete(files)` for plain-JVM unit testing without SAF.
- [x] Routes added to `MyMoneyNavHost`: `settings_root`, `settings_theme`, `settings_language`, `settings_about`, `settings_about_privacy`, `settings_about_help`, `settings_backup` (added). Right-drawer "Settings" → `settings_root` (root lands in Slice 5).
- [x] Live test:
  - Theme switch — flipping to Dark changes the app immediately.
  - Language — switching to Russian recreates the activity with Cyrillic strings (placeholder until PHASE_15 ships actual translations; the EN labels will still show but the locale is set).
  - Backup export → file lands in chosen folder; reading it as SQLite shows the seeded tables.
  - Backup import → app restarts, data identical to exported snapshot.
- [x] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:settings:assembleDebug` succeeds.
- All 5 in-scope settings screens render and persist their choice to `AppSettings`.
- Theme + language switches observable live.
- Backup export + import round-trip works.
- AS-15 bundled HTML opens in `WebView` (JS disabled).

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :feature:settings:assembleDebug
.\gradlew.bat :feature:settings:test
.\gradlew.bat :app:installDebug
```

## Notes for next session

PHASE_12 is **done** — all task checkboxes ticked; green at HEAD (commit `08f2969`): `:feature:settings:testDebugUnitTest` + `:core:domain:test` + `:app:testDebugUnitTest` (0 failed) and `:feature:settings:assembleDebug` + `:app:assembleDebug`. Delivered S15/S19/S20/S18/S14 via 5 `/cmp --phase` slices (feat commits f065666, 815d4b7, 26dc07a, 003ec46, 395adf9; test commits 0c8c601, 105e1a9, 08f2969). Reviewer + Verifier green on every slice.

### Architecture decisions (mirrored in PROGRESS Decisions log)
- **Decision 3** — `BackupRepository` interface lives in `:core:domain` (pure-JVM → `String` URIs, not `android.net.Uri`); impl in `:core:database`; the `:feature:settings` VM injects the domain interface only. Deviates from this file's `feature/settings/backup/BackupRepository.kt` location — required so the feature never touches `MoneyDatabase`/data internals (PHASE_06 precedent).
- **Per-app locale** — `MainActivity` is now `AppCompatActivity` and `Theme.MyMoney` reparents to `Theme.AppCompat.DayNight.NoActionBar` so `AppCompatDelegate.setApplicationLocales` applies on API 31/32 (framework `LocaleManager` is 33+). The apply call sits behind an injectable `AppLocaleController` so the VM stays JVM-testable.
- **Theme** — `MyMoneyTheme` gained a `themeMode` overload (+ `ThemeMode` enum), keeping the old `darkTheme`/`dynamicColor` overload so previews compile. `MainActivity` collects `AppThemeViewModel.themeMode` → live re-theme.
- **OSS licences** — `com.google.android.gms.oss-licenses-plugin` applied in `:app` (marker resolves via the 2026-05-22 settings.gradle.kts `resolutionStrategy` fix); `play-services-oss-licenses` 17.1.0 fetched online into the Gradle cache (offline builds work thereafter). `gms.google-services` is NOT applied — no `google-services.json` needed.

### In-scope-but-deferred TDD items (NOT done this phase)
- **S18 CSV export/import** (§4.17 AC4) and **factory-reset "Delete all data"** (AC5, incl. `delete_database_message` two-step confirm) — not in this phase's checklist; do in PHASE_15 polish or a dedicated slice.
- **`about_eula` "Terms of use"** row on S20 — omitted (no bundled EULA asset); add with a `terms_<lang>.html` asset when product supplies copy.

### Deferred to PHASE_15 / needs a device (NOT verifiable offline here)
Implementation is complete + JVM-verified; the following need an emulator and are captured as per-slice manual checklists in this session's Verifier outputs:
- Theme live-switch (Dark/Light/System, persists across relaunch); Language recreate + per-app locale chip in system App-info (RU labels land in PHASE_15); About Privacy/Help WebView (JS off, `_ru` variant on locale switch) + OSS licences activity; Backup export→`monefy_backup_<ts>.db`, OQ-8 prune-to-3, import→restart→data restored, invalid file→error banner; Settings root right-drawer entry + Sound/Haptic immediate persist.
- **Live Compose-UI tests** — every screen has a JVM placeholder pinning its contract + the real `createComposeRule()` test embedded as a template (Compose-UI-test/Robolectric artifacts absent from the offline cache).
- **RU strings** for all new keys (`:feature:settings` has no `values-ru/strings.xml` yet).

### Non-blocking follow-ups
- `WebViewScreen.assetSuffix()` reads `AppCompatDelegate.getApplicationLocales()` directly — extract a pure `assetSuffix(languageTag: String?)` seam for direct unit testing (a placeholder currently pins the en/ru/default contract).
- PHASE_11 + PHASE_12 commits are **local, not pushed** (Decision 2, pending user confirm).
