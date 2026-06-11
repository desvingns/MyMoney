# Дабл-тап-guard на всех путях сохранения
Epic: audit2-save-integrity
Order: 02 of 04
Status: done
Depends-on: audit1-timezone-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Двойной быстрый тап по «сохранить» (в keypad-first флоу — по карточке категории) не должен создавать две транзакции и дважды дёргать popBackStack (второй pop снимает Dashboard → пустой экран). Каждый путь сохранения начинает с синхронной проверки-установки `isSaving`; словарные VM получают такой же флаг.
LAYERS: presentation
CHANGED_HINT:
  - feature/transaction/.../expense/AddExpenseViewModel.kt — save() :170-217 и onCategoryPicked() :155-168: первой строкой `if (_state.value.isSaving) return`; `isSaving = true` синхронно ДО launch (G5)
  - feature/transaction/.../income/AddIncomeViewModel.kt:~184 — то же (G5)
  - feature/transaction/.../transfer/TransferViewModel.kt:203-262 — то же (G5)
  - feature/transactionslist/.../detail/TransactionDetailViewModel.kt:253-343 — то же для save/delete (G5)
  - feature/dictionaries/.../categories/CategoryEditViewModel.kt:81-111, .../accounts/AccountEditViewModel.kt, .../currencies/CurrencyEditViewModel.kt, .../goals/GoalEditViewModel.kt — добавить isSaving в UiState + guard (G6)
  - юнит-тесты всех 8 VM: двойной вызов save → ровно один upsert и ровно один NavigateBack
TEST_TYPES: unit
CONSTRAINTS:
  - Флаг ставится СИНХРОННО в методе-обработчике события, не внутри launch — иначе окно гонки остаётся (G5).
  - При ошибке сохранения isSaving сбрасывается (форма редактируема повторно).
  - Общие файлы: TransactionDetailViewModel после audit1-timezone-01; GoalEdit/AccountEdit затем правятся в audit7-forms-hardening-02 — этот SPEC раньше.
  - UI-слой не менять (никаких disabled-перерисовок сверх существующих) — guard чисто во VM.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Однократное сохранение при дабл-тапе

  Scenario: Дабл-тап по категории в keypad-first флоу
    Given пользователь ввёл сумму 100 на форме расхода
    When пользователь дважды быстро нажимает карточку категории «Еда»
    Then создаётся ровно одна транзакция
    And навигация назад выполняется ровно один раз

  Scenario: Дабл-тап по Save в словаре
    Given форма редактирования категории заполнена корректно
    When пользователь дважды быстро нажимает «Сохранить»
    Then в справочнике появляется ровно одна категория

  Scenario: Ошибка снимает блокировку
    Given сохранение завершилось ошибкой
    Then isSaving сброшен и повторное сохранение возможно
```

## Gap / context
Баг H3 аудита: ни один из 8 путей сохранения не защищён от повторного входа (G5, G6); худший
эффект — дубль транзакции + двойной popBackStack → пустой NavHost.

## Implementation links
- commit: ccfeb611, f359bc0, aef23782
- files:
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditViewModel.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditViewModel.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/currencies/CurrencyEditViewModel.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditViewModel.kt
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditViewModelTest.kt
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditViewModelTest.kt
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/currencies/CurrencyEditViewModelTest.kt
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditViewModelTest.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModel.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModel.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModel.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModelTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModelTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModelTest.kt
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModel.kt
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModelTest.kt
