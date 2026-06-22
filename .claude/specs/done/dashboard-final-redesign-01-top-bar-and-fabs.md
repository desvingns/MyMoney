# Single-row top bar + three neon-ring FABs
Epic: dashboard-final-redesign
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Redesign the S01 dashboard top bar into a single row (menu · ‹period + mint underline› · more) and the FAB row into three neon-outline rings (− expense coral / ⇄ transfer cyan / + income mint), per the "Dashboard Final" mockup.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt (top bar L382–454, FAB row L337–346 TwoFabLayout); feature/dashboard/.../components/PeriodSwitcher; core/ui/theme/Spacing.kt (dashboardFab*, dashboardTopBar*), Typography.kt (dashboardPeriodSelected); the existing transfer (SwapHoriz) + search (Search) toolbar actions and the more/overflow (MoreVert) action in DashboardScreen; reference mockup scratchpad 04_fourth.jsx (RealTopBar, RealFabs)
TEST_TYPES: compose-ui
CONSTRAINTS: Top bar = ONE row, bg NeonBackground, minHeight ~56dp: [menu icon 48dp, color textPrimary] · [flex center: chevL · column{ period title 22sp/700 capitalize + mint underline bar 78x4dp radius2 NeonMint } · chevR] · [more icon 48dp]. Keep the existing menu<->back-arrow toggle when a drawer is open, and the existing period label/auto-shrink logic — only re-present it centered with the new mint underline. REMOVE transfer (swap) and search icons from the row. Transfer action -> the new middle FAB. Search -> an item in the overflow/right menu (feature must stay reachable). FAB row = THREE neon outline-ring FABs on one row, justified space-between, order −/⇄/+ with colors dashboardActionExpense(coral)/NeonCyan/dashboardActionIncome(mint), reusing dashboardFab* tokens; tune horizontal padding so all three fit evenly (mockup: 88px FABs, 26px side padding). No labels on FABs. Update the stale DashboardTopBarPeriodTitleUiTest + any FAB-row test for the new structure. Strings via resources (en default + ru), no hardcoded user-facing text.
=== END SPEC ===

## Gap / context
Current top bar is two rows (row1 = ☰ ⇄ 🔍 ⋮, row2 = period switcher) and the FAB row has only
two buttons (− +). The mockup collapses the toolbar to a single row and shows three FABs with the
transfer in the middle. This slice moves the transfer action toolbar→FAB and search→overflow so no
feature becomes unreachable mid-epic.

## Implementation links
- commit: dd3b93f2 + 6dd113b8 (theme tokens), 20520782 (feat top-bar+FABs), 9d745b14 (tests)
- files:  DashboardScreen.kt, components/PeriodLabel.kt, components/RightDrawerContent.kt, components/ThreeFabLayout.kt (renamed from TwoFabLayout.kt), feature/dashboard res strings (en+ru), core/ui theme Spacing.kt + Color.kt; tests DashboardContentUiTest.kt + DashboardTopBarPeriodTitleUiTest.kt
