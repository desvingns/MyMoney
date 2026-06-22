# Aurora hero card + neon-wave trend chart default
Epic: dashboard-final-redesign
Order: 02 of 03
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Replace the standalone trend card + two income/expense panels with one centered "Aurora" hero card (uppercase balance label -> big balance -> income/expense pills -> neon wave trend chart inside), and make the configurable trend chart default to the neon-wave look 1:1 with the mockup.
LAYERS: presentation data
CHANGED_HINT: feature/dashboard/.../DashboardScreen.kt (DashboardTrendBalanceCard L260–274, DashboardBalancePanel L552–613 + its two-card Row L278–297, BalanceTrendChart usage L522–528); core/designsystem/.../chart/BalanceTrendChart.kt + ChartStyle.kt (NeonArea/SmoothArea/Mountain wave styles); feature/dashboard/.../DashboardState.kt (ChartConfig default style L78–87); core/ui/theme/Color.kt + Typography.kt + Theme.kt(shapes); reference mockup scratchpad 03_third.jsx (SecAurora) + 02_second.jsx (ChartWave) + 04_fourth.jsx
TEST_TYPES: compose-ui unit
CONSTRAINTS: New AuroraBalanceCard composable, centered text: container radius 24dp, padding 18/18/14dp, bg radial-gradient(120% 90% at 50% 0%, auroraAccent@0.20 -> white@0.02 at 70%), inset 1px border auroraAccent@0.28 + soft neon glow; auroraAccent = new token 0xFF37E1C0. Content: label (uppercase, 11sp/700, white@0.55, letterSpacing~1) = balance string; balance value 36sp/800 white letterSpacing −1 with accent text-glow = state.periodNet; pills row centered gap~10dp: income pill "↑ <income>" green (0xFF3DF59B, bg@0.12, inset ring@0.3) + expense pill "↓ <expense>" coral-pink (0xFFFF8A9B, bg@0.12, inset ring@0.3), rounded 20dp, 5/12dp padding. Trend chart (existing BalanceTrendChart) embedded INSIDE the card below the pills, height ~116dp. Keep the chart configurable + tap->ChartSettingsSheet + right-menu entry (decision: keep settings); set ChartConfig default style to the wave look and refine that style to match the mockup: smooth area, vertical gradient fill accent@0.4->0, line accent ~2.4dp with glow, point dots, last point larger+lighter, optional weekday labels, accent in multi mode = auroraAccent. Remove the two standalone DashboardBalancePanel cards (replaced by the in-card pills). Keep the over-budget alert + category tiles below. Separate (per-currency) mode is handled in SPEC 03 — do not regress it here. Update stale tests for the removed panels / moved chart. Strings via resources (en + ru).
=== END SPEC ===

## Gap / context
The mockup merges balance + income + expense + trend chart into one centered "Aurora" card with a
radial neon-glow background; today these are a separate trend card plus two 84dp income/expense
panels. This is the headline visual change. The chart stays configurable (grilled decision) — only
its default style flips to the neon wave and the wave style is refined to match.

## Implementation links
- commit: 26a268a5 (tokens), 94b4809d (Aurora card + wave chart), fd1550a5 (wave default), b2ba2c38 (tests)
- files:  components/AuroraBalanceCard.kt (new), DashboardScreen.kt, core/designsystem chart BalanceTrendChart.kt + ChartStyle.kt (SmoothArea wave + Default), core/datastore AppSettings.kt + AppSettingsRepositoryImpl.kt (default smooth_area), core/ui theme Color/Shape/Spacing/Typography; tests AuroraBalanceCardUiTest + DashboardContentUiTest + ChartConfigMappingTest + AppSettingsRepositoryTest
