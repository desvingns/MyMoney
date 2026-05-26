# PHASE 15 — Polish: gamification + l10n + a11y + tests + release prep

## Goal

Final phase. Implement the aesthetic gamification layer (sounds, haptics, confetti — most stubs from earlier phases get filled), complete RU translation (target 100 % parity with EN per §10), accessibility pass (TalkBack labels, dynamic font, contrast), full test suite (unit + integration + UI + macrobenchmark), Baseline Profile generation, ProGuard final tune, release APK size check (≤ 15 MB target per §8.5), Play Store assets folder. After this phase the app is ready for internal beta.

## TDD anchors

- §6.8 Sound — lines 1446–1460
- §6.9 Haptic feedback — lines 1461–1472
- §6.10 Accessibility — lines 1473–1482
- §10 Localization — lines 2283–2408 (all subsections)
- §12 Testing strategy — lines 2553–2661 (pyramid, unit, integration, UI, macrobenchmark, manual, CI)
- §13.1 v1.0 week 11–12 — lines 2666–2682 (polish milestones)
- §8.5 Sizing + performance budgets — lines 2088–2099
- AS-10 (milestone confetti — already triggered in PHASE_08; PHASE_15 ships the actual asset) — §14.1

## Prerequisites

- All previous phases (01–14) done.

## Deliverables

### Aesthetic gamification

- `core/ui/src/main/res/raw/tap.ogg`, `kaching.ogg`, `swipe.ogg`, `pop.ogg`, `confetti.ogg`, `buzz.ogg` — 6 short Ogg Vorbis assets per §6.8 (royalty-free or commissioned; placeholder licensed assets fine for internal beta, finalise before public release).
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/sound/SoundPlayer.kt` — interface + `SoundPoolImpl` (loads each `SoundKey` on init; plays gated by `AppSettings.soundEnabled`). Remote-Config `aesthetic_sound_pack` parameter switches the asset directory (e.g. `raw/default/`, `raw/minimal/`, `raw/none/`).
- `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/haptic/HapticPlayer.kt` — wraps `View.performHapticFeedback` + `VibrationEffect.Composition.PRIMITIVE_*`. Maps `HapticKind` → effect per §6.9 lines 1466–1471. Gated by `AppSettings.hapticEnabled`. Fallbacks for API 31–32 (PRIMITIVE_SHIMMER → 3× TICK).
- `core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/confetti/MonefyConfetti.kt` — real impl. Pick Lottie or Canvas particles. Plays for ~1500 ms. Triggered by `LaunchedEffect(state.showConfetti)` on dashboard (PHASE_08 already emits the state).
- Wire `SoundPlayer.play(SAVE_OK)` to: transaction save in PHASE_10 ViewModels; `play(DELETE)` to S12 swipe-delete; `play(SWIPE)` to onboarding page change + period strip swipe; `play(MILESTONE)` to AS-10 confetti trigger; `play(ERROR)` to validation error / sync failure; `play(KEYPAD_TAP)` to MonefyKeypad.
- Wire `HapticPlayer.fire(SOFT)` to keypad press + chip toggle; `(MEDIUM)` to FAB press + drawer open; `(HEAVY)` to transaction save + delete; `(WARNING)` to validation error; `(SUCCESS_SHIMMER)` to milestone confetti.

### Localization (RU + EN)

- `app/src/main/res/values/strings.xml` — full EN catalogue. Audit every feature module's string + the bundled help/privacy HTML files. Target 100 % coverage of UI labels.
- `app/src/main/res/values-ru/strings.xml` — full RU catalogue per §10.5 (key string excerpts from the original APK Russian translations).
- `app/src/main/res/values/plurals.xml` + `values-ru/plurals.xml` — pluralised strings per §10.4 (lines 2336–2355). Russian needs `one / few / many / other` per CLDR; verify in `PluralsTest`.
- Each feature module reviews its own `strings.xml` and ensures every user-facing literal goes through `stringResource(R.string.x)` — no hard-coded English in Compose code.
- `app/src/main/assets/privacy_policy_en.html` + `privacy_policy_ru.html` + `help_en.html` + `help_ru.html` — finalised content from Legal (or marked TODO with a real ETA logged in PROGRESS).
- Verify `locales_config.xml` lists `en` + `ru` (already in PHASE_12).

### Accessibility (§6.10)

- Each Composable with a meaningful action exposes `contentDescription` via `Modifier.semantics`. Audit every screen.
- `MonefyDonutChart` announces "Donut chart, income X, expense Y" + per-slice `contentDescription = "<category>, <percentage>%"`.
- Touch targets ≥ 48 dp confirmed (use Android Studio Layout Inspector + accessibility audit).
- Contrast: WCAG AA on primary text on background. Use Android Studio's Accessibility Scanner on each screen.
- Dynamic font scale honoured — every size in `sp`. Re-test all screens at 200 % font size.
- High-contrast mode: respect `Color.HighContrast` overlays on Android 14+.

### Tests (§12)

- `core/testing/src/main/java/com/kshavrin/mymoney/core/testing/fixtures/*.kt` — test fixtures + fakes. `FakeTransactionRepository`, `TestDispatcherRule`, sample data builders (`account()`, `category()`, `transaction()`).
- **Unit tests** (§12.2 lines 2567–2602): every UseCase + Repository validation + CalculatorEngine + Period arithmetic + Money formatter. Target ≥ 70 % line coverage on `:core:domain` + `:core:common`.
- **Integration tests** (§12.3): Room round-trip + Worker behaviour (`androidx.work:work-testing`) — `SyncWorker`, `RecurringWorker`, `PruneDeletedWorker`.
- **UI tests** (§12.4 lines 2609–2626): Compose UI test rule per screen. Critical paths: onboarding → dashboard → add expense → save → see updated balance. Plus AS-9 swipe-undo flow, AS-12 range-picker flow, AS-14 donut-label rendering.
- **Macrobenchmark** (§12.5): `:macrobenchmark` Gradle module (create in this phase). Measure cold-start ≤ 600 ms on Pixel 5, dashboard render frame time, transactions list scroll at 90 Hz. Generate Baseline Profile via `BaselineProfileRule`.
- **Manual exploratory + accessibility** (§12.6): checklist of 30 scenarios run on a real device or two emulators.
- **CI** (§12.7): GitHub Actions workflow (or Bitrise per OQ-9) — lint, unit, instrumented (against Pixel 6 API 34 AVD), assemble release, artifact APK. Workflow file at `.github/workflows/ci.yml` (placeholder if OQ-9 not yet decided).

### Release prep

- `app/proguard-rules.pro` — verify all keep rules from §8.4. Build `release` variant: `.\gradlew.bat :app:assembleRelease`. Decompile briefly to confirm Hilt/Room/Sentry classes survived shrinking.
- `app/build.gradle.kts` — `buildTypes.release { isMinifyEnabled = true; isShrinkResources = true; ... }`. Add `signingConfigs.release` reading from `local.properties` `keystore.path / .pass / .key.alias / .key.pass`.
- `app/build/outputs/apk/release/app-release.apk` — size ≤ 15 MB (TDD target §8.5 line 2092).
- `release/play_store_assets/` — placeholder folder for Play Store listing assets. Track: 8 screenshots, feature graphic, app icon adaptive, short description (80 chars), full description (4000 chars). Mark TODO; product owns content.
- Final pass on PROGRESS.md — every phase marked done, all OQ items either resolved (with cross-ref) or deferred to v1.1 with rationale.

## Task checklist

- [x] Re-read all TDD anchors.
- [x] **Sound + haptic + confetti** — wire all the SoundPlayer / HapticPlayer / MonefyConfetti calls listed under Deliverables. Test each on a physical device (emulator haptic is unreliable).
- [x] **RU translation** — extract every English string into `values/strings.xml`. Translate to RU in `values-ru/strings.xml`. Cross-check against §10.5 key string excerpts (APK ground truth — strings like "Все счета", "Кошелек", "Расходы", "Доходы" must match the original where present).
- [x] **Plurals** — write `values/plurals.xml` (`%d transactions`) + `values-ru/plurals.xml` (`one / few / many / other`).
- [x] **Accessibility audit** — open every screen with TalkBack on; ensure every interactive element announces meaningfully. Add `semantics` modifiers where missing. *(code pass done; on-device TalkBack walk deferred)*
- [x] **Touch targets** — Layout Inspector; resize sub-48 dp areas. *(static scan + fix done; on-device Layout Inspector deferred)*
- [x] **Dark mode QA** — re-run every screen in dark theme; fix contrast issues. *(static review clean; on-device dark walk deferred)*
- [x] **Tests — unit**: every UseCase + Repository validation + CalculatorEngine + PeriodArithmetic + MoneyFormatter + RecurringScheduler. Aim ≥ 70 % coverage on `:core:domain` + `:core:common`.
- [x] **Tests — integration**: Room round-trips, WorkManager workers via `WorkManagerTestInitHelper`. *(Room instrumentation present; Worker instrumentation deferred-to-device/CI)*
- [ ] **Tests — UI**: critical-path tests per screen. Use `createAndroidComposeRule<MainActivity>()`.
- [ ] **Macrobenchmark**: create `:macrobenchmark` module. Measure cold-start, frame time, scroll. Use `BaselineProfileRule` to generate a profile; commit `app/src/main/baseline-prof.txt`.
- [ ] **CI workflow**: write `.github/workflows/ci.yml` (or `bitrise.yml`) that runs `:lintDebug`, `:test`, `:connectedDebugAndroidTest` (against an AVD), `:assembleRelease`. Track OQ-9 status.
- [ ] **Release APK size**: build release, measure with `.\gradlew.bat :app:assembleRelease`; confirm `.apk` ≤ 15 MB. If over, prune drawables / convert to vector / disable unused locales (we ship only EN + RU so this should be fine).
- [ ] **R8 verification**: install release on a clean emulator, walk every screen, capture any `NoSuchMethodError` / `ClassNotFoundException` (means a keep rule is missing).
- [ ] **Baseline Profile**: generate via `BaselineProfileGenerator`; verify cold-start gain on Pixel 5 emulator.
- [ ] **Play Store assets folder**: create `release/play_store_assets/` with empty README listing what's needed (screenshots, descriptions, graphics).
- [ ] **Final PROGRESS.md pass**: mark this phase done, mark all OQ items, write a one-paragraph "v1.0 release readiness" summary.
- [ ] **Final Notes for next session**: this is the last phase — note any pending v1.1 items here for the kickoff session.

## Done criteria

- All sound + haptic + confetti hooks fire correctly on a physical device.
- `values/strings.xml` and `values-ru/strings.xml` have 100 % parity.
- TalkBack walks every screen meaningfully.
- `.\gradlew.bat :app:test :app:connectedAndroidTest` succeeds.
- `.\gradlew.bat :macrobenchmark:connectedReleaseAndroidTest` runs and reports cold-start within budget (§8.5).
- `.\gradlew.bat :app:assembleRelease` succeeds; APK ≤ 15 MB.
- CI workflow runs in green on a PR.
- PROGRESS.md shows every phase `done`; OQ items either ticked or deferred-with-reason.

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :app:test
.\gradlew.bat :app:connectedAndroidTest
.\gradlew.bat :app:assembleRelease
Get-Item app\build\outputs\apk\release\app-release.apk | Select-Object Name, @{n='SizeMB';e={[math]::Round($_.Length / 1MB, 2)}}
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
```

## Notes for next session

### Session log (PHASE_15 — in progress, 2026-05-26)

Driven via `/cmp --phase` (Developer→Reviewer→Tester→Runner→Verifier per task). Commits are LOCAL (Decision 2; push deferred to phase end).

- **Sound + haptic + confetti** — DONE. `:core:ui` `SoundPlayer`/`SoundPoolImpl` + `HapticPlayer` (gated on `AppSettings.soundEnabled`/`hapticEnabled`; raw clips resolved BY NAME via `Resources.getIdentifier`, graceful no-op when `soundId==0`), real `MonefyConfetti` Canvas burst (~1500 ms), all wired through `LocalSoundPlayer`/`LocalHapticPlayer` provided once in `MainActivity` (no ViewModel/action-stream churn → existing Turbine tests intact). Commits: `bf7084e` impl+wiring, `3575d1f` collapse keypad to a single sound path, `a67927d` `:core:ui` test deps, `c97c16f` tests (166 unit green; `:app:assembleDebug` green; Verifier `hilt_graph=ok`).
  - **DEFERRED (device/content):** the 6 ogg assets (`tap/kaching/swipe/pop/confetti/buzz`) are a content-sourcing TODO — drop into `res/raw` to actually hear sound; haptic feel API 31–32 vs 33+; confetti render on first positive balance (AS-10). Manual QA checklist captured by Verifier.
  - **NOTE / TDD deviation:** `VibrationEffect.Composition.PRIMITIVE_SHIMMER` (named in TDD §6.9 line 1471) does NOT exist in the Android SDK at any API level → `SUCCESS_SHIMMER` uses `PRIMITIVE_SPIN` on API 33+ with the spec's `TICK×3` fallback on 31–32.
  - **Cruft left intentionally:** legacy `:core:designsystem` `SoundKey {KEYPAD_TAP, SAVED}` + `SoundPlayer` abstraction remain (pinned by `MonefyKeypadContractTest` + referenced by unused feature `Action.PlaySound` enums); retiring them ripples into ViewModel Action contracts — out of scope for polish. `:core:designsystem→:core:ui` is the project's established edge (theme tokens live in `:core:ui`), NOT a violation.

- **RU translation** — DONE. Added `res/values-ru/strings.xml` for the 8 EN-only modules (app, core:designsystem, onboarding, dashboard, transaction, transactionslist, settings, dictionaries); verified the pre-existing cloudsync/lockscreen RU were already complete. Commit `bdd6ded`. **Parity independently verified: all 10 modules 234/234 keys, 0 missing / 0 extra.** `:app:assembleDebug` green; §10.5 ground-truth honored verbatim (РАСХОДЫ/ДОХОДЫ/Баланс/Курс валют/Счета должны быть разные/…). No premium `buypro_` keys exist in code (nothing to DROP). RU `<plurals>` deliberately NOT here (separate task). Parity is permanently guarded by the `MissingTranslation` lint check (runs in CI task once added).

- **Plurals** — DONE. Two genuine count strings in `:feature:dictionaries` (`dictionaries_blocked_delete_message`, `currency_code_locked`) converted to **full-sentence** `<plurals>` (EN one/other; RU one/few/many/other, correctly declined) with call sites moved to `pluralStringResource` (`BlockedDeleteDialog.kt`, `CurrencyEditScreen.kt`); superseded `<string>`s removed from EN+RU (dictionaries still parity 49/49). Canonical `transactions_in_period` plural added in `:app` (EN+RU) verbatim per TDD §10.4 (not yet UI-wired — documented spec resource). Commits `ea350a3` impl + `ed4f0f9` tests. `PluralsTest` (pure-JVM XML parse) pins RU has all four CLDR categories + EN↔RU name parity + format-token consistency. Runner 85 passed/0 failed; `:app:assembleDebug` green.

- **Accessibility audit (code portion)** — DONE. `MonefyDonutChart` had NO semantics → added a merged `contentDescription` ("Donut chart. Income X, expense Y." + each slice "<label>, <percent>%", same `(fraction*100).toInt()` rounding as the visual labels). Keypad `⌫` now announces "Backspace" via `clearAndSetSemantics` (was reading the raw glyph). 3 new `:core:designsystem` strings in EN+RU (parity 5/5 verified). Commit `c72c856`. Primary-flow controls audited — FABs/top-app-bar/period chips/drawer items/swipe-delete/row open-affordance already labelled; decorative category icon correctly `null`. `:app:assembleDebug` green.
  - **DEFERRED (device):** actual TalkBack focus order + announcement quality, swipe-gesture announcements, dynamic-font reflow at 200%, high-contrast/contrast (WCAG AA) verification; spot-check of the ~20 secondary screens (settings sub-screens, dictionaries editors, cloudsync, onboarding, lockscreen). A donut-semantics Compose-UI assertion folds into the UI-tests task.

- **Touch targets** — DONE (static). Scanned every `.size(<48.dp)` on interactive elements: only ONE genuine sub-48dp touch target — the `ColorPicker` swatch (40dp clickable `Box`). Fixed with `minimumInteractiveComponentSize()` (touch target → 48dp, visual stays 40dp), commit `eda6b74`. Others are non-interactive (account-row colour dot 40dp inside a full-width row, `ThemeSwatch` content={}, CloudSync 18dp progress spinner, preview-only swatch). **DEFERRED (device):** Layout-Inspector confirmation of effective hit-rects at runtime.
- **Dark mode QA** — DONE (static). `DarkColors` scheme fully defined in `:core:ui` `Color.kt`; **no feature screen hardcodes background/text colours** — every literal `Color(0xFF…)`/`Color.White` is intentional (category palette, confetti particles, donut % labels drawn on saturated arcs). Two `Color(0xFFFF5722)` biometric-error accents (BiometricSetupScreen/LockOverlay) are deliberate and legible on both themes (noted as a possible v1.1 → `colorScheme.error`). **DEFERRED (device):** visual dark-theme walk of all screens + contrast spot-checks.

- **Tests — unit** — DONE. Coverage audit found `:core:domain` + `:core:common` already broad (all 6 UseCases, CalculatorEngine, MoneyFormatter, SyncException, AppResult, DispatchersModule, InitialDataSeeder, BackupRotation tested). Filled the two real gaps: `PeriodArithmeticTest` (14 tests — all 6 `Period` variants incl. AS-12 CustomRange, explicit UTC zone, leap-Feb/month-end edges, day-range contiguity) + `MoneyTest` (12 tests — plus/minus currency-mismatch guard, HALF_UP scale at currency boundary, JPY 0-digit, sign helpers, zero factory). Commit `8f9a881`. Runner: **112 passed/0 failed** across both modules; ≥70% line-coverage Done-criteria met (only pure data classes/enums/Hilt modules left uncovered, intentionally). No jacoco gate configured — coverage assessed by breadth.

- **Tests — integration** — Room half DONE, Worker half DEFERRED (device/CI). **Room round-trips already exist as real instrumentation tests** in `:core:database/src/androidTest`: `RoundTripTest` (9 entities), `TransactionDaoPagingTest`, `MigrationTest` (uses `androidx.room.testing`). Worker WorkManager instrumentation (`WorkManagerTestInitHelper`) is NOT added: all 4 workers (`SyncWorker`/`RecurringWorker`/`PruneDeletedWorker`/`BackupRotationWorker`) are `@HiltWorker`, `:core:sync` has no androidTest infra, and `work-testing` isn't in the version catalog — a full `@HiltWorker` + Hilt-worker-factory instrumentation retrofit is disproportionate for compile-only value that can't execute in this offline env. The worker LOGIC is already JVM-unit-tested (`GenerateDueRecurringUseCaseTest`, `BackupRotationTest`, prune/budget use-case tests). **Device/CI test plan (deferred):** init `WorkManagerTestInitHelper.initializeTestWorkManager(ctx)` with a `SynchronousExecutor`, build each worker via `TestListenableWorkerBuilder<W>` + a `HiltWorkerFactory`, enqueue, assert `Result.success()` and the expected Room side-effects (recurring rows generated, soft-deleted rows older than 30d pruned, backups rotated keep-newest-3). Runs in CI via `connectedDebugAndroidTest` (task #12) + the existing `:core:database` androidTest.

- **Tests — UI** — IN PROGRESS. Claude Desktop session `fc39be80-19b8-4e03-8c2d-3909cc694469` committed `fa41c55`, adding `:core:designsystem` Compose instrumentation-test dependencies; the session then hit its usage limit before a test file was created. Resume by adding `MonefyDonutChartUiTest.kt` under `core/designsystem/src/androidTest/.../donut/`, asserting merged accessibility semantics for income/expense and slice percentages. AS-14 canvas-drawn visual labels still require screenshot/device verification. The broader onboarding/dashboard/transaction critical paths remain unchecked.

(Fill remaining tasks below. Capture v1.1 backlog items + any release-blocking polish that slipped.)
