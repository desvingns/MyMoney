# Right-corner (⋮) drawer slides in from the right
Epic: dashboard-drawers-rework
Order: 02 of 04
Status: done
Depends-on: 01
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Make the right-corner overflow (⋮) menu drawer (Categories / Accounts / Currencies / Settings / About) slide in from the RIGHT with the dashboard dimmed on the LEFT, matching Monefy (04.jpg). After SPEC-01 it still slides from the left; this flips it to the right edge. Positioning only — no content or event changes.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardScreen.kt — change the RIGHT `DashboardDrawerOverlay(...)` call's `side = DrawerSide.Left` → `side = DrawerSide.Right`. The overlay already supports right anchoring from SPEC-01 (Alignment.CenterEnd + slideIn/Out from +width). The left drawer stays `DrawerSide.Left`.
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt — update the right-drawer test (≈L449-462) to assert the right panel is anchored to the RIGHT edge (its right bound ≈ screen width; its left bound > screen-centre) while keeping the ~0.62 width-ratio check.
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Right drawer must hug the right edge with the dimmed dashboard on the left; the left drawer is unchanged (left edge).
  - Under RTL the CenterStart/CenterEnd alignments auto-mirror — keep them; do NOT use absolute offsets.
  - No changes to RightDrawerContent, its events, width, or the dismiss behaviour (dismiss is SPEC-03). Keep test tags. English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
Point 3. Monefy's ⋮ menu (04.jpg) opens from the right; the current app opens it from the left
because Material3 `ModalNavigationDrawer` is start-anchored. With the custom overlay from SPEC-01
this is a one-argument side flip.

## Implementation links
- commit: 7672b56
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
