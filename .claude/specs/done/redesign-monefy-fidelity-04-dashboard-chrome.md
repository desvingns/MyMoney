# Dashboard chrome: top bar, balance pill, two ± FABs (S01)
Epic: redesign-monefy-fidelity
Order: 04 of 05
Status: done
Depends-on: —
Date: 2026-05-30

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Restyle dashboard chrome to S01 — Monefy-style top bar (wordmark title + currency subtitle, overflow on the right), balance pill with a "Баланс" label + grouped amount, and two large ± circular buttons (white fill + coloured ring + coloured icon).
LAYERS: presentation
CHANGED_HINT: feature/dashboard/.../DashboardScreen.kt (TopAppBar); feature/dashboard/.../components/TwoFabLayout.kt; core/designsystem/.../pill/MonefyBalancePill.kt; screenshots 01.jpg/05.jpg; TDD §03_style L124 & L130
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - ± buttons: large (~64-72dp) circular, WHITE container, thick coloured BORDER (tertiary for −, primary for +) + coloured icon — invert the current solid fill. Keep onMinus/onPlus events + haptics. Reference shows NO middle icon between the two circles (transfer lives in the top bar) — drop the middle SwapHoriz or keep it visually subordinate.
  - Top bar: left-aligned title as the app wordmark + a currency-name subtitle line (state.currentCurrency?.name); right actions = search + swap + overflow (Icons.Filled.MoreVert) opening the right drawer (replace the second Menu icon). Keep green container + white content + every existing event.
  - Balance pill: prefix "Баланс " (string resource) + amount via MoneyFormatter (grouped, currency symbol) instead of raw toPlainString; keep positive=primary / negative=tertiary + tap. (Fixes formatBalance bypassing MoneyFormatter.)
  - No VM/behaviour changes; no hardcoded colours/strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
TwoFabLayout uses solid-filled FABs 56dp (reference is the inverse: white fill + coloured ring + coloured icon, larger); the top bar is a plain title (no wordmark/currency subtitle, right uses a hamburger not an overflow); the balance pill lacks the "Баланс" label + grouped formatting.

## Implementation links
- commit: `9c430cf` (feat: restyle dashboard chrome) + `7e251d3` (test: cover dashboard chrome restyle). Local, not pushed.
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/pill/MonefyBalancePill.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/TwoFabLayout.kt
  - feature/dashboard/src/main/res/values/strings.xml
  - feature/dashboard/src/main/res/values-ru/strings.xml
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
