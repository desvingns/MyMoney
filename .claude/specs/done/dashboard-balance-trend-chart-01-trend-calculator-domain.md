# Тренд баланса — доменный калькулятор точек
Epic: dashboard-balance-trend-chart
Order: 01 of 07
Status: active
Depends-on: —
Date: 2026-06-21

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Чистый доменный use-case `BalanceTrendCalculator`, возвращающий список из N точек тренда (по умолчанию 5: 4 предыдущих периода + текущий) для якорного периода, типа периода, числа точек и метрики. Метрики: `CUMULATIVE` (накопленный баланс с нуля внутри окна — default), `PERIOD_NET` (остаток за каждый период), `INCOME_EXPENSE` (пара доход/расход на точку). Недостающие периоды дают нетто 0 (zero-fill). Для All/CustomRange окно делится на N равных по длине под-интервалов. Тяжёлая логика чистая и тестируется фейками.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/TrendPoint.kt (new) — модель точки: `index: Int`, `label: String`(или сырой `Period` для локализации на UI), `value: Money`, опц. `income`/`expense: Money` для метрики INCOME_EXPENSE. (assumption — новая модель)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/ChartMetric.kt (new) — enum `CUMULATIVE`/`PERIOD_NET`/`INCOME_EXPENSE`. (assumption)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculator.kt (new) — `buildWindow(anchor: Period, count: Int): List<Period>` (чистый генератор окна через `Period.previous()` — G5; для CustomRange/All разбивает диапазон `PeriodArithmetic.toEpochMillisRange` — G:PeriodArithmetic.kt — на N равных под-Period.CustomRange) + `suspend operator fun invoke(window: List<Period>, metric: ChartMetric, snapshotProvider: suspend (Period) -> BalanceSnapshot): List<TrendPoint>` — зовёт provider на каждый период (G4) и аккумулирует.
  - переиспользует `BalanceSnapshot` (G4 — BalanceSnapshot.kt:3), `Period` (G6), `Period.previous()` (G5), `PeriodArithmetic` (grounding).
TEST_TYPES: unit
CONSTRAINTS:
  - Чистый домен, фейки только (без мок-фреймворка). `buildWindow` и аккумуляция — отдельно юнит-тестируемы.
  - Всегда ровно `count` точек (default 5); zero-fill отсутствующих периодов (D4). Никогда не меньше.
  - Деньги — `BigDecimal` в домене (G13); не `Double`. `BigDecimal` создавать через `valueOf`, не `BigDecimal(double)` (memory).
  - Provider-подход (а не прямой `accountId`) — чтобы калькулятор работал для всех режимов выбора дашборда (один счёт / convert / separate); VM подаёт `suspend (Period)->BalanceSnapshot` поверх своего `computeSnapshot` (G7).
  - All/CustomRange: при делении на N под-интервалов крайний правый заканчивается концом исходного диапазона; для `Period.All` диапазон = [первая операция … сегодня] *(assumption O2; источник «первой операции» — через repository, иначе fallback к фикс. окну)*.
  - `:core:domain` тест-задача — `test`, НЕ `testDebugUnitTest`; раннер её пропускает (memory) — проверять модуль напрямую.
  - English-идентификаторы; zero comments кроме неочевидного WHY.
  - Calculation (domain_math):
      Обозначения: `net(p) = income(p) − expense(p)`; окно `p_0..p_{N-1}` от старого к новому.
      CUMULATIVE: `value(k) = Σ_{i=0..k} net(p_i)` (старт от 0 в начале окна).
      PERIOD_NET: `value(k) = net(p_k)`.
      INCOME_EXPENSE: `point(k) = (income(p_k), expense(p_k))`.
      zero-fill: период без операций → `net = 0` (плоский сегмент).
      Округление: масштаб валюты как в `BalanceCalculator` (`toMoneyScale`).
      Примеры (N=5):
        1) нетто [+10, −4, +6, 0, +3] → CUMULATIVE [10, 6, 12, 12, 15]; последняя ≥0.
        2) новый счёт, только текущий +5, 4 прошлых пустые → CUMULATIVE [0, 0, 0, 0, 5].
        3) нетто [+4, −1, −2, −3, −1] → CUMULATIVE [4, 3, 1, −2, −3]; ряд пересекает 0 (между 3-й и 4-й), последняя <0.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Накопленный баланс по 5 месяцам
  Given нетто за 5 последовательных периодов = [+10, -4, +6, 0, +3]
  When вызывается BalanceTrendCalculator с метрикой CUMULATIVE и count=5
  Then возвращаются 5 точек со значениями [10, 6, 12, 12, 15]

Scenario: Zero-fill при недостатке истории
  Given операции есть только в текущем периоде (нетто +5), предыдущие 4 пусты
  When вызывается калькулятор с count=5, метрика CUMULATIVE
  Then возвращаются ровно 5 точек [0, 0, 0, 0, 5]

Scenario: Разбиение произвольного диапазона
  Given выбран Period.CustomRange длиной 10 дней
  When вызывается калькулятор с count=5
  Then окно содержит 5 под-интервалов по 2 дня, последний заканчивается концом диапазона
```

## Gap / context
Сейчас дашборд считает только один снапшот за выбранный период (`recomputeBalance`). Графику нужен ряд из N точек —
выносим чистую логику окна+аккумуляции в домен, чтобы её можно было переиспользовать и тестировать без Android.

## Implementation links
- commit: 861e47eb (prod) + 3f6cd46f (tests)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/ChartMetric.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/TrendPoint.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculator.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculatorTest.kt (20 tests)
