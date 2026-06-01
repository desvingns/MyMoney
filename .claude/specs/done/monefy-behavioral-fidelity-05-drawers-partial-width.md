# Both navigation drawers open as partial-width panels (S02/S04)
Epic: monefy-behavioral-fidelity
Order: 05 of 09
Status: done
Depends-on: —
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Make both navigation drawers open as PARTIAL panels (~62% of screen width) with the dimmed dashboard visible behind, instead of the current near-full-width sheets (02.jpg left, 04.jpg right). Layout only.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/.../DashboardScreen.kt L106 (left ModalDrawerSheet) + L114 (right ModalDrawerSheet) — constrain each sheet's width, e.g. `ModalDrawerSheet(modifier = Modifier.width(LocalConfiguration.current.screenWidthDp.dp * 0.62f))`; screenshots 02.jpg / 04.jpg
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Constrain BOTH the left and right ModalDrawerSheet to ~0.62 of the screen width (acceptable 0.60–0.68) so the dashboard stays visibly dimmed behind, matching 02.jpg / 04.jpg. Derive from LocalConfiguration screenWidthDp — do NOT hardcode px.
  - Do NOT change drawer CONTENT or events here (left-drawer content is SPEC-06; the right-drawer content is already correct). No VM/nav changes; English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User notes #1 and #6 (the width aspect). Both `ModalDrawerSheet { … }` usages
(DashboardScreen.kt:106 and 114) have no width modifier, so on phones the drawers default to a
near-full-width sheet that covers almost the whole window. The reference shows ~60% partial panels
with the dashboard dimmed behind.

## Implementation links
- commit: 46784bfee40fdfa3a872aed8fde7c552f6b2e804
- tests: b518816d98f27691d6a922cd2ab0922ca90a6242
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
