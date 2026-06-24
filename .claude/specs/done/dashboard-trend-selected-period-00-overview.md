# Dashboard trend «за выбранный период» — epic overview
Epic: dashboard-trend-selected-period
Order: 00 of 05
Status: done
Depends-on: —
Date: 2026-06-22
Date completed: 2026-06-24

## Goal
График остатка в карточке «Аврора» (и мини-графики per-currency в «раздельно») сейчас рисует кумулятивный баланс по **4 предыдущим периодам + текущий** (фикс. ~5 точек, окно строится `previous()` назад — G1). Делаем новый **авто-режим** (дефолт): график строится **за выбранный период** dashboard, число точек выводится по типу периода, пустой хвост обрезается, внутренние пропуски остаются плоской «стагнацией». Старое поведение **сохраняется** как «ручной» режим (override) — настройки `тип периода` + `число точек` остаются доступны в ручном. Вход — та же карточка/тот же sheet настроек графика. Вне scope: стиль/палитра графика, абсолютный баланс на оси Y, Room-миграция.

## Locked decisions
- **D1:** Авто-режим (за выбранный период, авто-число точек) — новый дефолт; ручной режим (тип периода + число точек = старое «текущий + 4 предыдущих») сохраняется как override. [Q1, confirmed]
- **D2:** Число точек: Неделя/Месяц = 1/день; Год = 1/месяц (12); Всё время = 30 равномерно по [первая запись … сегодня]; Диапазон дат = 1/день, cap 30 (>30 дней → 30 равных бакетов); Конкретный день = 12 по 2 часа. [Q3, confirmed]
- **D3:** Обрезка: спан = [старт выбранного периода … последний бакет с активностью]. Хвост после последней записи обрезаем; внутренние пустые бакеты — плоская стагнация; лидирующие пустые до первой записи остаются плоско от старта. [Q2, confirmed]
- **D4 (assumption):** База кумулятива = 0 на старте выбранного периода (как сейчас, G2) — внутрипериодная динамика, не абсолютный баланс.
- **D5 (assumption):** Авто-режим применяется и к per-currency мини-графикам «раздельно» (G10), каждый в своей валюте, без конвертации.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-trend-selected-period-01-auto-window-per-period.md` | — | domain | Плотное окно под-бакетов из выбранного периода (день/месяц/30-срез/cap-30). |
| 02 | `dashboard-trend-selected-period-02-trailing-trim-stagnation.md` | 01 | domain | Обрезка пустого хвоста по последней активности; внутр. стагнация. |
| 03 | `dashboard-trend-selected-period-03-intraday-2h-buckets.md` | 01 | domain | Конкретный день → 12 точек по 2ч (новый файл, обход дневного `Period`). |
| 04 | `dashboard-trend-selected-period-04-vm-auto-mode-wiring.md` | 01,02,03 | data+presentation | `chartAutoMode` + авто-путь в VM (Аврора и per-currency); labels; дефолт=auto. |
| 05 | `dashboard-trend-selected-period-05-settings-auto-manual-toggle.md` | 04 | presentation | Тумблер «авто/вручную»; period-type+pointCount видны только в ручном. |

## Why this ordering
Foundation-first: чистый домен (01→02→03) перед интеграцией (04) перед UI (05). **01/02 делят `BalanceTrendCalculator.kt`** → строго последовательно (один файл, нет параллельной правки). **03 — новый файл `IntradayTrendCalculator.kt`** → нет код-клэша с 01/02 (Depends-on 01 — концептуальный/нарративный, не файловый). **04/05 делят `ChartConfig`/`ChartConfigMapping`/`ChartSettingsSheet`** → последовательно. Старый `buildWindow` и его тесты (G14) **не трогаем** — они валидны для ручного режима.

## Key facts (verified, см. `~/AppSpecs/dashboard-trend-selected-period/pipeline/grounding.md`)
- **G1:** `BalanceTrendCalculator.buildWindow(anchor,count=5,zone)` — календарные периоды идут `previous()` назад (это «4 предыдущих»); `All`/`CustomRange` → `splitRange` (равные дневные срезы). `core/domain/.../usecase/BalanceTrendCalculator.kt:21-35,75-95`; `DEFAULT_TREND_POINTS=5` :16.
- **G2:** `invoke(window,metric,snapshotProvider): List<TrendPoint>` — `CUMULATIVE` копит running net; пустой бакет не сбрасывает накопитель → внутренняя «стагнация» уже работает при дневных бакетах. `:37-73`.
- **G4:** `Period` = Day/Week/Month/Year/All/CustomRange(start,end). `core/domain/.../model/Period.kt:7-48`.
- **G5:** `Period` дневно-выровнен (`PeriodArithmetic.toEpochMillisRange`, Day→[00:00…23:59]); под-дневной гранулярности НЕТ. `core/domain/.../time/PeriodArithmetic.kt:9-33`.
- **G6:** `TransactionRepository.findByPeriod(accountId,period): List<Transaction>`, `Transaction.occurredAt: Instant`. `core/domain/.../repository/TransactionRepository.kt:25-28`.
- **G7:** `BalanceCalculator.forAccounts(accounts,currency,period): BalanceSnapshot`. `core/domain/.../usecase/BalanceCalculator.kt:42-51`.
- **G8/G9/G10:** VM `recomputeBalance()` :382-396, `trendAnchorPeriod()` :686-698, `computeCurrencyCards()` :604-650 — `DashboardViewModel.kt`.
- **G11/G12:** `ChartConfig{periodType,pointCount,metric,style,...}` `DashboardState.kt:78-97`; `AppSettings.toChartConfig()` `ChartConfigMapping.kt:42-93`.
- **G13:** `AuroraBalanceCard(points:List<Float>,chartConfig)` → `BalanceTrendChart(points,labels,...)` `core/designsystem/.../chart/BalanceTrendChart.kt:134-143`; мини-чарт tag `DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG`.
- **G14:** `BalanceTrendCalculatorTest` хардкодит размеры окна/семантику «4 предыдущих» (:67-359) — остаётся для ручного режима, авто-путь = новые тесты.
- **G15/G16:** chart UI-тесты в `:app` androidTest (instrumented); `:core:domain` JVM через `test`; mp-runner пропускает `:feature:*` тесты + ktlint-гейт (`:<module>:ktlintFormat`); backtick-имена; fakes-only.

## Implementation links
- commit: (pending)
- files:  (pending)
