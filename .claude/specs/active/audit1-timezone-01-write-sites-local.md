# Запись occurredAt локальной полночью во всех формах транзакций
Epic: audit1-timezone
Order: 01 of 04
Status: active
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Транзакция, сохранённая с датой «D», должна попадать в день/месяц D в ЛЮБОЙ таймзоне. Все 4 точки записи переходят с `atStartOfDay(ZoneOffset.UTC)` на `atStartOfDay(ZoneId.systemDefault())`; чтение даты в форме редактирования — симметрично локально. Редактирование Monefy-импортированной записи перестаёт сдвигать дату на день.
LAYERS: presentation
CHANGED_HINT:
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModel.kt:195 — `ZoneOffset.UTC` → `ZoneId.systemDefault()` (G1)
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModel.kt:195 — то же (G1)
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModel.kt:223 — то же (G1)
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModel.kt:403-404 — запись локально (G1); :108 — чтение `atZone(ZoneId.systemDefault()).toLocalDate()` (G2)
  - Тесты соответствующих VM — обновить ожидаемые инстанты (см. CONSTRAINTS)
TEST_TYPES: unit
CONSTRAINTS:
  - Диалоги дат НЕ трогать: конвертация UTC-millis ↔ LocalDate на границе Material3-пикера остаётся через ZoneOffset.UTC (G6) — это контракт пикера, а не конвенция хранения.
  - Monefy-импорт (G4) уже пишет локально — не трогать.
  - `TransactionDetailViewModel.kt` также правится в audit2-save-integrity-02 — этот SPEC идёт ПЕРВЫМ.
  - В тестах не полагаться на зону машины: фиксировать TimeZone в @Before/@After (полная сюита — SPEC 04).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Дата транзакции стабильна в любой таймзоне

  Scenario: Сохранение в UTC-минус зоне попадает в свой день
    Given системная зона America/New_York (UTC-4)
    And пользователь сохраняет расход с датой 10 июня
    When дашборд показывает период «День: 10 июня»
    Then расход входит в суммы этого дня
    And в периоде «День: 9 июня» расхода нет

  Scenario: Открыл-сохранил не сдвигает дату импортированной записи
    Given системная зона Europe/Moscow
    And запись импортирована из Monefy с датой 1 июня
    When пользователь открывает её в форме редактирования и сохраняет без изменений
    Then дата записи остаётся 1 июня

  Scenario: Запись 1-го числа не утекает в прошлый месяц
    Given системная зона America/New_York (UTC-4)
    When пользователь сохраняет расход с датой 1 июля
    Then расход входит в суммы периода «Месяц: июль» и не входит в июнь
```

## Gap / context
Баг C1 аудита: запись UTC-полночью при локальном чтении (G1↔G3) ломает агрегаты во всех UTC−зонах
и тихо портит даты при редактировании импортированных строк (вторая конвенция G4, чтение G2).

## Implementation links
- commit: 715fca6e (production), 37b825f9 (unit coverage)
- files:
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModel.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModel.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModel.kt
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModel.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModelTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModelTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModelTest.kt
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModelTest.kt
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailFormMappingTest.kt
- verification: blocked before Gradle task execution because this environment has no usable JDK 21. `JAVA_HOME` path from AGENTS is absent; no `java` is on PATH; runner found only PyCharm JBR Java 25.0.2, which fails Kotlin DSL initialization with `IllegalArgumentException: 25.0.2`.
