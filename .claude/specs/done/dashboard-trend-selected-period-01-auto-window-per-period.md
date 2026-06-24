# Авто-окно графика по выбранному периоду
Epic: dashboard-trend-selected-period
Order: 01 of 05
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Чистая доменная функция, которая по **выбранному** `Period` строит плотное окно под-бакетов для авто-режима графика. Маппинг (D2): Week → 7 дневных `Period.Day` (weekStart..+6); Month → `lengthOfMonth()` дневных `Period.Day` (день 1..последний); Year → 12 месячных `Period.Month` (янв..дек); All → 30 равных под-диапазонов по [`earliestDate` … `today`]; CustomRange → если дней ≤30, по одному `Period.Day` на день; если >30, 30 равных под-диапазонов. `Period.Day` (Конкретный) этой функцией НЕ обрабатывается — он уходит в отдельный intra-day путь (SPEC 03); для `Period.Day` функция бросает `IllegalArgumentException` (вызывающий не должен звать её для Day). Старый `buildWindow` (ручной режим) НЕ трогаем.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculator.kt:21 — добавить `fun buildAutoWindow(anchor: Period, earliestDate: LocalDate? = null, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Period>` РЯДОМ с существующим `buildWindow` (G1). `today` инжектится (детерминизм, не `LocalDate.now()` внутри).
  - core/domain/.../usecase/BalanceTrendCalculator.kt:75 — для All строить `Period.CustomRange(earliestDate, today)` и переиспользовать `splitRange(..., count = 30, zone)` (G1); НЕ звать `splitRange(Period.All, …)` — её ветка `All` использует `Instant.now()` (скрытый clock, ломает детерминизм). Для CustomRange>30 — `splitRange(anchor, 30, zone)`.
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculatorTest.kt — добавить unit-тесты на `buildAutoWindow` (G14). Старые тесты `buildWindow` НЕ менять.
TEST_TYPES: unit
CONSTRAINTS:
  - Делит файл `BalanceTrendCalculator.kt` с SPEC 02 — строго последовательно (нет параллельной правки одного файла). SPEC 03 — новый файл, не клэшит (G14).
  - Детерминизм: `today` — параметр, без `now()`/I/O внутри (domain-math #6).
  - `earliestDate == null` (нет данных) при All → вернуть `emptyList()` (assumption — график пустой).
  - Месяц: длина через `YearMonth.lengthOfMonth()` (Feb 28/29). CustomRange длина = `ChronoUnit.DAYS.between(start,end)+1`; ровно 30 дней → по-дневно (30 точек), 31+ → 30 равных бакетов.
  - `:core:domain` JVM-тесты идут через task `test` (НЕ `testDebugUnitTest`); backtick-имена; fakes-only (G15/G16).
  - Calculation-блок ниже — обязательные фикстуры для тестов.
=== END SPEC ===

### Calculation: buildAutoWindow (маппинг периода → список бакетов)
- Formula:
  - `Week(ws)`      → `[ Day(ws+0) … Day(ws+6) ]` (ровно 7).
  - `Month(ym)`     → `[ Day(ym.atDay(1)) … Day(ym.atDay(L)) ]`, `L = ym.lengthOfMonth()`.
  - `Year(y)`       → `[ Month(y,1) … Month(y,12) ]` (ровно 12).
  - `All`           → `splitRange(CustomRange(earliestDate, today), 30)` (30 смежных под-диапазонов; последний кончается на `today`).
  - `CustomRange(s,e)`: `d = DAYS.between(s,e)+1`; `d ≤ 30` → `[ Day(s+0) … Day(s+d-1) ]` (d точек); `d > 30` → `splitRange(CustomRange(s,e), 30)` (30 под-диапазонов; последний кончается на `e`).
  - `Day`           → `IllegalArgumentException` (см. SPEC 03).
- Symbols: `ws/s/e/earliestDate/today` = LocalDate; `ym` = YearMonth; `y` = Int; результат = `List<Period>` (Day | Month | CustomRange).
- Precision/rounding: нет денежной математики; границы — целые дни. `splitRange` распределяет дни как `totalDays*i/count` (целочисленно), последний бакет кончается ровно на конце диапазона (как сейчас, G1).
- Edge: All без данных → `[]`; CustomRange ровно 30 дней → 30 дневных; пустой Year/Month/Week невозможен (всегда фикс. длина).
- Worked examples (fixtures, zone = UTC):
  | anchor | earliestDate | today | результат (size + ключевые границы) |
  |---|---|---|---|
  | `Week(Mon 2026-05-11)` | — | — | 7: `Day(2026-05-11)` … `Day(2026-05-17)` |
  | `Month(2026-02)` | — | — | 28: `Day(2026-02-01)` … `Day(2026-02-28)` (2026 не високосный) |
  | `Month(2024-02)` | — | — | 29: `Day(2024-02-01)` … `Day(2024-02-29)` (високосный) |
  | `Year(2026)` | — | — | 12: `Month(2026-01)` … `Month(2026-12)` |
  | `All` | 2026-01-01 | 2026-06-22 | 30 бакетов; `first.start = 2026-01-01`, `last.end = 2026-06-22` |
  | `CustomRange(2026-01-01, 2026-01-10)` | — | — | 10: `Day(2026-01-01)` … `Day(2026-01-10)` |
  | `CustomRange(2026-01-01, 2026-03-31)` (90 дней) | — | — | 30 бакетов CustomRange; `last.end = 2026-03-31` |
  | `Day(2026-05-10)` | — | — | бросает `IllegalArgumentException` |

## Acceptance (Gherkin, UI-agnostic)
```gherkin
Feature: Авто-окно графика по выбранному периоду
  Покрывает D2. Источник: карточка «Аврора» (S01), доменный калькулятор тренда.

  @auto-window
  Scenario: Месяц даёт по одной точке на день
    Given выбран период «Месяц» из 30 дней
    When график строится в авто-режиме
    Then окно содержит 30 точек, по одной на каждый день месяца

  @auto-window
  Scenario: Год даёт 12 точек по месяцам
    Given выбран период «Год»
    When график строится в авто-режиме
    Then окно содержит 12 точек, по одной на каждый месяц

  @auto-window
  Scenario: Диапазон больше 30 дней сжимается до 30 точек
    Given выбран диапазон дат длиной 90 дней
    When график строится в авто-режиме
    Then окно содержит ровно 30 точек
    And последняя точка заканчивается на конце диапазона

  @auto-window @boundary
  Scenario: Всё время без записей даёт пустое окно
    Given нет ни одной транзакции
    When график строится для периода «Всё время» в авто-режиме
    Then окно пустое
```

## Gap / context
Закрывает разрыв: график показывал «4 предыдущих периода + текущий» (целые периоды как точки, G1) — теперь точки = под-бакеты ВНУТРИ выбранного периода. Это фундамент для 02 (обрезка) и 04 (проводка в VM).

## Implementation links
- commit: bfb87d68
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculator.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceTrendCalculatorTest.kt
