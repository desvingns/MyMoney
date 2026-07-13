# Roborazzi screenshot regression suite for designsystem + key screens
Epic: review-2026-07
Order: 12 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add JVM screenshot regression testing with Roborazzi + Robolectric: first add @Preview-style composable entry points for the complex :core:designsystem components (MonefyDonutChart, BalanceTrendChart, Aurora balance panel, calculator keypad), then record baseline screenshots for them plus 3–4 key screen states (dashboard day view, add-expense, transactions list) in light+dark, and wire verifyRoborazziDebug as an opt-in Gradle task documented for the mp-runner "optional Roborazzi screenshot verify" hook.
LAYERS: [presentation]
CHANGED_HINT: core/designsystem (previews + tests), gradle/libs.versions.toml (roborazzi, robolectric — test-only deps), feature/dashboard test sources
TEST_TYPES: unit [screenshot]
CONSTRAINTS: test-only dependencies (production stack stays TDD-locked); baselines committed to VCS with a documented re-record command; deterministic rendering (fixed locale/fontScale/time, seeded data); JVM-based — no device or CI minutes needed
=== END SPEC ===

## Gap / context
Visual culture is manual today (hand-pulled device PNGs); Roborazzi automates it and
the /mp runner already supports the verify hook. Source: review items 12+49 (P2/M).

## Implementation links
- commits: `86b2e81f` (preview entry points + roborazzi Gradle wiring + ScreenshotTestHarness),
  `d9066241` (screenshot tests + 12 committed baselines)
- files:
  - `gradle/libs.versions.toml`, `build.gradle.kts`, `core/designsystem/build.gradle.kts`,
    `feature/dashboard/build.gradle.kts` (test-only roborazzi 1.32.2 + robolectric 4.14.1,
    plugin applied, `isIncludeAndroidResources = true`)
  - `core/designsystem/src/main/.../{donut,chart,form,keypad}/*Preview.kt` (4 @Preview entry
    points + seeded sample composables reused by the screenshot suite)
  - `core/designsystem/src/test/.../screenshot/ScreenshotTestHarness.kt` (captureThemed helper)
  - `core/designsystem/src/test/.../screenshot/DesignSystemScreenshotTest.kt` (donut, balance
    trend, keypad, add-expense amount + category — light+dark, 10 baselines)
  - `feature/dashboard/src/test/.../DashboardScreenshotTest.kt` (dashboard day-view, light+dark)
  - 12 baseline PNGs under each module's `src/test/screenshots/`
- verify / re-record: `./gradlew :<module>:verifyRoborazziDebug` (opt-in CI gate) /
  `./gradlew :<module>:recordRoborazziDebug`. Deterministic render: fixed qualifier
  `w411dp-h914dp-xxhdpi`, seeded data, `snap()` animations. JVM/off-device (no device, no CI minutes).
- notes: "Aurora balance panel" from the epic index has no dedicated component; covered instead by
  the add-expense (transaction form) screen state. `RobolectricDeviceQualifiers` is NOT in
  Robolectric 4.14.1 — pinned an explicit qualifier string instead. Deterministic mp-runner reports
  a known false-negative (kover/jacoco parse); recordRoborazzi/verifyRoborazzi exit 0 + full unit
  suites green were the verified-manual pass.
