# Конкретный день → 12 точек по 2 часа
Epic: dashboard-trend-selected-period
Order: 03 of 05
Status: done
Depends-on: 01
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Доменный калькулятор для авто-режима при выбранном `Period.Day` («Конкретный») — 12 кумулятивных точек, по одной на 2-часовой слот (00–02, 02–04, …, 22–24) выбранного дня, с обрезкой хвоста по последнему слоту с активностью (консистентно с SPEC 02). Поскольку `Period` дневно-выровнен и под-дневной гранулярности НЕТ (G5), это ОТДЕЛЬНЫЙ путь: на вход — уже отфильтрованные на день+счета/валюту сырые транзакции (`findByPeriod`, G6); группировка по `occurredAt` в слот; знак нетто на транзакцию — как у `BalanceCalculator` (income +, expense −, переводы исключены) (G7). Новый файл, чтобы не конфликтовать с 01/02.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/IntradayTrendCalculator.kt (new) — `fun buildIntradaySeries(transactions: List<Transaction>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<BigDecimal>` (до 12 кумулятивных значений, хвост обрезан). Транзакции уже отфильтрованы вызывающим (G6); знак — зеркалит `BalanceCalculator.forAccounts` нетто-конвенцию (G7).
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/IntradayTrendCalculatorTest.kt (new) — unit-тесты (фикстуры ниже).
TEST_TYPES: unit
CONSTRAINTS:
  - НОВЫЙ файл → нет код-клэша с 01/02 (Depends-on 01 — нарративный). Можно реализовывать параллельно с 02.
  - Знак нетто на транзакцию и исключение переводов — зеркалить логику `BalanceCalculator`/`getCategorySummary` income/expense (G7). Точная обработка переводов — (assumption): сверить с DAO-запросом при impl (переводы в `getCategorySummary` не участвуют → в intraday тоже исключаем).
  - Слот полуинтервальный: `[start, end)`; транзакция ровно на 02:00:00 → слот [02:00,04:00). Слот по часу: `j = floor(hour/2)`, в зоне `zone`.
  - Тип результата `List<BigDecimal>` (свести к `List<Float>`+labels для `BalanceTrendChart` в SPEC 04) — O1, (assumption): допустим новый тип `IntradayPoint`, если разработчику удобнее, но он обязан редуцироваться к точкам+подписям рендерера (G13).
  - База кумулятива = 0 на начале дня (D4). Хвост: последний слот с ≥1 транзакцией; нет транзакций → `∅`.
  - Детерминизм (zone — параметр)/`:core:domain` task `test`/backtick/fakes-only (G15/G16).
=== END SPEC ===

### Calculation: intraday 2h cumulative series
- Formula:
  - Слоты `j ∈ 0..11`, слот j = `[ day·00:00 + 2j ч, day·00:00 + 2(j+1) ч )` в `zone`.
  - `net_j = Σ signed(t) для t с occurredAt ∈ слот j`; `signed(t) = +t.amount` (income), `−t.amount` (expense), переводы исключены (G7).
  - `cum_j = Σ_{k≤j} net_k` (база 0, D4).
  - `lastActiveSlot = max { j : в слоте j ≥1 транзакция }`; нет → `∅`. Результат `[ cum_0 … cum_lastActiveSlot ]`.
- Symbols: `transactions` = List<Transaction> (отфильтрованы на день+счета); `t.occurredAt` = Instant; `t.amount` = BigDecimal; `cum_j` = BigDecimal (2 dp на границе).
- Precision: BigDecimal, `toMoneyScale` на границе (2 dp). Отрицательный кумулятив не зажимается.
- Edge: нет транзакций → `∅`; всё в слоте 0 → 1 точка; транзакция на границе слота → в более поздний слот (полуинтервал); транзакция в 23:59 → слот 11.
- Worked examples (fixtures, day = 2026-05-10, zone = UTC):
  | транзакции (occurredAt, signed) | слоты с net | результат (values) |
  |---|---|---|
  | Ex1: +100 @ 09:30 (income), −40 @ 13:00 (expense) | slot4=+100, slot6=−40 | 7 точек: `0,0,0,0,100,100,60` (lastActive=slot6) |
  | Ex2: +50 @ 00:10 | slot0=+50 | 1 точка: `50` |
  | Ex3: +20 @ 02:00:00 (граница), +30 @ 23:59 | slot1=+20, slot11=+30 | 12 точек: `0,20,20,20,20,20,20,20,20,20,20,50` (lastActive=slot11) |
  | Ex4: нет транзакций | — | `∅` |

  Ручная проверка Ex1: 09:30 → `floor(9/2)=4` → slot4 `[08:00,10:00)`; 13:00 → `floor(13/2)=6` → slot6 `[12:00,14:00)`. cum: s0..s3=0, s4=100, s5=100 (пусто, стагнация), s6=60. lastActive=6 → 7 точек. ✓
  Ex3: 02:00:00 → `floor(2/2)=1` → slot1 (НЕ slot0, полуинтервал). 23:59 → `floor(23/2)=11`. ✓

## Acceptance (Gherkin, UI-agnostic)
```gherkin
Feature: График за конкретный день по 2-часовым слотам
  Покрывает D2 (Конкретный день).

  @intraday
  Scenario: День даёт точки по 2-часовым слотам до последней записи
    Given выбран конкретный день
    And в этот день есть доход в 09:30 и расход в 13:00
    When график строится в авто-режиме
    Then график показывает кумулятивный остаток по 2-часовым слотам
    And последняя точка — слот, содержащий 13:00, более поздние слоты отсутствуют

  @intraday @boundary
  Scenario: День без записей даёт пустой график
    Given выбран конкретный день без транзакций
    When график строится в авто-режиме
    Then график пуст

  @intraday @boundary
  Scenario: Запись на границе слота попадает в более поздний слот
    Given в выбранный день есть запись ровно в 02:00
    When график строится в авто-режиме
    Then запись учитывается во втором слоте (02:00–04:00), а не в первом
```

## Gap / context
Закрывает разрыв: «День = 12 точек каждые 2 часа» невыразимо через дневно-выровненный `Period` (G5). Отдельный intra-day путь читает сырые транзакции дня (G6) и бакетит по времени. Самый рискованный SPEC эпика (новая абстракция времени).

## Implementation links
- commit: d259feeb (impl) + d040376c (tests)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/IntradayTrendCalculator.kt (new)
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/IntradayTrendCalculatorTest.kt (new)
