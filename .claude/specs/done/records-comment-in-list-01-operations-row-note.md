# Комментарий операции в строке списка операций
Epic: records-comment-in-list
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В строке обычной операции (`TransactionLeaf`) на экране списка операций (открывается тапом по балансу на дашборде) отображать комментарий `transaction.note` МЕЖДУ суммой и датой. Если `note` пустой/null — не рендерить ничего, высота строки не меняется. Если `note` длинный — одна строка в гибком пространстве между суммой и датой, при переполнении ellipsis (`…`); дата справа всегда видна и не переносится. Поле `note` уже есть в доменной `Transaction` и уже загружено в память списка (G2, G3) — правка чисто presentation, БЕЗ изменений данных/БД/формы.
LAYERS: presentation
CHANGED_HINT:
  - feature/transactionslist/.../list/TransactionsListScreen.kt:640-673 — в `Row` функции `TransactionLeaf` поменять раскладку: сумма (`Text`) перестаёт быть `weight(1f)` → wrap-content; добавить ПОСЛЕ суммы и ПЕРЕД датой комментарий. Когда `transaction.note` не пуст: `Text(note, style=labelMedium/onSurfaceVariant, maxLines=1, overflow=Ellipsis, modifier=Modifier.weight(1f))`. Когда пуст/null: вместо текста `Spacer(Modifier.weight(1f))` — чтобы дата осталась прижатой вправо и высота строки не менялась (D3/D4) (G4)
  - добавить `testTag`/`contentDescription` на комментарий, если потребуется для UI-теста (missing-seam policy: только тег/visibility, не новый UI) (G10)
TEST_TYPES: compose-ui
CONSTRAINTS:
  - ⚠ Общий файл `TransactionsListScreen.kt` с records-comment-in-list-03 (строка перевода) — этот SPEC правит ТОЛЬКО `TransactionLeaf`, 03 правит ТОЛЬКО `TransferRow`; 01 выходит первым, 03 ребейзится поверх.
  - Не трогать данные/маппер/БД/форму — `note` уже в `Transaction` (G2) и в памяти группы (G3).
  - Стиль/токены комментария брать из существующей темы (`labelMedium`, `onSurfaceVariant`), без новых цветов.
  - Compose-UI тест: рендерить `<...>Content` напрямую в `MyMoneyTheme` через `createComposeRule()`; один @Test на слайс (G10).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Комментарий обычной операции виден в списке

  Scenario: Операция с комментарием
    Given в списке есть операция с суммой и комментарием "Кофе с коллегой"
    When открыт список операций (тап по балансу на дашборде)
    Then в строке этой операции между суммой и датой виден текст "Кофе с коллегой"
    And дата операции по-прежнему видна справа

  Scenario: Операция без комментария
    Given в списке есть операция с пустым комментарием
    Then между суммой и датой ничего не отображается
    And высота строки такая же, как у операции с комментарием в одну строку

  Scenario: Длинный комментарий
    Given у операции очень длинный комментарий
    Then комментарий показан в одну строку и обрезан многоточием
    And дата справа не переносится и остаётся видимой
```

## Gap / context
Пользовательский запрос: «Комментарии к операции должны отображаться в списке операций … между
суммой операции и датой». Поле `note` уже вводится в форме (G7-форма) и хранится, но в списке не
показывается. Это основной (и независимый) слайс — обычные операции.

## Implementation links
- commits: d20a0a92 (theme tokens), 6a6eba4a (TransactionLeaf note), 2ba6f865 (tests)
- files: core/ui/.../theme/Typography.kt, core/ui/.../theme/Color.kt, feature/transactionslist/.../list/TransactionsListScreen.kt, feature/transactionslist/.../list/TransactionListItem.kt, app/src/androidTest/.../list/TransactionsListContentUiTest.kt, feature/transactionslist/src/test/.../list/TransactionsListContentTest.kt
- verified: instrumented 26/26 green on emulator-5554; JVM tests + ktlintCheck pass; pushed to main
