# Restore top-bar period-title accessibility gate (Compose 1.8 test artifact)
Epic: review-2026-07
Order: 13c of 35
Status: backlog
Depends-on: review-2026-07-13
Date: 2026-07-14

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Re-add the accessibility gate to DashboardTopBarPeriodTitleUiTest (enableAccessibilityChecks() on the rule + >=48dp touch-target assertions on the menu / overflow / previous / next icon buttons) AND resolve the Compose-1.8 interaction that makes it fail: with enableAccessibilityChecks() enabled under composeBom 2025.04.01 (Compose/Foundation 1.8), the five DATE period-title tests (day / week range / custom range) fail assertIsDisplayed with "The component is not displayed!", even though the titles render correctly in the running app.
LAYERS: [presentation]
CHANGED_HINT: app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardTopBarPeriodTitleUiTest.kt; the AutoShrinkPeriodTitle path in feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodLabel.kt (PeriodSwitcher center title). Verify on Pixel_5_API_34.
TEST_TYPES: [compose-ui]
CONSTRAINTS: do not weaken/@Ignore the title tests; the app must keep rendering long titles correctly (verified 2026-07-14 via screenshot + uiautomator, so this is a test/framework interaction, not a user-facing bug); the fix must leave all six SPEC-13 gate classes green on device.
=== END SPEC ===

## Gap / context
Discovered while closing review-2026-07-13 (a11y automated checks). The other five gate
classes (DashboardContent, PeriodStrip, CategoryTilesList, AddExpense, MonefyKeypad) are
green on Pixel 5 API 34. DashboardTopBarPeriodTitleUiTest was the sixth: its five long
DATE titles fail `assertIsDisplayed` ("component not displayed") once
`enableAccessibilityChecks()` is on the rule under Compose 1.8 — the SHORT "All" title
passes, and the failure is identical whether the title uses the manual onTextLayout shrink
loop or Compose 1.8 `BasicText(autoSize=...)`, so it is not the title composable. The app
renders every title correctly (verified via device screenshot + uiautomator dump: top-bar
title "July" laid out at non-zero bounds). To keep `main` green, this class was reverted to
its pre-gate state when review-2026-07-13 shipped; this SPEC re-adds its gate after the
Compose-1.8 `enableAccessibilityChecks` interaction is diagnosed (candidate causes:
scope `enableAccessibilityChecks` to the interaction tests only, or a known 1.8
test-framework display-bounds quirk).

## Implementation links
- commit: (pending)
- files: (pending)
