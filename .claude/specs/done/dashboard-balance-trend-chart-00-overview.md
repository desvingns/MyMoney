# Замена кольца дашборда на график тренда баланса — обзор эпика
Epic: dashboard-balance-trend-chart
Order: 00 of 07
Status: done
Completed: 2026-06-22
Depends-on: —
Date: 2026-06-21

## Goal
Заменить неоновое кольцо в центре S01 на линейный **график тренда** внутри карточки остатка, а доход/расход
вынести из центра кольца в 2 отдельные карточки. График показывает **накопленный баланс** по 5 периодам
(4 предыдущих + текущий), с настраиваемым видом. Тап по графику открывает **шторку настроек** (вид/период/метрика/скрытие),
которая хранится в DataStore. В режиме «Все счета → раздельно» мини-график появляется в каждой валютной карточке.
Верхний селектор периода, drawers, FAB и плитки категорий — **без изменений**.

## Locked decisions (из grill.md)
- **D1** Кольцо → карточка остатка с графиком + 2 карточки Доход/Расход. Бейдж «сохранено %» НЕ добавляем.
- **D2** Линия = накопленный баланс **с нуля внутри окна** (нарастающая сумма нетто видимых периодов), не реальный баланс счёта.
- **D3** Большое число = остаток за период (доход − расход), заголовок «ОСТАТОК ЗА <период>». Независимо от линии.
- **D4** Всегда ровно N точек (default 5), недостающие периоды = 0. N настраивается.
- **D5** Month→месяцы, Week→недели, Year→годы, Day→дни; All/CustomRange → N равных под-интервалов (All = первая операция → сегодня — *(assumption)*).
- **D6** Ровно 3 декоративные вертикальные линии (= 4 полосы), не границы периодов; тумблер.
- **D7** Подписи периода под каждой точкой; тумблер.
- **D8** Авто min–max + линия нуля при пересечении 0.
- **D9** Цвет линии зелёный/красный по знаку последней точки (default; правило переключается) + точка на конце.
- **D10** Тап по графику → шторка настроек; тап по сумме сохраняет переход в операции *(assumption — развести зоны тапа)*.
- **D11/D12** Bottom sheet: стиль (≈20), тип периода + число точек, метрика, тумблеры линий/подписей, правило цвета, показать/скрыть.
- **D13** Конфиг persistent (DataStore); когда график скрыт — вход из правого меню ⋮.
- **D14** Режим «раздельно» → мини-график в каждой валютной карточке (тот же конфиг).
- **D15** ≈20 визуальных стилей для просмотра на устройстве и последующего отбора.

## SPECs (запуск через `/mp --feature --next` по порядку)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-balance-trend-chart-01-trend-calculator-domain.md` ✅ done | — | domain | `BalanceTrendCalculator` + `TrendPoint`: N точек, метрики, zero-fill, разбиение All/Range |
| 02 | `dashboard-balance-trend-chart-02-chart-settings-persistence.md` ✅ done | — | data | конфиг графика в `AppSettings` + DataStore |
| 03 | `dashboard-balance-trend-chart-03-trend-chart-component.md` ✅ done | — | presentation | базовый `BalanceTrendChart` в `:core:designsystem` |
| 04 | `dashboard-balance-trend-chart-04-twenty-visual-styles.md` ✅ done | 03 | presentation | ≈20 стилей `ChartStyle` + диспетчер |
| 05 | `dashboard-balance-trend-chart-05-dashboard-integration.md` ✅ done | 01, 03 | presentation | замена кольца: карточка остатка + график + Доход/Расход |
| 06 | `dashboard-balance-trend-chart-06-chart-settings-sheet.md` ✅ done | 02, 04, 05 | presentation | шторка настроек + persistence + вход из ⋮ |
| 07 | `dashboard-balance-trend-chart-07-separate-mode-mini-charts.md` | 01, 03, 05 | presentation | мини-графики в валютных карточках |

## Why this ordering
01/02/03 — независимый фундамент (domain, data, базовый компонент). 03→04 правят один файл графика → последовательно.
05→06→07 все правят `DashboardScreen.kt` / `DashboardViewModel.kt` / `DashboardState.kt` (**same-file clash**) → строго последовательно
в этом порядке. Room-миграция не нужна (конфиг в DataStore Preferences).

## Key facts (verified, см. pipeline/grounding.md)
- **G1** точка замены — `NeonRingChart{RingCenterContent}` в `DashboardScreen.kt:253-276`; плитки ниже на `:293`.
- **G2** `DashboardState.kt:12` — `period`/`balanceSnapshot`/`periodNet`/`isSeparateMode`; новое поле `trendPoints`.
- **G3** доход/расход сейчас ВНУТРИ кольца (`RingCenterContent.kt`), а не карточками — вынос = новая верстка.
- **G4** `BalanceCalculator.invoke(accountId, period)` / `forAccounts(...)` (`BalanceCalculator.kt:28/42`) → `BalanceSnapshot(income/expense/net)`.
- **G5** `Period.previous()/next()` (`Period.kt:31-33`); окно = `[p.shift(-(N-1))…p]`.
- **G7** `recomputeBalance()` (`DashboardViewModel.kt:353`) уже зовёт `computeSnapshot(...)` и `period.previous()` — расширяем до N.
- **G12** компонент в `:core:designsystem` (зеркалит `NeonRingChart.kt`); `:feature:*` не зависит от `:feature:*`.
- **G15** `AppSettings.kt:3` + `AppSettingsRepositoryImpl.kt:41/65` + `AppSettingsKeys.kt` — добавление поля настройки.
- **G16** правое меню — `RightDrawerContent.kt:35`.
- **G17** валютные карточки — `CurrencyBalanceCardList.kt`, `CurrencyBalanceCard` (`DashboardState.kt:60`), `computeCurrencyCards()` (`DashboardViewModel.kt:579`).
- **G18** `Period.localizedLabel()` (`PeriodLabel.kt:203`) — заголовок и подписи точек.

## Implementation links
- commit: —
- files: —
