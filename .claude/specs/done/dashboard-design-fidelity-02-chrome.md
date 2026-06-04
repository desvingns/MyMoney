# Dashboard chrome — период, balance-pill, FAB, цвета, отступы
Epic: dashboard-design-fidelity
Order: 02 of 02
Status: done
Depends-on: dashboard-design-fidelity-01
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
WHAT: Align the Dashboard chrome (period header, balance pill, two FABs, income-accent color, spacings) to docs/design/dashboard-redesign/phone.jsx, and pass the new donut params so the screen renders the extruded design donut.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt, feature/dashboard/.../components/PeriodLabel.kt, feature/dashboard/.../components/TwoFabLayout.kt, core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/balancebar/MonefyBalanceBar.kt ; design refs: docs/design/dashboard-redesign/phone.jsx (function Phone), render-current.png, monefy-reference-05.jpg
TEST_TYPES: compose-ui screenshot
CONSTRAINTS:
- Read docs/design/dashboard-redesign/phone.jsx (function Phone) — authoritative for layout/spacing/colors.
- PeriodLabel: CURRENT period label large (≈ headlineSmall), color = colorScheme.primary, FontWeight.Bold, centered; PREVIOUS = small/bodyMedium, faded (onSurfaceVariant @0.4), left; NEXT = hidden (alpha 0f, still occupying balance via a hidden slot OR simply not drawn) — match phone.jsx period strip (year-1 faded · year big green · year+1 opacity 0). Keep it generic for ALL Period types (Day/Week/Month/Year/All/CustomRange), keep maxLines=1.
- MonefyBalanceBar: container color = colorScheme.primary; text color = colorScheme.onPrimary (white); shape = MaterialTheme.shapes.small (~8dp); the two flanking "hamburger" Menu icons sit OUTSIDE the pill, tinted colorScheme.primary at alpha 0.7 (per phone.jsx). Keep onClick, testTag("dashboard_balance_bar"), the balance_bar_label string, and kopecks in the amount. Drop the sign-based red/green animation — pill is always green (per design); negative balance is still surfaced by the separate over-budget Surface in DashboardScreen.
- TwoFabLayout: FAB size 96.dp → 100.dp; border width 7.dp → 6.dp; keep icon size 48.dp; minus border/content = colorScheme.tertiary (red), plus border/content = colorScheme.secondary (income green, #50AB6F — change from primary); keep containerColor = onPrimary (white), CircleShape, 0 elevation, contentDescription + onClick.
- DashboardScreen: pass to MonefyDonutChart the new params — style = DonutStyle.Extrude, ringThicknessFraction ≈ 0.39, sliceGapDegrees = 5f, iconScale = 1.7f, centerDecimalDigits = 0 (center figures without kopecks). Tune spacings toward phone.jsx proportions: period strip top, donut Box horizontal padding, balance horizontal padding, FAB top gap. Keep ALL events/navigation/testTags (DASHBOARD_DONUT_TAG, dashboard_balance_bar, top-bar tags), swipe gestures, drawers, confetti.
- Topbar/logo: keep FontFamily.Cursive (no Pacifico asset in this SPEC).
- Module boundary: :feature:dashboard may depend on :core:* only (never :feature:*). :core:designsystem (balancebar) may NOT depend on feature/app. No Dispatchers.IO. No hardcoded user-facing strings. Comments: zero by default.
=== END SPEC ===

## Gap / context
Сейчас: период prev/current/next одинаково бледный; balance bar — surfaceVariant с текстом
primary/tertiary; FAB 96dp/7dp с plus=primary. Дизайн: крупный зелёный current-период; зелёная
(primary) плашка баланса с белым текстом и «гамбургерами» по бокам; FAB 100dp/6dp с plus=secondary.
Плюс этот SPEC пробрасывает параметры доната из SPEC A в экран и выравнивает отступы.

## Implementation links
- commit: 4c00a88 3812a7b 3d4ddcc 4302287 161caa1 21135fb (+ parallel donut d252a0c ee4db96 9fc7c66 2e4ac61)
- files: MonefyDonutChart.kt, DonutGeometry.kt, MonefyBalanceBar.kt, DashboardScreen.kt, PeriodLabel.kt, TwoFabLayout.kt + UI/unit tests
