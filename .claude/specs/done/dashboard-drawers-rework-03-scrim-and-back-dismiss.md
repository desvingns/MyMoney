# Close the drawer by tapping the dimmed area or pressing system Back
Epic: dashboard-drawers-rework
Order: 03 of 04
Status: done
Depends-on: 01
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: While a drawer is open, tapping the semi-transparent (dimmed) dashboard area returns to the dashboard (closes the drawer), and the system BACK button does the same. Applies to BOTH the left (account/period) and right (⋮ menu) drawers.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardScreen.kt — at BOTH `DashboardDrawerOverlay(...)` call sites set `onDismiss = { onEvent(DashboardEvent.DrawerDismissed) }` (was a no-op in SPEC-01) so a scrim tap closes the drawer. Add a single `BackHandler(enabled = state.leftDrawerOpen || state.rightDrawerOpen) { onEvent(DashboardEvent.DrawerDismissed) }` inside DashboardContent (import androidx.activity.compose.BackHandler — already used in SearchScreen.kt / LockOverlay.kt). `DashboardEvent.DrawerDismissed` is already handled in the VM (≈L264-265, closes both) — no VM change needed.
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt — add a scrim-tap test (open a drawer via state, click the scrim region, assert DrawerDismissed emitted / both booleans false; the stateful helper already maps DrawerDismissed → close-both ≈L611). Add a system-back test using `createAndroidComposeRule<ComponentActivity>()` + `androidx.test.espresso.Espresso.pressBack()` (the existing `createComposeRule()` has no Activity, so a real BACK press needs an Activity-backed rule — put it in a small dedicated test class if cleaner).
TEST_TYPES: compose-ui, instrumented
CONSTRAINTS:
  - The BackHandler MUST be gated `enabled = leftDrawerOpen || rightDrawerOpen` so that with no drawer open BACK propagates normally (app exit / nav-up). Do NOT register a second OnBackPressedCallback.
  - No predictive-back progress animation is required (minSdk 31 / targetSdk 36) — the drawer simply closes.
  - The scrim sits only over the content region (below the toolbar), so a tap on the toolbar is NOT a dismiss and the toolbar stays interactive. Both affordances reuse the existing DrawerDismissed event. English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
Points 2 and 3 (the "return to dashboard" half). The VM already exposes `DrawerDismissed` but nothing
fires it, and there is no `BackHandler` on the dashboard. This wires the scrim tap and the hardware
Back to that existing event for both drawers.

## Implementation links
- commit: 11de4a5, 6d03dfc
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardDrawerBackPressUiTest.kt
