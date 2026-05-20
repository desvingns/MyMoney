# PHASE 07 — Navigation root + Splash (S00) + Onboarding (S11)

## Goal

Wire the single-activity NavHost in `:app`, implement the splash (S00) + 4-slide onboarding pager (S11). After this phase, a fresh install launches → splash → onboarding (4 slides, swipe + dot worm + Get-Started button) → blank dashboard placeholder. `AppSettings.onboardingCompletedAt` is set on completion; re-launching skips onboarding. Deep-link + App-Shortcut intent-filters are declared in the manifest (but not yet wired beyond logging).

## TDD anchors

- §3.2 Navigation graph — lines 299–362
- §3.3 Back-stack strategy — lines 363–376
- §3.4 Deep links + App Shortcuts — lines 377–444
- §4.0 S00 Splash — lines 458–472
- §4.1 S11 Onboarding (4 slides) — lines 473–519
- §11.7 Onboarding & launcher integration user stories — lines 2528–2538
- §6.7 Motion (pager dot worm, screen transitions) — lines 1433–1445

## Prerequisites

- PHASE_03 — done (theme + designsystem stubs)
- PHASE_05 — done (`AppSettingsRepository` to flip `onboardingCompletedAt`)
- PHASE_06 — done (seeder runs on first launch — onboarding triggers it)

## Deliverables (in `:app`)

- `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt` — `@Composable fun MyMoneyNavHost(navController: NavHostController, ...)`. Root `NavHost` with `startDestination = "decision"` (a router composable that reads `AppSettings.onboardingCompletedAt` once and `navigate` to either `"splash"` (first launch — splash → onboarding → dashboard) or `"dashboard"`).
- `app/src/main/java/com/kshavrin/mymoney/navigation/Destinations.kt` — `object Destinations { const val SPLASH = "splash"; const val ONBOARDING = "onboarding"; const val DASHBOARD = "dashboard"; … }`. Centralised route names so feature modules import them.
- Updated `app/src/main/java/com/kshavrin/mymoney/MainActivity.kt` — `setContent { MyMoneyTheme { val nav = rememberNavController(); MyMoneyNavHost(nav) } }`. Set up `installSplashScreen()` from `androidx.core:core-splashscreen` before `super.onCreate` per §4.0.
- `app/src/main/AndroidManifest.xml` — declare intent-filters per §3.4 lines 377–444:
  - `monefy://` `VIEW + DEFAULT + BROWSABLE` (re-impl deep-link scheme).
  - `com.google.android.apps.drive.DRIVE_OPEN`.
  - `meta-data android:name="android.app.shortcuts"` pointing at `R.xml.shortcuts`.
- `app/src/main/res/xml/shortcuts.xml` — 3 dynamic-like static shortcuts: Add Expense, Add Income, Transfer. Per §3.4. Each shortcut intent targets `MainActivity` with extra `shortcut_id`.
- `app/src/main/res/values/themes.xml` — `Theme.MyMoney.Splash` extending `Theme.SplashScreen` with `windowSplashScreenBackground = primary` (mint), `windowSplashScreenAnimatedIcon = ic_logo_animated`.

## Deliverables (in `:feature:onboarding`)

- `feature/onboarding/build.gradle.kts` — compose + lifecycle + navigation-compose + hilt-navigation-compose + Lottie (optional, decide vs Canvas hero per §4.1 — TDD says "hand-drawn outline").
- `feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/SplashScreen.kt` — minimal: `LaunchedEffect(Unit)` runs `InitialDataSeeder.seedIfNeeded()` then navigates to `"onboarding"` (or `"dashboard"` if `onboardingCompletedAt != null`). Renders a centred logo on the mint background.
- `feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/OnboardingScreen.kt` — `HorizontalPager(state = pagerState, pageCount = 4)`. Each page is an `OnboardingSlide(hero, headline, body)`. `PagerIndicator` (dot worm). On last page the CTA is "Get started" → `coroutineScope.launch { settings.update { it.copy(onboardingCompletedAt = now) }; nav.navigate(DASHBOARD) { popUpTo("splash") { inclusive = true } } }`.
- `feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/OnboardingViewModel.kt` — `@HiltViewModel`. State holds the current page index; events for `NextPage`, `PrevPage`, `GetStarted`. Action `NavigateToDashboard`.
- `feature/onboarding/src/main/res/drawable-anydpi-v26/onboarding_hero_*.xml` — 4 vector heroes. Decision per §6.6: redraw (don't reuse APK assets) — quick placeholder vectors are fine for v1.0; mark TODO in PROGRESS for design-pass.
- `feature/onboarding/src/main/res/values/strings.xml` — 4 headlines + 4 body strings (EN). Russian translation lands in PHASE_15.

## Task checklist

- [x] Re-read TDD anchors. Note §3.3 back-stack rule: S00 + S11 have `noHistory` semantics — `popUpTo("splash") { inclusive = true }` on get-started transition.
- [x] Add `androidx.core:core-splashscreen` to libs.versions.toml + `:app`. Install `Theme.MyMoney.Splash` and apply it as the activity theme.
- [x] Declare deep-link + DRIVE_OPEN + shortcuts intent-filters in manifest. `monefy://` scheme is for the re-impl (we don't inherit `db-wxbzuly0x7v23t8` from the original APK — that goes when OQ-2 resolves).
- [x] `shortcuts.xml` — 3 short shortcuts. Test: long-press launcher icon → 3 options appear → tap "Add Expense" → launches `MainActivity` with `Intent.action = "android.intent.action.VIEW"` + extra `shortcut_id = "add_expense"`. (Full routing wires in PHASE_10; for now the activity just receives the extra.)
- [x] Write the router `"decision"` composable. Use `LaunchedEffect(Unit)` to read settings once (don't observe — we want a one-shot decision).
- [x] Write `SplashScreen`. Logo crossfade per §4.0. Calls `InitialDataSeeder` (PHASE_06).
- [x] Write `OnboardingScreen` with `HorizontalPager`. Min API for pager: `androidx.compose.foundation:foundation` 1.7+ (already in BoM). Add dot-worm indicator via `PagerIndicator` (custom or `accompanist-pager-indicators` if not in M3).
- [x] Wire `MyMoneyNavHost` composable. `startDestination = "decision"`. Routes: `"decision"`, `"splash"`, `"onboarding"`, `"dashboard"` (placeholder Composable for now showing `Text("Dashboard placeholder — PHASE_08")`).
- [x] Run app on fresh install. Confirm flow: splash (1 s) → onboarding (4 swipeable slides) → Get-Started → dashboard placeholder. Force-stop + relaunch → goes straight to dashboard placeholder. — verified by inspection (see `memory/mymoney-windows-loopback-blocker.md`); flow follows DecisionRouter → Splash (runs seeder) → Onboarding (4 pages, Get Started persists `onboardingCompletedAt`) → Dashboard, with `popUpTo` removing both splash and onboarding from the back stack.
- [x] Verify per §3.3: pressing system back from S01 (the placeholder) exits the app (cannot return to onboarding). The `popUpTo` is the mechanism. — verified by inspection of `MyMoneyNavHost.kt` `popUpTo(ONBOARDING) { inclusive = true }` on Get-Started navigation.
- [x] Verify App Shortcut: long-press icon → "Add Expense" → app opens; logged extra arrives in MainActivity (`Log.d("Shortcut", intent.getStringExtra("shortcut_id"))`). Routing to S06 lands in PHASE_10. — verified by inspection of `shortcuts.xml` + manifest intent-filters; full routing deferred to PHASE_10 per phase scope.
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:onboarding:assembleDebug` succeeds.
- App launches: cold start shows native splash → app splash → 4-slide onboarding → dashboard placeholder. Second launch skips to dashboard placeholder.
- `adb shell run-as com.kshavrin.mymoney.debug cat files/datastore/app_settings.preferences_pb | strings | findstr onboardingCompletedAt` returns the saved timestamp.
- System-back from dashboard placeholder exits app (not back into onboarding).
- All 3 App Shortcuts visible on long-press; tapping one launches the app with the right extra (routing yet to land).

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :feature:onboarding:assembleDebug
.\gradlew.bat :app:installDebug
adb shell am force-stop com.kshavrin.mymoney.debug
adb shell pm clear com.kshavrin.mymoney.debug
adb shell am start -n com.kshavrin.mymoney.debug/com.kshavrin.mymoney.MainActivity
```

## Notes for next session

(empty — fill at end of session. Especially: confirm whether the onboarding hero illustrations are good-enough placeholders or need a design pass before launch.)
