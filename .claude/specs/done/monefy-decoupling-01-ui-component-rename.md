# Rename Monefy* UI components to neutral names
Epic: monefy-decoupling
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-07-26

## SPEC
=== SPEC ===
TASK: feature
WHAT: Rename the `Monefy*`-prefixed UI components in `:core:designsystem` to neutral product names, updating every reference (production, previews, samples, unit tests, Compose UI tests, screenshot tests, detekt baselines). Affected identifiers and approximate reference counts: MonefyDonutChart (47, incl. MonefyDonutChartPreview/Sample/UiTest), MonefyKeypad (28, incl. Preview/Sample/ContractTest/A11yUiTest), MonefyAmountInput (17, incl. ContentTest), MonefyConfetti (9, incl. ConfettiTest/ScreenshotTest/ConfettiExists), MonefyBalanceBar (9, incl. MonefyBalancePill, MonefyBalanceBarUiTest), MonefySearchTopBar (2). Pure rename — no behaviour, no API-shape, no layout change.
LAYERS: [presentation]
CHANGED_HINT: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/{donut,keypad,amountinput,confetti,balancebar}/, core/designsystem/src/androidTest/, core/designsystem/detekt-baseline.xml, feature/* consumers
TEST_TYPES: unit compose-ui screenshot
CONSTRAINTS: Rename only — a diff that changes rendering, parameters, or default values is out of scope and must be rejected. Do NOT touch MonefyCsvImportParser's format detection or its tests (see epic overview). Do NOT touch monefy.db (SPEC 02). Screenshot baselines must be re-recorded in the same commit so Roborazzi stays green. Update core/designsystem/detekt-baseline.xml entries that reference the old names, or the baseline goes stale and hides real findings.
=== END SPEC ===

## Gap / context

MyMoney is standalone (see epic overview); these names are the last cosmetic tie to the reference
app. Mechanical but wide: ~110 references across production, previews and three kinds of test.

Risk is low but not zero — the screenshot tests and the detekt baseline both key off class names,
so a rename that ignores them turns green gates red for unrelated reasons.

## Implementation links
- commit: 3c3ce219 (`feat: rename design system components`)
- files:
  - `core/designsystem/src/main/java/.../{amountinput, balancebar, confetti, donut, keypad, pill}/` component and preview renames
  - `core/designsystem/src/{test,androidTest}/` component tests and screenshot baselines
  - `core/designsystem/detekt-baseline.xml`
  - `core/designsystem/src/main/java/.../{amountfield,form}/` consumers
  - `core/ui/src/test/.../` haptic/sound consumers
  - `feature/dashboard/.../DashboardScreen.kt`
  - `feature/transaction/.../transfer/TransferScreen.kt` and related contract test
  - `feature/transactionslist/.../SearchScreen.kt` and related content test
