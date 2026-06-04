# Dashboard drawers open as a custom overlay below the toolbar (toolbar stays clickable)
Epic: dashboard-drawers-rework
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Replace both Material3 `ModalNavigationDrawer` instances on the dashboard with one custom in-content overlay so the top toolbar (logo, search, transfer, ⋮) stays VISIBLE and fully CLICKABLE while a drawer is open. Each drawer opens as a partial (~62% width) panel with the dashboard dimmed behind; the dimmed scrim consumes touches so the dashboard behind it is inert. For THIS spec both drawers still slide from the LEFT (right-side anchoring is SPEC-02; tap/back dismissal is SPEC-03). Reference 02.jpg / 04.jpg.
LAYERS: presentation
CHANGED_HINT:
  - NEW feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardDrawerOverlay.kt — `enum class DrawerSide { Left, Right }` + `@Composable fun DashboardDrawerOverlay(open: Boolean, side: DrawerSide, widthFraction: Float = 0.62f, onDismiss: () -> Unit, content: @Composable () -> Unit)`. Root `Box(Modifier.fillMaxSize())`: (a) scrim = `AnimatedVisibility(open, enter = fadeIn(tween(~220)), exit = fadeOut(tween(~220)))` wrapping `Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)).clickable(interactionSource = remember{…}, indication = null) { onDismiss() })` — clickable so it CONSUMES touches (blocks the dashboard behind); (b) panel = `AnimatedVisibility(open, Modifier.align(if (side==Left) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight(), enter = slideInHorizontally { if (side==Left) -it else it } + fadeIn(), exit = slideOutHorizontally { if (side==Left) -it else it } + fadeOut())` wrapping `Surface(Modifier.fillMaxWidth(widthFraction).fillMaxHeight()) { content() }`.
  - feature/dashboard/.../DashboardScreen.kt — make `Scaffold` the ROOT of DashboardContent. DELETE both `ModalNavigationDrawer` wrappers (≈L104-121 + their closing braces ≈L284-285), `rememberDrawerState` (≈L83-84), the two `LaunchedEffect(state.leftDrawerOpen/rightDrawerOpen)` sync blocks (≈L91-96) and the `drawerWidth` val (≈L89). In the Scaffold content lambda wrap the existing dashboard `Box` + two overlays in `Box(Modifier.fillMaxSize().padding(innerPadding))`: `DashboardDrawerOverlay(open = state.leftDrawerOpen, side = DrawerSide.Left, onDismiss = {}) { LeftDrawerContent(state, onEvent) }` and `DashboardDrawerOverlay(open = state.rightDrawerOpen, side = DrawerSide.Left, onDismiss = {}) { RightDrawerContent(onEvent) }`. onDismiss is a no-op here on purpose — SPEC-03 wires it. Drop the now-unused imports (ModalNavigationDrawer, ModalDrawerSheet, DrawerValue, rememberDrawerState).
  - feature/dashboard/.../DashboardViewModel.kt ≈L260-263 — enforce mutual exclusion: `LeftDrawerToggled` → `copy(leftDrawerOpen = !leftDrawerOpen, rightDrawerOpen = false)`; `RightDrawerToggled` → `copy(rightDrawerOpen = !rightDrawerOpen, leftDrawerOpen = false)`.
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt — keep the width tests (≈L434-462) green (panel is `fillMaxWidth(0.62f)`; ratio stays 0.60–0.68); update the stateful helper `setStatefulDashboardContent` (≈L609-610) to mirror mutual-exclusion (set the opposite boolean false on each toggle); add an assertion that a TopAppBar node (e.g. DASHBOARD_TOP_BAR_TITLE_TAG) is still displayed while a drawer is open.
TEST_TYPES: compose-ui, unit
CONSTRAINTS:
  - Toolbar must remain visible AND interactive with a drawer open — guaranteed by placing the overlay strictly inside the Scaffold content (below innerPadding.top). NEVER wrap the Scaffold/TopAppBar inside the overlay.
  - Preserve the ~62% width (acceptable 0.60–0.68) via `fillMaxWidth(0.62f)`; do NOT hardcode px.
  - Reuse LeftDrawerContent / RightDrawerContent verbatim — only their parent wrapper changes. Keep all existing test tags.
  - Animation MUST be a finite tween (~200–250 ms) so Compose tests reach idle — no infinite / never-settling spring.
  - Do NOT add swipe-to-open (preserve the period-swipe from monefy-behavioral-fidelity-07). The scrim consumes touches so the dimmed dashboard isn't interactive while a drawer is open. English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
Point 4 (toolbar) + the structural foundation for points 2 & 3. Material3 `ModalNavigationDrawer`
(DashboardScreen.kt ≈L104-121) is start-anchored and full-height, so it covers the toolbar and
cannot host a right-side drawer. Replacing it with a content-region overlay frees the toolbar and
unblocks SPEC-02/03/04.

## Implementation links
- commit: 9a77342, d27c80e, 1ad92d1, be2c179
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Motion.kt
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Theme.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardDrawerOverlay.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardDrawerOverlayUiTest.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
