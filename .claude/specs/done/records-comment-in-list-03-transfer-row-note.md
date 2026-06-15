# Комментарий перевода в строке списка операций
Epic: records-comment-in-list
Order: 03 of 03
Status: done
Depends-on: records-comment-in-list-02, records-comment-in-list-01
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В строке перевода (`TransferRow`) на экране списка операций отображать `transfer.note` (поле появляется в `TransferRecord` после SPEC 02). Если `note` пуст/null — не рендерить ничего. Если длинный — одна строка с ellipsis. Раскладка `TransferRow` отличается от обычной операции: это `Row[Icon][Column{маршрут; дата}][сумма]` (дата — внутри Column, сумма — справа). Комментарий показывать внутри `Column` отдельной строкой МЕЖДУ маршрутом и датой (порядок: маршрут → комментарий → дата) — это держит комментарий рядом и перед датой, согласованно с операционным слайсом (one-line + ellipsis, D4).
LAYERS: presentation
CHANGED_HINT:
  - feature/transactionslist/.../list/TransactionsListScreen.kt:361-383 — в `Column` функции `TransferRow` добавить между `Text` маршрута и `Text` даты условный `Text(transfer.note, style=transferRowMeta/onSurfaceVariant, maxLines=1, overflow=Ellipsis)` — рендерить только при `!transfer.note.isNullOrBlank()` (G6 после 02)
  - при необходимости `testTag`/`contentDescription` для UI-теста (missing-seam policy, G10)
TEST_TYPES: compose-ui
CONSTRAINTS:
  - ⚠ Общий файл `TransactionsListScreen.kt` с records-comment-in-list-01 — этот SPEC ребейзится ПОВЕРХ 01 (01 правит `TransactionLeaf`, 03 — `TransferRow`); согласовать импорт/линт.
  - Зависит от records-comment-in-list-02: поле `TransferRecord.note` должно уже существовать.
  - (assumption) Точный слот комментария в вертикальной раскладке перевода (маршрут → комментарий → дата) — решение этого SPEC; пользовательское «между суммой и датой» дано для горизонтальной строки операции, у перевода структура иная. Если на ревью предпочтительнее иной слот — поправить здесь.
  - Стиль брать из существующей темы (`transferRowMeta`/`onSurfaceVariant`), без новых токенов.
  - Compose-UI тест: рендер `<...>Content` в `MyMoneyTheme`, один @Test на слайс (G10).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Комментарий перевода виден в списке

  Scenario: Перевод с комментарием
    Given в списке есть перевод с комментарием "На отпуск"
    When открыта вкладка переводов в списке операций
    Then в строке перевода между маршрутом и датой виден текст "На отпуск"

  Scenario: Перевод без комментария
    Given перевод без комментария
    Then дополнительная строка комментария не отображается

  Scenario: Длинный комментарий перевода
    Given у перевода длинный комментарий
    Then он показан в одну строку с многоточием
```

## Gap / context
Завершает эпик: после прокидывания данных (02) строка перевода получает доступ к `note` и
отображает его. Секвенируется после 01 из-за общего файла экрана.

## Implementation links
- commits: d4479b0a (TransferRow note prod), 6531841c (tests)
- files: feature/transactionslist/.../list/TransactionsListScreen.kt, feature/transactionslist/.../list/TransactionListItem.kt (transferNote testTag seam), app/src/androidTest/.../list/TransactionsListContentUiTest.kt, feature/transactionslist/src/test/.../list/TransactionsListContentTest.kt
- verified: instrumented 30/30 green on Pixel_5_API_34 (emulator-5554); :feature:transactionslist:testDebugUnitTest + ktlintCheck pass; lintDebug errors are pre-existing/unrelated (backup_rules.xml FullBackupContent); pushed to main
