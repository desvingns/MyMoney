# Temporarily skip the onboarding tutorial on first launch (keep all code)
Epic: —
Order: —
Status: done
Depends-on: —
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: On first launch, skip the onboarding tutorial slides and land directly on the dashboard, WITHOUT removing any tutorial code. The Splash screen MUST still run (it seeds the default categories/accounts/currencies via InitialDataSeeder); only the 4-slide OnboardingScreen is bypassed. Gated by a new BuildConfig flag SHOW_ONBOARDING (default false = skip) so the tutorial can be re-enabled later by flipping the flag.
LAYERS: presentation
CHANGED_HINT:
  - app/build.gradle.kts — add `buildConfigField("boolean", "SHOW_ONBOARDING", "false")` to android.defaultConfig. buildConfig is already enabled (BuildConfig.SENTRY_DSN / VERSION_NAME exist).
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt ≈L36-44 — in the SPLASH composable's `onNavigateToOnboarding` callback, branch on `com.kshavrin.mymoney.BuildConfig.SHOW_ONBOARDING`: when false → `navController.navigate(Destinations.DASHBOARD) { popUpTo(Destinations.SPLASH) { inclusive = true } }`; when true → keep the existing navigate to ONBOARDING. Import com.kshavrin.mymoney.BuildConfig.
  - app/src/androidTest/java/com/kshavrin/mymoney/MainActivityLaunchTest.kt — `launchesToOnboardingOnFreshInMemoryState` currently asserts the onboarding "Skip" button. With SHOW_ONBOARDING=false a fresh launch must reach the dashboard instead. Update it to wait for + assert a dashboard marker (e.g. the dashboard title `com.kshavrin.mymoney.feature.dashboard.R.string.dashboard_title`, or the DASHBOARD_TOP_BAR_TITLE_TAG node) and rename accordingly (e.g. launchesToDashboardWhenOnboardingDisabled).
TEST_TYPES: instrumented
CONSTRAINTS:
  - DO NOT delete or gut any onboarding/tutorial code — SplashScreen, SplashViewModel, OnboardingScreen, OnboardingViewModel, the slide data and the onboarding strings all stay intact. This is a reversible bypass, not a removal.
  - Splash MUST keep running so InitialDataSeeder.seedIfNeeded() seeds defaults before the dashboard; only the tutorial slides are skipped. Do NOT bypass the Splash/DECISION → seeding path.
  - SHOW_ONBOARDING default = false (tutorial skipped now). Re-enabling the tutorial is intentionally OUT OF SCOPE — the user will spec it separately; DO NOT add a re-enable task to the backlog.
  - DecisionRouterViewModel is unchanged (still routes fresh state → Splash via onboardingCompletedAt == null); the bypass happens only at the Splash → next hop. English ids; at most one short WHY comment on the flag (temporary skip; tutorial re-enabled later).
=== END SPEC ===

## Gap / context
Point 1 of the user's request: the 4-slide tutorial is repetitive on every fresh install during development. Skip it for now but keep the code to re-enable at the end. Routing is `DecisionRouterViewModel` (onboardingCompletedAt) → Splash → OnboardingScreen; the Splash→Onboarding hop in MyMoneyNavHost (≈L36-44) is the seam. Splash also seeds default data (SplashViewModel.initialise → InitialDataSeeder.seedIfNeeded), so it must not be skipped — only the tutorial.

## Implementation links
- commit: 6658568 (pushed to origin/main)
- files:
  - app/build.gradle.kts (added buildConfigField SHOW_ONBOARDING=false)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt (Splash→next hop branches on BuildConfig.SHOW_ONBOARDING)
  - app/src/androidTest/java/com/kshavrin/mymoney/MainActivityLaunchTest.kt (fresh launch now asserts dashboard, onboarding bypassed)
- verification: Reviewer ✅ no layer violations; `:app:compileDebugKotlin` + `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL (JDK 17, TMP=D:\gradletmp). Verifier ✅ pass (nav/Hilt/Room/strings n/a-ok, tests_exist ok).
- notes: mp-runner-android.sh reported a FALSE pass:false (phantom :app:detekt/:app:jacoco tasks + TMP-loopback) — verified manually instead, no auto-fix run. Instrumented MainActivityLaunchTest was COMPILED but NOT executed on-device (standard Runner is JVM-only); on-device launch behavior pending manual checklist / `/mp --device`. No new unit tests (BuildConfig branch is a compile-time constant; tester documented the coverage exception).
