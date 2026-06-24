# Обрезка пустого хвоста + точки стагнации
Epic: dashboard-trend-selected-period
Order: 02 of 05
Status: done
Depends-on: 01
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Доменная функция авто-серии, которая по окну (из SPEC 01) и `snapshotProvider` строит итоговый `List<TrendPoint>` с обрезкой пустого ХВОСТА и сохранением внутренних/лидирующих пропусков как плоской «стагнации» (D3). Логика: предварительно получить снапшот для каждого бакета окна; `lastActiveIndex` = максимальный индекс, где есть активность (`income.amount.signum() > 0 || expense.amount.signum() > 0`); обрезать окно до `[0 … lastActiveIndex]`; затем посчитать кумулятив (как `invoke`/`CUMULATIVE`, G2) по обрезанному окну, переиндексировав точки 0..k. Внутренние пустые бакеты остаются (кумулятив не сбрасывается → плоский сегмент = стагнация). Лидирующие пустые (до первой активности) НЕ обрезаются — серия начинается со старта периода (index 0).
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculator.kt:37 — добавить `suspend fun buildAutoSeries(window: List<Period>, metric: ChartMetric, snapshotProvider: suspend (Period) -> BalanceSnapshot): List<TrendPoint>`, переиспользуя кумулятивную логику `invoke` (G2) поверх обрезанного окна. Активность — из `BalanceSnapshot.income/expense` (G7).
  - core/domain/.../usecase/BalanceTrendCalculator.kt — (implementation note, уточняет строку выше) `snapshotProvider` вызывается по разу на бакет для определения активности; кэшировать снапшоты, чтобы не звать дважды (один проход).
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculatorTest.kt — добавить unit-тесты на обрезку/стагнацию (фикстуры ниже) (G14).
TEST_TYPES: unit
CONSTRAINTS:
  - Делит файл `BalanceTrendCalculator.kt` с SPEC 01 и 03 — последовательно после 01 (G14).
  - Обрезается ТОЛЬКО хвост (после последней активности). Лидирующие и внутренние пустые бакеты сохраняются (D3).
  - Нет активности ни в одном бакете → `emptyList()` (assumption — пустой график).
  - Активность только в бакете 0 → серия из 1 точки (UI должен пережить <2 точек — отметить в SPEC 04, риск геометрии `BalanceTrendChart`, G13).
  - Только метрика `CUMULATIVE` (текущий дефолт, D4); для `PERIOD_NET`/`INCOME_EXPENSE` обрезка той же логикой по lastActiveIndex (точки берутся как в `invoke`).
  - Детерминизм/`:core:domain` task `test`/backtick/fakes-only (G15/G16).
=== END SPEC ===

### Calculation: trailing-trim + stagnation
- Formula:
  - `snap_i = snapshotProvider(window[i])`, i ∈ 0..n-1.
  - `active_i = (snap_i.income.amount.signum() > 0) ∨ (snap_i.expense.amount.signum() > 0)`.
  - `lastActiveIndex = max { i : active_i }`; если такого нет → результат `∅`.
  - `trimmed = window[0 .. lastActiveIndex]` (сохраняет лидирующие/внутренние пустые).
  - `CUMULATIVE`: `value_j = Σ_{k≤j} snap_k.net.amount` (база 0, D4), масштаб `toMoneyScale(currency)`.
- Symbols: `window` = List<Period> (из SPEC 01); `snap_i` = BalanceSnapshot; `value_j` = BigDecimal (2 dp на границе); индекс точки переиндексируется 0..lastActiveIndex.
- Precision: BigDecimal, `toMoneyScale` на границе (2 dp), как `invoke` (G2). Отрицательный кумулятив НЕ зажимается к нулю.
- Edge: всё пусто → `∅`; активность только в index 0 → 1 точка; активность в середине, дальше пусто → хвост обрезан, внутренние пустые плоские.
- Worked examples (fixtures, метрика CUMULATIVE):
  | окно (n бакетов) | net по бакетам (income−expense) | активные | результат (values) |
  |---|---|---|---|
  | Ex1 Month, 30 дн. | дни 1..7: +10,−4,+6,0(акт.нет),+3,0(акт.нет),+2; дни 8..30: 0 (нет записей) | 1,2,3,5,7 | 7 точек: `10, 6, 12, 12, 15, 15, 17` (дни 8..30 обрезаны) |
  | Ex2 Week, 7 дн. | d1 +10 (income), d5 −4 (expense), остальные пусто | 1,5 | 5 точек: `10, 10, 10, 10, 6` (d6–d7 обрезаны; d2–d4 плоско=стагнация) |
  | Ex3 Month, 30 дн. | нет активности нигде | — | `∅` (пустая серия) |
  | Ex4 Week, 7 дн. | только d1 +10 | 1 | 1 точка: `10` |

  Ручная проверка Ex1: кумулятив по дням 1..7 = 10, 10−4=6, 6+6=12, 12+0=12, 12+3=15, 15+0=15, 15+2=17. День 4 и 6 имеют net 0 НО это «нет активности» (income=expense=0) — они ВНУТРИ диапазона активности (между d3 и d7) → сохраняются как стагнация (12 и 15). lastActiveIndex = day7 (index 6). ✓
  Ex2: d1=+10; d2..d4 нет активности → плоско 10; d5 −4 → 6; lastActive=d5 (index4) → 5 точек. ✓

## Acceptance (Gherkin, UI-agnostic)
```gherkin
Feature: Обрезка хвоста и точки стагнации
  Покрывает D3.

  @trim
  Scenario: Записи в первые 7 дней месяца дают 7 точек
    Given выбран период «Месяц» из 30 дней
    And записи есть только в дни 1..7
    When график строится в авто-режиме
    Then график содержит 7 точек от начала месяца до 7-го дня
    And дни 8..30 на графике отсутствуют

  @trim @stagnation
  Scenario: Пропуск в середине не обрезается, а становится плоским
    Given в выбранном периоде есть записи в день 1 и день 5, между ними записей нет
    When график строится в авто-режиме
    Then точки за дни 2,3,4 присутствуют и равны кумулятиву на конце дня 1
    And последняя точка — за день 5

  @trim @boundary
  Scenario: Период без записей даёт пустой график
    Given в выбранном периоде нет ни одной записи
    When график строится в авто-режиме
    Then график пуст
```

## Gap / context
Закрывает разрыв: при дневных бакетах (SPEC 01) хвост периода без записей рисовался бы как длинная плоская линия. Обрезаем хвост, но сохраняем внутренние «стагнации» (требование ТЗ «не обрезаем … точка стагнации»).

## Implementation links
- commit: fcf939e3
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculator.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculatorTest.kt
