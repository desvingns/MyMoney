# Округление сумм при импорте (Monefy CSV + MyMoney CSV)
Epic: money-decimal-precision
Order: 04 of 05
Status: done
Depends-on: money-decimal-precision-01
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Импорт не должен заносить в БД суммы с более чем 2 знаками. Оба CSV-пути парсят `BigDecimal` и пишут как Double без округления. После SPEC каждая импортируемая сумма нормализуется политикой 01 `toMoneyScale(currency)` непосредственно перед записью (upsert), чтобы данные из чужих файлов сразу были чистыми.
LAYERS: data, domain
CHANGED_HINT:
  - core/domain/.../csv/MonefyCsvImportParser.kt:95-108 — после `parse`/`abs()` суммы применить `.toMoneyScale(currency)` (политика 01) ИЛИ оставить парсер сырым, а округлить в репозитории на записи (см. CONSTRAINTS) (G7)
  - core/database/.../repository/BackupRepositoryImpl.kt:668-705 — `importMonefyCsv`: округлить сумму перед `transactionDao().upsert(toEntity())` (G7)
  - core/database/.../repository/BackupRepositoryImpl.kt:392-494 — `importMyMoneyCsv`: округлить `fields[2].toBigDecimalOrNull()` перед upsert (G8)
  - тесты: импорт строки с суммой "1182337.0799999996"/"12.3456" → в БД 1182337.08 / 12.35
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Точка округления — ОДНА, на границе записи в репозитории (валюта счёта/транзакции там известна для `decimalDigits`); если в парсере валюта недоступна — округлять в `BackupRepositoryImpl`, не в парсере. Не дублировать правило — звать функцию SPEC-01.
  - Облачный restore (`importDb`) — бинарная копия БД, построчно НЕ парсится (G9); этот SPEC его НЕ трогает, старые «грязные» бэкапы чистит миграция SPEC-05.
  - `BackupRepositoryImpl.kt` — частый файл правок эпиков импорта; менять только участки записи сумм, не задевая стратегии слияния/дедупликации.
  - Импортную оркестрацию Room проверять инструментально на устройстве (G15); :core:* runner пропускает (G14); ktlintFormat (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Импорт не заносит суммы с более чем 2 знаками

  Scenario: Monefy CSV с длинным дробным хвостом
    Given строка Monefy CSV с суммой "1182337.0799999996"
    When файл импортируется
    Then транзакция сохранена с суммой 1182337.08

  Scenario: MyMoney CSV с 4 знаками
    Given строка MyMoney CSV с суммой "12.3456"
    When файл импортируется
    Then транзакция сохранена с суммой 12.35

  Scenario: Корректная 2-знаковая сумма не меняется
    Given строка CSV с суммой "100.50"
    When файл импортируется
    Then транзакция сохранена с суммой 100.50
```

## Gap / context
Импорт из чужих файлов (особенно Monefy с конвертацией) мог заносить суммы с длинным дробным хвостом; округление на записи держит БД чистой с момента импорта.

## Implementation links
- commit: 578abe5f
- files:  core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt; core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/BackupCsvTransferTest.kt; core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt
