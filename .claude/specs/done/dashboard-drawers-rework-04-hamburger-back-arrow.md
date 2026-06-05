# Hamburger swaps to a back-arrow while a drawer is open
Epic: dashboard-drawers-rework
Order: 04 of 04
Status: done
Depends-on: 01
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: While EITHER drawer is open, the top-left hamburger (☰) icon becomes a back-arrow (←); tapping it closes the drawer and returns to the dashboard. When no drawer is open the icon is the hamburger and opens the left drawer (current behaviour, unchanged).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardScreen.kt — TopAppBar `navigationIcon` lambda (≈L131-141): compute `val drawerOpen = state.leftDrawerOpen || state.rightDrawerOpen`; Icon = `if (drawerOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu`; contentDescription = `stringResource(if (drawerOpen) R.string.dashboard_back else R.string.dashboard_menu)`; onClick → `onEvent(if (drawerOpen) DashboardEvent.DrawerDismissed else DashboardEvent.LeftDrawerToggled)` (keep the existing haptic fire). Import androidx.compose.material.icons.automirrored.filled.ArrowBack (already used in SearchScreen.kt).
  - feature/dashboard/src/main/res/values/strings.xml + feature/dashboard/src/main/res/values-ru/strings.xml — add `dashboard_back` ("Back" / "Назад").
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt — add: with a drawer open, the nav icon's contentDescription == dashboard_back and tapping it emits DrawerDismissed; with no drawer open, contentDescription == dashboard_menu and tapping emits LeftDrawerToggled (the closed case is already covered ≈L549-570 — keep it green).
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Use the AutoMirrored ArrowBack so the icon mirrors under RTL.
  - INTENTIONAL deviation from Monefy: 02.jpg keeps the hamburger while the drawer is open; the user wants a back-arrow. Document only — do not revert it during a fidelity audit.
  - Do not rename existing test tags or content descriptions (dashboard_menu stays for the closed state). English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
Point 4 (the icon-swap half). There is no back-arrow logic today; the hamburger always shows and
always toggles the left drawer. This makes the nav icon context-aware so an open drawer offers an
explicit "back to dashboard" affordance.

## Implementation links
- commit: b707318, 1237f5b
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - feature/dashboard/src/main/res/values/strings.xml
  - feature/dashboard/src/main/res/values-ru/strings.xml
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
