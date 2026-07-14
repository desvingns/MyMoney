# Automated accessibility checks: ATF, touch targets, fontScale
Epic: review-2026-07
Order: 13 of 35
Status: done
Depends-on: —
Date: 2026-07-06
Completed: 2026-07-14

## SPEC
=== SPEC ===
TASK: feature
WHAT: Turn the existing Compose UI test suites into an accessibility gate: enable Accessibility Test Framework checks (enableAccessibilityChecks / AccessibilityValidator) on the main screen test classes, add explicit ≥48dp touch-target assertions (assertTouchWidthIsAtLeast/assertTouchHeightIsAtLeast) for the calculator keypad, category tiles, chips and icon buttons, and add a fontScale 1.5 + 2.0 test pass for the dashboard (Aurora balance panel 26sp/30sp fixed typography is the known truncation candidate) and the add-expense form.
LAYERS: [presentation]
CHANGED_HINT: existing *UiTest classes in app/src/androidTest and core:designsystem androidTest; density/fontScale via CompositionLocalProvider or createComposeRule density overrides
TEST_TYPES: [compose-ui]
CONSTRAINTS: device gate for instrumented runs (Pixel_5_API_34); findings that need UI changes are filed as follow-up SPECs, not silently fixed here beyond trivial paddings; never suppress an ATF violation without a written justification
=== END SPEC ===

## Gap / context
Zero automated a11y assertions today; cheap to add onto 90 existing instrumented
tests. Source: review items 15+32+33 (P3/S + P2/S + P2/S).

## Implementation links
- commits: 2236ebeb (gate test files) · d77c3daa · 564b15bb · ff6681ea · 0cf02519 (production a11y fixes; also cover 13a/13b)
- gate test files: app+designsystem `TouchTargetAssertions.kt`; `DashboardContentUiTest`, `PeriodStripUiTest`, `CategoryTilesListUiTest`, `AddExpenseScreenUiTest`, `MonefyKeypadA11yUiTest`
- verified on Pixel_5 API 34: 5 of 6 gate classes green (0 ATF violations, 48dp touch targets, fontScale 1.5/2.0). Findings surfaced and resolved: chart/scrim/drawer-row screen-reader labels, category progress-bar label, drawer touch-height (verticalScroll), period-chip 48dp width (content padding + performScrollTo for the horizontalScroll strip). Real UI defects filed + fixed as 13a/13b.
- Deferred: `DashboardTopBarPeriodTitleUiTest` a11y gate → follow-up 13c. Under Compose 1.8 (BoM 2025.04.01) enableAccessibilityChecks makes its long date-title tests report "component not displayed" while the app renders titles correctly (verified via screenshot + uiautomator). The class was reverted to its pre-gate state to keep main green; 13c re-adds the gate after the framework interaction is diagnosed.
- Note: full :app instrumented run has pre-existing reds unrelated to this work (3 stale AuroraBalanceCardUiTest "compact" assertions; 3 MainActivity*JourneyTest ComposeTimeouts — both proven pre-existing at bdbdf649; 1 operations-summary lifecycle flake).
