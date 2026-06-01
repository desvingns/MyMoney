# Balance bar with triple-line glyphs, above the ± buttons (S01)
Epic: monefy-behavioral-fidelity
Order: 04 of 09
Status: done
Depends-on: —
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Replace the balance "pill" above the chart with a Monefy balance bar placed BELOW the donut and just ABOVE the ± buttons (05.jpg): a centered "Баланс <net> ₽" flanked by triple-line (≡) glyphs on BOTH the left and right. Tapping the bar opens the all-records screen (existing BalanceCardClicked navigation). The donut center now carries the income/expense totals (SPEC-02), so the bar shows only the net balance.
LAYERS: presentation
CHANGED_HINT: core/designsystem/.../balancebar/MonefyBalanceBar.kt (NEW — "Баланс " label + grouped amount + a three-horizontal-line glyph on each side; clickable; testTag dashboard_balance_bar); feature/dashboard/.../DashboardScreen.kt (remove the MonefyBalancePill Row at L200-221; render MonefyBalanceBar in the layout slot BETWEEN the donut Box L224-239 and TwoFabLayout L243; relocate the over-budget chip; keep BalanceCardClicked); update app/src/androidTest/.../DashboardContentUiTest.kt + the feature/dashboard tests that reference dashboard_balance_pill; screenshots 05.jpg / 11.jpg. core/designsystem/.../pill/MonefyBalancePill.kt may be left unused or removed if no other caller remains.
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Dashboard layout order (top -> bottom): period area -> donut -> MonefyBalanceBar -> ± buttons (05.jpg / 11.jpg). The bar sits directly above the ± FABs.
  - MonefyBalanceBar (new, in :core:designsystem): a rounded full-width-ish bar, "Баланс " label (R.string, EN+RU) + grouped amount via MoneyFormatter (positive = primary green / negative = tertiary red, reuse the pill's colour convention); a three-horizontal-line glyph on the LEFT and on the RIGHT; clickable -> onClick; testTag dashboard_balance_bar.
  - Keep the over-budget chip behaviour (move it adjacent to / below the bar). Keep BalanceCardClicked -> the existing navigation (SPEC-08b re-points the destination to the reworked records screen).
  - Replace any test asserting the dashboard_balance_pill testTag with dashboard_balance_bar. No VM/domain changes; no hardcoded colours/strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #7 (part b). MonefyBalancePill is rendered in a Row ABOVE the chart
(DashboardScreen.kt:200-221). The reference (05.jpg) shows the balance as a thin bar with triple-line
(≡) glyphs on both sides, just above the ± buttons, and that bar is the entry point to the records
list. After SPEC-02 the donut center carries the income/expense totals, so the bar needs only net.

## Implementation links
Commits:
- `9befee3` feat — MonefyBalanceBar (:core:designsystem) + DashboardScreen relayout (pill row removed; period → donut → bar → ± FABs; over-budget chip moved below bar; BalanceCardClicked kept); EN+RU strings.
- `491cfb5` fix — drop invalid `assertDoesNotExist` import in MonefyDonutChartUiTest (unblocked :core:designsystem androidTest compile).
- `b15eb6d` test — MonefyBalanceBarUiTest (new) + migrated DashboardContentUiTest & MainActivityAddExpenseJourneyTest from `dashboard_balance_pill` → `dashboard_balance_bar`.

Changed files:
- core/designsystem/.../balancebar/MonefyBalanceBar.kt (NEW), core/designsystem res values + values-ru (balance_bar_label).
- feature/dashboard/.../DashboardScreen.kt, feature/dashboard res values + values-ru.
- core/designsystem/.../balancebar/MonefyBalanceBarUiTest.kt (NEW); app androidTest DashboardContentUiTest.kt, MainActivityAddExpenseJourneyTest.kt; core/designsystem androidTest MonefyDonutChartUiTest.kt (import fix).

Verification: 141 unit tests pass, lintDebug ok; androidTest compiles (:core:designsystem, :app). Instrumented compose-ui tests are device-run (defer to `/cmp --device` slice). MonefyBalancePill.kt left unused (no callers) per file-safety rule.
