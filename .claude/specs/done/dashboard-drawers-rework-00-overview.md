# Dashboard side-drawer rework — epic overview
Epic: dashboard-drawers-rework
Order: 00 of 04
Status: draft
Depends-on: —
Date: 2026-06-04

## Goal
Rework the dashboard's two side drawers and toolbar to the intended Monefy-like interaction model
(points 2–4 of the user's request). Today both drawers are Material3 `ModalNavigationDrawer`, which
is start-anchored and full-height — so the right-corner (⋮) menu wrongly slides from the LEFT and
both drawers cover the toolbar. Material3's component cannot anchor to the right or sit below the
bar, so this epic replaces both with a single custom in-content overlay (`DashboardDrawerOverlay`)
and layers the dismiss + icon behaviours on top.

Reference (Monefy): left account/period drawer = `TDD/MyMoney/input/screenshots/02.jpg`; right
menu drawer (slides from the RIGHT) = `04.jpg`. Drawer width is already ~62% (prior epic SPEC
`monefy-behavioral-fidelity-05`, done) and must be preserved.

## Ordered SPECs
| # | File | Slice (user point) | Layers | Depends-on |
|---|------|--------------------|--------|-----------|
| 01 | `-01-toolbar-visible-overlay.md` | Toolbar stays visible/clickable; both drawers → custom partial overlay below the bar; scrim consumes touches; VM mutual-exclusion (point 4 — toolbar) | presentation | — |
| 02 | `-02-right-drawer-from-right.md` | Right (⋮) drawer slides in from the RIGHT, dashboard dimmed on the left (point 3 — side) | presentation | 01 |
| 03 | `-03-scrim-and-back-dismiss.md` | Tap on the dimmed area closes the drawer; system BACK closes the drawer — both drawers (points 2 & 3 — dismiss) | presentation | 01 |
| 04 | `-04-hamburger-back-arrow.md` | While a drawer is open the top-left ☰ becomes a back-arrow that closes it (point 4 — arrow) | presentation | 01 |

Recommended implementation order: **01 → 02 → 03 → 04**. 02/03/04 are mutually independent; all build on the custom overlay from 01.

## Locked decisions
- The custom `DashboardDrawerOverlay` (scrim + animated sliding panel, `side = Left/Right`) REPLACES
  both Material3 `ModalNavigationDrawer` usages. The old `rememberDrawerState` + the two sync
  `LaunchedEffect`s are removed; the overlay reads `leftDrawerOpen` / `rightDrawerOpen` directly.
- Toolbar stays clickable because the overlay renders INSIDE the Scaffold content lambda (below
  `innerPadding.top`); the TopAppBar is a separate Scaffold slot and is never covered.
- At most one drawer open at a time — enforced in the ViewModel (opening one closes the other).
- The ☰→back-arrow swap (SPEC-04) is an **INTENTIONAL deviation** from Monefy (02.jpg keeps the
  hamburger while the drawer is open). User-requested; do not "fix" it back during a fidelity audit.
- Right-from-right (02), toolbar-visible (01) and tap-outside/back-to-dismiss (03) RESTORE Monefy
  fidelity (the current app diverges from it).
- No swipe-to-open: the overlay opens only from the state booleans. Do not add a horizontal
  drag-to-open — it would conflict with the dashboard period-swipe (prior SPEC
  `monefy-behavioral-fidelity-07`). The scrim naturally blocks the period-swipe while a drawer is open.

## User points → SPEC map
- **Point 2** (left drawer: tap the dimmed area → dashboard; system back → dashboard): SPEC-03 (on the overlay from 01).
- **Point 3** (right drawer slides from the right; same dismiss behaviours): SPEC-02 (side) + SPEC-03 (dismiss).
- **Point 4** (drawers not full-height / toolbar stays clickable; ☰→back-arrow): SPEC-01 (toolbar) + SPEC-04 (arrow).
- **Point 1** (skip onboarding) is a separate standalone SPEC: `skip-onboarding-temporarily.md`.

## Cross-cutting notes
- This project's runner compiles androidTest and fails if an API rework breaks it — every SPEC here
  MUST update the matching tests in `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt` in
  the SAME pass. Preserve existing test tags (`RIGHT_DRAWER_*`, `DASHBOARD_TOP_BAR_*`,
  `DASHBOARD_DONUT_TAG`, `dashboard_balance_bar`).
- Key files: `feature/dashboard/.../DashboardScreen.kt`, `.../components/DashboardDrawerOverlay.kt`
  (NEW), `.../components/{Left,Right}DrawerContent.kt` (reused verbatim), `.../DashboardViewModel.kt`,
  `.../DashboardState.kt`; `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt`.

## Implementation links
- commit: (pending)
- files: (pending)
