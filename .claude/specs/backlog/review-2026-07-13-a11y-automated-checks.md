# Automated accessibility checks: ATF, touch targets, fontScale
Epic: review-2026-07
Order: 13 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

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
- commit: (pending)
- files: (pending)
