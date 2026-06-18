# Дашборд «Neon Ring» — редизайн S01 — epic overview
Epic: dashboard-neon-ring-redesign
Order: 00 of 06
Status: done
Depends-on: —
Date: 2026-06-15
Completed: 2026-06-18

## Goal
Переделать главный экран S01 под новый неон-макет (референс: `docs/design/dashboard-redesign/neon-ring/`).
Вместо текущего Monefy-доната с иконками категорий НА кольце — плоское **неоновое градиентное кольцо**
(мятный→циан, со свечением) на тёмно-синем фоне; в центре — крупный «Остаток» (net периода) + пилюля
доход/расход; разбивка категорий переезжает в **отдельный скроллируемый список плиток** под кольцом;
кнопки −/+ крупнее на 15%. Неон-палитра заменяет палитру **всего приложения** (прочие экраны лишь
перекрашиваются, без layout-редизайна). Вне scope: изменение поведения/вёрстки экранов ввода операции,
списка операций, настроек, словарей; изменения домена/БД (баланс считается прежним BalanceCalculator).

## Locked decisions
- Свечение кольца НЕ обрезается сверху/снизу — нужен запас под glow, без обрезающего clipRect (D1, заметка заказчика).
- «Остаток» в центре динамически ужимается, чтобы не касаться внутреннего края кольца (D2, заметка заказчика).
- Кнопки −/+ на 15% крупнее: 90dp → 104dp, иконка/паддинги пропорц. (D3, заметка заказчика).
- Список плиток категорий скроллится при переполнении (D4, заметка заказчика).
- Всё прочее — один в один с макетом (D5, заметка заказчика).
- Кольцо = единая градиентная дуга, БЕЗ сегментов; заполнение = расход ÷ доход за период (clamp 0..1; доход 0 → пустой трек) (D6).
- Центр = net периода (доход − расход), округление до целого; отдельной панели баланса нет, центр кольца её заменяет (D7).
- Неон-палитра заменяет палитру ВСЕГО приложения; ThemeMode System/Light/Dark схлопывается в единый неон (D8 + assumption A1).
- Плитки = только расходы, по убыванию суммы, полоска = доля от расхода, тап → операции категории, БЕЗ свёртки «<2% Другое» (D9).
- Допущения (assumption): топ-бар сохраняется и лишь перекрашивается; FAB сохраняют смысл (−расход/+доход); пилюля доход/расход и кольцо не кликабельны; суммы на плитках/в пилюле тоже целые (под макет); пустой период → пустой трек + подсказка в списке.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-neon-ring-redesign-01-neon-design-tokens.md` | — | presentation | Неон-палитра всего приложения + токены кольца/плиток/FAB (+15%) |
| 02 | `dashboard-neon-ring-redesign-02-dashboard-state-neon.md` | — | presentation | VM/State: net периода, доля кольца = расход÷доход, развёрнутый список расходных категорий |
| 03 | `dashboard-neon-ring-redesign-03-neon-ring-chart.md` | 01 | presentation | Компонент NeonRingChart: градиентная дуга + неклиппируемое свечение + слот центра |
| 04 | `dashboard-neon-ring-redesign-04-ring-center-balance.md` | 01, 03 | presentation | Авто-ужатие «Остаток» (целое) + пилюля доход/расход в слоте центра |
| 05 | `dashboard-neon-ring-redesign-05-category-tiles-list.md` | 01 | presentation | Плитка категории + скроллируемый список под кольцом, тап → drill-down |
| 06 | `dashboard-neon-ring-redesign-06-dashboard-assembly.md` | 01,02,03,04,05 | presentation | Сборка DashboardContent (кольцо+центр+плитки+скролл), рестайл топ-бара, FAB +15%, обновить androidTest |

## Why this ordering
01 (токены) и 02 (состояние) — фундамент: 03/04/05 опираются на неон-токены, 06 — на состояние. 02 правит
свой файл (VM/State) и независим от 01, поэтому может идти параллельно. 03 (кольцо) даёт слот центра, который
заполняет 04 — отсюда `04 Depends-on 03`. 05 (плитки) зависит только от токенов. 06 — интеграция, идёт
последним и единственный правит `DashboardScreen.kt` + androidTest. Пересечений по файлам между SPEC-ами нет
(каждый компонент — новый файл; 02 — VM/State; 06 — экран), последовательность диктуется только `Depends-on`.

## Key facts (verified)
- G1 (entry): `DashboardRoute()`→`DashboardContent(state,onEvent)`; Scaffold(topBar=`DashboardTopBar` ☰/поиск/перевод/⋮, Column: PeriodLabel, `DashboardBalancePanel`, donut Box, over-budget, `TwoFabLayout`) — `feature/dashboard/.../DashboardScreen.kt:80-90,114-312`.
- G2: `DashboardState` (period, accounts, currencies, dashboardSelection, balanceSnapshot, slices, …) — `feature/dashboard/.../DashboardState.kt:11-36`.
- G3: `BalanceSnapshot{income, expense, net, byCategory:[CategoryBalance{categoryId,categoryName,colorHex,total,fraction,iconKey,isExpense}]}` — `core/domain/.../model/BalanceSnapshot.kt:3-18`.
- G4: `CategorySlice{categoryId,color,fraction,label,iconKey,hasBudgetAlert}` — `core/designsystem/.../donut/CategorySlice.kt:5-12`.
- G5: `snapshotToSlices()` — expense-only, fraction=cat/totalExpense, `parseHexColor`, свёртка <2% в OTHER (id `-1L`, `OTHER_GROUP_MAX_FRACTION=0.02f`) — `feature/dashboard/.../DashboardViewModel.kt:297-336,339-346`.
- G6: `MonefyDonutChart` Canvas (outerRadiusFraction=0.62f, ringThicknessFraction=0.36f, Extrude); центр-текст scale-to-fit `:641-687,658-667`; свечение cast-shadow BlurMaskFilter 7dp + **clipRect, срезающий верх** `:892-947` — `core/designsystem/.../donut/MonefyDonutChart.kt:109-389`.
- G7: `DashboardBalancePanel` h=84dp maxW=245dp — `feature/dashboard/.../DashboardScreen.kt:369-426`.
- G8: `TwoFabLayout` Row SpaceBetween, FAB size=90dp, border=4dp, Icon=32dp, label padding=16dp — `feature/dashboard/.../components/TwoFabLayout.kt:30-120`.
- G9: `Spacing` дашборд-токены (`dashboardFabSize=90dp`, …) — `core/ui/theme/Spacing.kt:5-46`.
- G10: `Color.kt` DarkColors/LightColors + дашборд ColorScheme-расширения; `isLightDashboardPalette = background.luminance()>0.5f` — `core/ui/theme/Color.kt:10-52,85-86,127-182`.
- G11: Typography дашборд-стили — `core/ui/theme/Typography.kt:9-80`; Shapes — `core/ui/theme/Shape.kt:8-36`.
- G12: деньги = BigDecimal ≤2 знака; `MoneyFormatter.format(amount,symbol,decimalDigits,locale,symbolPosition BEFORE/AFTER)` — `core/common/.../money/MoneyFormatter.kt:9-33`.
- G13: `categoryIcon(iconKey)→ImageVector` — `core/designsystem/.../icon/CategoryIcons.kt:80-120`; `Category{colorHex,iconKey}` строки — `core/domain/.../model/Category.kt:5-15`.
- G14: `Period` sealed (Day/Week/Month/Year/All/CustomRange); VM observe/combine/recomputeJob + `BalanceCalculator.forAccounts(accounts,currency,period)` — `feature/dashboard/.../DashboardViewModel.kt:43-140,229-287`.
- G15: donut/Canvas `drawText` НЕ семантизируется (pixel/captureToImage); дашборд UI-тесты на testTag/onNodeWithTag — `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt`; есть DashboardContentUiTest, DashboardDrawerOverlayUiTest, LeftDrawerPeriodSelectorUiTest, DashboardDrawerBackPressUiTest; donut-юнит DonutGeometryTest/DonutAnimationKeyTest.
- G16: CI-гейт `:app:ktlintCheck` (ktlintFormat перед коммитом); runner пропускает `:core:*`/`:feature:*` тесты → прогон вручную, инстру-тесты `connectedDebugAndroidTest` на устройстве; `CategoryIconsTest` хардкодит 67/66 ключей (не добавлять иконки).
- G17 (reference): `docs/design/dashboard-redesign/` существует — туда сохранён HTML + `REFERENCE.md`.

## Risks
- R1 (assumption): перекрас ВСЕГО приложения в неон-тёмное затрагивает формы/список/настройки/словари/диалоги, не проектировавшиеся под тёмное — возможны проблемы контраста/читаемости. Эпик их перекрашивает, но не редизайнит; нужен ручной вычит контраста ключевых экранов (или отдельный follow-up SPEC).
- R2: уход от Monefy-доната убирает тап по сектору/иконки на кольце; часть существующих UI-тестов дашборда переписывается в 06.

## Implementation links
- commit: 7b6f789c, 12eb605f, ffe9b6c3, dc5c9902, 401b7710, b930f36c, c171caa8, f5f1ade9
- files:  see completed SPECs 01-06
