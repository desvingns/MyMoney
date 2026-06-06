# Dashboard header - white MyMoney title and currency subtitle in light theme
Epic: dashboard-donut-refinement
Order: 01 of 03
Status: done
Depends-on: -
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Render the dashboard top-bar "MyMoney" title and the currency-name subtitle WHITE in light theme (they are black/onSurface today) so both read on the green header gradient. They already look white in dark theme; switch both to the always-white onPrimary token.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt (top-bar title Text ~L377-384 and currency subtitle Text ~L386-391 - add `color = MaterialTheme.colorScheme.onPrimary`). onPrimary is white in both schemes (core/ui/.../theme/Color.kt) and already tints the header icons.
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Only the two header texts (title + currency subtitle). Do NOT touch period row / balance panel / FAB / icon colors.
  - Use the existing colorScheme.onPrimary token (white in light AND dark). Do NOT hardcode Color.White; do NOT add a new token.
  - Header background is the dashboardHeroGradientStart/End green gradient - onPrimary guarantees contrast in both themes.
  - Preserve test tags (DASHBOARD_TOP_BAR_TITLE_TAG / SUBTITLE_TAG), FontFamily.Cursive, weights, and merged semantics.
  - No hardcoded user-facing strings; English ids; zero comments unless a non-obvious WHY.
=== END SPEC ===

## Gap / context
In light theme, both dashboard header texts inherited onSurface and read poorly over the green header gradient. The dashboard-final-38 reference shows the title and currency subtitle as white.

## Implementation links
- commit: dd23993, 8f041db
- files: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
