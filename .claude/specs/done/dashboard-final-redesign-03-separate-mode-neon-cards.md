# Separate-mode per-currency cards — neon-aurora restyle
Epic: dashboard-final-redesign
Order: 03 of 03
Status: done
Depends-on: dashboard-final-redesign-02
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Restyle the "All accounts -> separately" per-currency balance cards to the new neon-aurora look (aurora gradient container + income/expense pills + neon-wave mini chart), keeping one card per currency group with no conversion.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/.../components/CurrencyBalanceCardList.kt; feature/dashboard/.../DashboardScreen.kt (separate-mode branch L247–251); the AuroraBalanceCard / pill styling introduced in SPEC 02; core/ui/theme tokens (auroraAccent, pill colors, trendChartMiniHeight); reference mockup scratchpad 03_third.jsx (SecAurora)
TEST_TYPES: compose-ui
CONSTRAINTS: Depends on SPEC 02 (reuses auroraAccent + pill styling + wave chart). Each per-currency card adopts the aurora container (radius/gradient/inset border/glow) and shows: currency code, that currency's balance, income/expense pills, and its mini wave trend chart (Spacing.trendChartMiniHeight) — own currency only, NO conversion (preserve current G11 semantics: one card per Account.currencyId group). Keep the existing tag DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG and the stacked-list tag; keep .assertExists() friendliness for the empty list. Donut stays hidden in separate mode. Update CurrencyBalanceCardListUiTest for the new structure. Strings via resources (en + ru).
=== END SPEC ===

## Gap / context
The user asked for separate mode to also adopt the neon redesign. Today its per-currency cards are
a plainer stacked layout; this slice gives each card the same aurora container + pills + wave
mini-chart introduced in SPEC 02, without changing the no-conversion per-currency semantics.

## Implementation links
- commit: 3ed4eb58 (restyle + extract AuroraCardCommon), b968bded (tests)
- files:  components/AuroraCardCommon.kt (new shared AuroraCardSurface+IncomeExpensePills), AuroraBalanceCard.kt (reuse shared), CurrencyBalanceCardList.kt (compact aurora cards); test CurrencyBalanceCardListUiTest.kt
