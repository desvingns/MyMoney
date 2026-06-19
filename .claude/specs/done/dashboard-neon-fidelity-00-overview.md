# Дашборд «Neon Ring» — фиделити к мокапу — epic overview
Epic: dashboard-neon-fidelity
Order: 00 of 04
Status: done
Depends-on: dashboard-neon-ring-redesign (отгружен)
Date: 2026-06-19

## Goal
Довести УЖЕ ОТГРУЖЕННЫЙ неоновый дашборд S01 до соответствия HTML-мокапу
(`MyMoney Ring (standalone).html`, вариант «Сетка плиток»). 4 дельты заказчика, где сборка
разошлась с макетом: (1) тонкий тулбар одной строкой без логотипа/валюты, период внутри тулбара;
(2) кольцо меньше (−20%) и выше; (3) плашка доход/расход внутри кольца динамически заполняет
нижнюю зону + тонкая линия-разделитель; (4) FAB −/+ на 10% меньше и без текстовых подписей.
Чистая **presentation**-работа. Вне scope: домен/БД/расчёт баланса, поведение выбора периода,
drawer-контент, цели навигации (search/transfer/overflow), неон-палитра и стиль иконок (уже есть),
содержимое/поведение плиток категорий.

## Locked decisions
- D1: дельты — чистая presentation-фиделити; domain/data/навигацию не трогаем (из запроса).
- D2: тулбар = одна тонкая строка иконок (☰·⇄·‹период›·🔍·⋮), плоский неон-фон; убрать И курсивный логотип «MyMoney», И подпись валюты «Serbian Dinar». Валюта/счёт остаётся видимой в Счетах/Настройках (Q1).
- D3 (assumption): ☰ продолжает открывать левый drawer (☰↔назад-тоггл сохраняем — намеренная Monefy-девиация) (G6).
- D4: переключатель периода (prev/next + метка месяца) переезжает ВНУТРЬ строки тулбара (по центру); отдельной строки `PeriodLabel` под баром больше нет; свайп-навигация периода не меняется (мокап + Q1).
- D5: `neonRingDiameter` 248dp → ≈200dp (−20%, пропорция мокапа); кольцо поднимается выше за счёт тоньшего тулбара и отсутствия отдельной строки периода; stroke/glow пропорц. (Q3).
- D6: плашка доход/расход ДИНАМИЧЕСКИ заполняет нижнюю свободную зону под числом «Остаток» в пределах внутреннего диаметра кольца; + тонкая линия-разделитель 1dp между строками доход/расход (Q2).
- D7: `dashboardFabSize` 104dp → ~94dp (−10%, явно указано); убрать текстовые подписи «РАСХОД»/«ДОХОД» (остаются круги −/+); contentDescription для доступности сохранить (из запроса).
- Допущения (assumption): точные численные лимиты адаптивной плашки (min/max высота, inset) и финальные dp после округления калибруются на устройстве через `/mp --fit` (O1, O2).

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-neon-fidelity-01-thin-toolbar-period-merge.md` | — | presentation | Тонкий неон-тулбар одной строкой; убрать логотип+валюту; период внутрь тулбара |
| 02 | `dashboard-neon-fidelity-02-ring-smaller-raised.md` | 01 | presentation | Кольцо 248→≈200dp (−20%), поднять выше, освободить место под плитки |
| 03 | `dashboard-neon-fidelity-03-center-plate-fill-divider.md` | 02 | presentation | Плашка ± динамически заполняет нижнюю зону внутри кольца + разделитель 1dp |
| 04 | `dashboard-neon-fidelity-04-fabs-smaller-no-labels.md` | — | presentation | FAB −10% (104→~94dp), убрать подписи «РАСХОД»/«ДОХОД» |

## Why this ordering
Порядок диктуется освобождением вертикального места сверху вниз И сериализацией правок общих файлов
(`Spacing.kt`, `DashboardScreen.kt`, `DashboardContentUiTest.kt` правятся в нескольких SPEC-ах —
аддитивно, но в одних файлах, поэтому НЕ параллелить). 01 (тулбар) поднимает верх и освобождает
высоту; 02 (кольцо) уменьшается и поднимается в освобождённое место; 03 (плашка) вписывается в НОВЫЙ
внутренний диаметр кольца → `03 Depends-on 02`; 04 (FAB) логически независим, но делит `Spacing.kt` и
тест-файл → идёт последним во избежание ребейза.

## Key facts (verified)
- G1 (entry): `DashboardRoute()`→`DashboardContent()` — `feature/dashboard/.../DashboardScreen.kt:77-86`.
- G2: `DashboardContent` = `Scaffold(topBar=DashboardTopBar)` → `Column{ PeriodLabel → scroll{NeonRingChart+RingCenterContent → overBudget → CategoryTilesList} → TwoFabLayout }` — `DashboardScreen.kt:89-284`.
- G3: `DashboardTopBar`=`MoneyHeroAppBar` (leading меню/назад, center title «MyMoney»+subtitle=`currentCurrency?.name`, actions Search/SwapHoriz/MoreVert), высота `Spacing.heroAppBarHeight`=64dp — `DashboardScreen.kt:287-340` + `core/designsystem/.../appbar/MoneyHeroAppBar.kt:33-75`.
- G4: `PeriodLabel` — отдельная строка высотой `Spacing.dashboardPeriodRowHeight`=80dp — `feature/dashboard/.../components/PeriodLabel.kt:38-107`.
- G5: тест-теги хардкод `DashboardScreen.kt:427-430`: `DASHBOARD_TOP_BAR_TITLE_TAG`, `…_SUBTITLE_TAG`, `DASHBOARD_DONUT_TAG`, `DASHBOARD_SCROLL_CONTENT_TAG`.
- G6: клики иконок → `DashboardEvent`(SearchClicked/TransferClicked/RightDrawerToggled) → VM → `DashboardAction` → `MyMoneyNavHost.kt:60-121`; ☰ открывает левый drawer.
- G7: `NeonRingChart` — Canvas градиентная дуга (доля=расход÷доход) + blur-glow — `core/designsystem/.../donut/NeonRingChart.kt:62`.
- G8: токены кольца `neonRingDiameter=248dp`, `neonRingStrokeWidth=20dp`, `neonRingGlowRadius=16dp`, `neonRingGlowSpread=4dp`; `calculateNeonRingChartLayout()` — `core/ui/.../theme/Spacing.kt:31-34`.
- G9: `RingCenterContent` — «Остаток» (14sp Medium) + число (48sp Bold) + плашка ↑доход/↓расход (Surface, shapes.extraLarge, surfaceVariant), БЕЗ разделителя — `feature/dashboard/.../components/RingCenterContent.kt:36`.
- G10: плашка ± паддинги h=12dp/v=4dp, gap=2dp, бейдж 12sp SemiBold — `RingCenterContent.kt:116-148`.
- G11: неон-токены цвета (`NeonMint=#5BE3B0`, `NeonCyan=#46B6E6`, `NeonCoral=#FF8A80`, `NeonSurface=#111A2E`, `NeonTrack=#1A2236`, `NeonTextPrimary=#E8EAF0`) — `core/ui/.../theme/Color.kt:9-18,122-129`.
- G12: данные центра/кольца — `periodNet`, `ringFraction`, `expenseTiles` (`DashboardState.kt:12-39`) + `BalanceSnapshot{income,expense,net}` (`core/domain/.../model/BalanceSnapshot.kt:3-8`).
- G13: `NeonRingChart` НЕ `clipRect` (glow не обрезать, реш. прошлого эпика); кольцо не кликабельно.
- G14: `CategoryTilesList` — LazyColumn, плитка 76dp, gap 8dp, под кольцом скроллится — `feature/dashboard/.../components/CategoryTilesList.kt:22`.
- G15: `TwoFabLayout` = Row из двух Column [FAB+подпись]; FAB size=`Spacing.dashboardFabSize`=104dp, border=4dp, Icons Remove/Add, подписи `Text(dashboardFabLabel)` top-padding 16dp — `feature/dashboard/.../components/TwoFabLayout.kt:30-120`.
- G16 (gotcha): `DashboardContentUiTest.kt:821-851` хардкодит `assertWidth/HeightIsEqualTo(104.dp)` для FAB — ресайз ломает тест.
- G17 (gotcha): `DashboardContentUiTest.kt:249-294` кольцо `assertHasNoClickAction`; `:853-866` `BALANCE_BAR_TAG` отсутствует.
- G18 (gotcha): CI `:app:ktlintCheck` (ignoreFailures=false) → `:<module>:ktlintFormat` перед коммитом; нет detekt/jacoco; runner пропускает `:core:*`/`:feature:*` тесты → дашборд-тесты вручную `./gradlew :app:connectedDebugAndroidTest` на устройстве.
- G19: тип-тесты — `unit` / `compose-ui` (эмулятор) / `instrumented` (устройство).

## Risks
- R1 (assumption): «динамическая» плашка внутри круглого кольца может выглядеть странно при малом месте — задать min/max высоту и inset под хорду внутр. круга, откалибровать на устройстве (H2/O1).
- R2: рефактор тулбара/FAB ломает существующие UI-тесты (теги title/subtitle, 104dp, подписи) — каждый SPEC обновляет свои тесты в той же правке (G5/G16).
- R3 (assumption): `MoneyHeroAppBar` может использоваться другими экранами — при правке общего компонента проверить все call-sites, не ломать их (01).

## Reference
- Мокап-источник: `MyMoney Ring (standalone).html` (вариант «Сетка плиток»); рекомендуется сохранить рядом с прошлым референсом в `docs/design/dashboard-redesign/neon-ring/`.
- Ключевые пропорции мокапа (frame 390×844): тулбар одной строкой `padding 2px 7px`; кольцо `R=96 SW=18`, `cy=107` (высоко); плашка `padding 7×14, gap 4`, разделитель `height:1 rgba(255,255,255,0.08)`; FAB 64×64 без подписей.

## Implementation links
- commit: 25748bd5, 653678bb, 95aab200, 69db2038, b8f5a159, 04af3f94, 412b9591, a8e18255, d611d342, 24b3aaa0, debac840, 4d0315af, 8c70b447
- files:  see completed SPECs 01-04
