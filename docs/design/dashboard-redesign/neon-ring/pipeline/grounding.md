# Grounding ledger — dashboard «Neon Ring» redesign в MyMoney

## Verified facts
- G1 (entry): `DashboardRoute()`→`DashboardContent(state,onEvent)`; Scaffold(topBar=`DashboardTopBar` ☰/поиск/перевод/⋮, Column: PeriodLabel, `DashboardBalancePanel`, donut Box, over-budget Surface, `TwoFabLayout`) — `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:80-90,114-312`
- G2 (signature): `DashboardState` (period, accounts, currencies, dashboardSelection, balanceSnapshot, slices, expenseCategoryPlaceholders, budgetAlertCategoryIds, overBudgetAmount, isLoading, drawers, showConfetti) — `feature/dashboard/.../DashboardState.kt:11-36`
- G3 (signature): `BalanceSnapshot{income, expense, net, byCategory:[CategoryBalance{categoryId,categoryName,colorHex,total,fraction,iconKey,isExpense}]}` — `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/BalanceSnapshot.kt:3-18`
- G4 (signature): `CategorySlice{categoryId,color,fraction,label,iconKey,hasBudgetAlert}` — `core/designsystem/.../donut/CategorySlice.kt:5-12`
- G5 (pattern): `snapshotToSlices()` — expense-only, fraction=cat/totalExpense, `parseHexColor`, свёртка <2% в OTHER (id `-1L`, `OTHER_GROUP_MAX_FRACTION=0.02f`) — `feature/dashboard/.../DashboardViewModel.kt:297-336,339-346`
- G6 (signature): `MonefyDonutChart` Canvas (outerRadiusFraction=0.62f, ringThicknessFraction=0.36f, Extrude); центр scale-to-fit `:641-687,658-667`; свечение cast-shadow BlurMaskFilter 7dp + clipRect, СРЕЗАЮЩИЙ верх `:892-947` — `core/designsystem/.../donut/MonefyDonutChart.kt:109-389`
- G7 (signature): `DashboardBalancePanel` h=84dp maxW=245dp — `feature/dashboard/.../DashboardScreen.kt:369-426`
- G8 (signature): `TwoFabLayout` Row SpaceBetween, FAB size=90dp, border=4dp, Icon=32dp, label padding=16dp — `feature/dashboard/.../components/TwoFabLayout.kt:30-120`
- G9 (convention): `Spacing` дашборд-токены (`dashboardFabSize=90dp`, `dashboardFabHorizontalPadding=44dp`, `dashboardFabOutlineWidth=4dp`, `dashboardDonutCenterDividerWidth=52dp`, `dashboardDonutCalloutIconSize=40dp`, `dashboardBalancePanelHeight=84dp`, …) — `core/ui/theme/Spacing.kt:5-46`
- G10 (convention): `Color.kt` DarkColors/LightColors + дашборд ColorScheme-расширения; `isLightDashboardPalette = background.luminance()>0.5f` — `core/ui/theme/Color.kt:10-52,85-86,127-182`
- G11 (convention): Typography дашборд-стили (topBarTitle 34sp, periodSelected 22sp, …) — `core/ui/theme/Typography.kt:9-80`; Shapes (balancePanel=12dp, periodIndicator=999dp) — `core/ui/theme/Shape.kt:8-36`
- G12 (convention): деньги = BigDecimal ≤2 знака HALF_UP; `MoneyFormatter.format(amount,symbol,decimalDigits,locale,symbolPosition BEFORE/AFTER)` — `core/common/.../money/MoneyFormatter.kt:9-33`
- G13 (convention): `categoryIcon(iconKey)→ImageVector` — `core/designsystem/.../icon/CategoryIcons.kt:80-120`; `Category{colorHex,iconKey}` строки — `core/domain/.../model/Category.kt:5-15`
- G14 (convention/pattern): `Period` sealed (Day/Week/Month/Year/All/CustomRange); VM observe/combine/recomputeJob + `BalanceCalculator.forAccounts(accounts,currency,period)` на DefaultDispatcher — `feature/dashboard/.../DashboardViewModel.kt:43-140,229-287`; `Period.kt:7-48`
- G15 (gotcha): donut/Canvas `drawText` НЕ семантизируется (нужен pixel probe/captureToImage); дашборд UI-тесты на testTag/onNodeWithTag — `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt`; есть DashboardContentUiTest, DashboardDrawerOverlayUiTest, LeftDrawerPeriodSelectorUiTest, DashboardDrawerBackPressUiTest; donut-юнит DonutGeometryTest/DonutAnimationKeyTest
- G16 (gotcha): CI-гейт `:app:ktlintCheck` (ktlintFormat перед коммитом); runner пропускает `:core:*`/`:feature:*` тесты → прогон вручную, инстру-тесты `connectedDebugAndroidTest` на устройстве; `CategoryIconsTest` хардкодит 67/66 ключей (L153/154) — не добавлять иконки
- G17 (reference): `docs/design/dashboard-redesign/` существует — туда сохранён HTML-макет + `REFERENCE.md`

## Project SPEC format (house style to match)
- board: `.claude/specs/backlog` ; naming: `<epic>-NN-<slug>.md` (overview = `-00-overview.md`)
- front-matter: `# <Title>` / `Epic: <slug>` / `Order: <NN of MM>` / `Status: backlog|draft|active|done` / `Depends-on: <epic-NN | —>` (полный id) / `Date: YYYY-MM-DD`
- SPEC block: `## SPEC` + `=== SPEC === … === END SPEC ===` с полями `TASK / PLATFORM / WHAT / LAYERS / CHANGED_HINT / TEST_TYPES / CONSTRAINTS` (+ `### Calculation` блок при domain_math)
- доп. секции: `## Acceptance (Gherkin)`, `## Gap / context`, `## Implementation links`
- sample: `.claude/specs/backlog/money-decimal-precision-02-balance-calculator-rounding.md`, `.claude/specs/done/redesign-monefy-fidelity-04-dashboard-chrome.md`
