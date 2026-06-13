# CSV-экспорт/импорт переводов (round-trip), без отказа экспорта
Epic: audit4-records
Order: 02 of 05
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: CSV-экспорт перестаёт падать при наличии переводов и включает их в выгрузку: строка с kind=transfer несёт счёт-источник, счёт-получатель и обе суммы (аддитивное расширение текущего MyMoney-формата — новые колонки в конце). Импорт MyMoney-CSV распознаёт kind=transfer и восстанавливает перевод (round-trip без потерь).
LAYERS: data
CHANGED_HINT:
  - core/database/.../repository/BackupRepositoryImpl.kt:100-102 — убрать IOException; сериализация transfer-строк: to_account, to_amount в новых колонках (G2)
  - core/database/.../repository/BackupRepositoryImpl.kt (importTransactionsCsv, MyMoney-путь) — парсинг kind=transfer: резолв обоих счетов (существующий resolv-механизм), восстановление toAccountId/toAmount; легаси-CSV без новых колонок продолжает импортироваться (расход/доход) (G12)
  - unit-тесты: экспорт смеси expense/income/transfer → парс → идентичные сущности (round-trip); легаси-заголовок без новых колонок
TEST_TYPES: unit
CONSTRAINTS:
  - Формат аддитивен: существующие колонки не переименовываются и не сдвигаются — старые выгрузки остаются импортируемыми.
  - Monefy-путь импорта (автодетект по заголовку) НЕ трогать.
  - `BackupRepositoryImpl.kt` правится также в audit7-forms-hardening-01 и audit9-sync-hardening-03 — выполнять в порядке эпиков (этот первый).
  - Экспорт остаётся атомарным: частичная запись при ошибке не оставляет битого файла (текущее поведение сохранить).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: CSV полон и устойчив

  Scenario: Экспорт с переводами не падает
    Given в базе есть расходы, доходы и переводы
    When пользователь выполняет экспорт CSV
    Then файл создаётся успешно
    And содержит строки всех трёх видов операций

  Scenario: Round-trip перевода
    Given экспортирован перевод 500 «Наличные» → «Карта»
    When файл импортируется в чистую базу
    Then восстановлен перевод 500 с теми же счетами

  Scenario: Старый формат без переводов
    Given CSV экспортирован прошлой версией приложения
    When пользователь импортирует его
    Then расходы и доходы импортируются как раньше
```

## Gap / context
Баг M1/M5 аудита: пользователь с хотя бы одним переводом полностью терял CSV-экспорт (G2).
Решение D4b из grill: экспортировать переводы, а не пропускать.

## Implementation links
- commit: 29ed27c8 (feat), 992a9356 (tests) — pushed to main 2026-06-13
- files:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt — removed transfer IOException refusal; export resolves both account names + appends `to_account`,`to_amount` columns; import accepts 9-col legacy and 11-col rows, transfer branch resolves both accounts + amounts
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/csv/MonefyCsvImportParser.kt — `MYMONEY_TRANSFER_HEADER` (additive 11-col); `detectFormat` maps both 9-col and 11-col MyMoney headers to MyMoney; Monefy branch untouched
  - core/domain/src/test/.../csv/MonefyCsvImportParserTest.kt — detectFormat coverage for new header + Monefy regression
  - core/database/src/androidTest/.../BackupCsvTransferTest.kt — 4 device-green round-trip tests (export mix, transfer round-trip, legacy 9-col import)
