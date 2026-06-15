# Недостающие VM-тесты: словари (Account/Currency/Category edit+list)
Epic: audit6-quality-gates
Order: 05 of 05
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Закрыть оставшиеся дыры покрытия :feature:dictionaries: новые AccountEditViewModelTest (валидация денег «ошибка вместо нуля», сохранение, архивация), AccountsListViewModelTest (загрузка/сортировка/навигация), CurrenciesListViewModelTest (список + code-lock логика на уровне списка), CategoryEditViewModelTest (создание/редактирование/архивация — дополняет существующий CategoryEditFromPickerTest).
LAYERS: test
CHANGED_HINT:
  - feature/dictionaries/src/test/.../accounts/AccountEditViewModelTest.kt — НОВЫЙ (G5)
  - feature/dictionaries/src/test/.../accounts/AccountsListViewModelTest.kt — НОВЫЙ (G5)
  - feature/dictionaries/src/test/.../currencies/CurrenciesListViewModelTest.kt — НОВЫЙ (G5)
  - feature/dictionaries/src/test/.../categories/CategoryEditViewModelTest.kt — НОВЫЙ, не дублируя CategoryEditFromPickerTest (G5)
  - переиспользовать существующие module-local fakes модуля (FakeCategoryRepository и т.п.), дополняя по необходимости (G6)
TEST_TYPES: unit
CONSTRAINTS:
  - Характеризация текущего поведения; дефекты — в новые SPEC-и (например, createdAt-затирание уже заведено в audit7-forms-hardening-02 — НЕ фиксировать его тестом как «правильное», пропустить этот аспект если audit7-02 ещё не выполнен).
  - Fakes-only (G6); Turbine для Action-флоу.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Словарные VM под тестами

  Scenario: Невалидная сумма в счёте
    When пользователь сохраняет счёт с нечисловым initial balance
    Then показывается ошибка валидации и счёт не сохраняется

  Scenario: Список счетов
    Given в базе три счёта
    Then состояние списка содержит три строки и тап эмитит навигацию в редактирование

  Scenario: Архивация категории с операциями
    Given у категории есть транзакции
    When пользователь архивирует её
    Then категория скрыта из выбора, транзакции сохранены
```

## Gap / context
Аудит §3 (G5): 4 словарных VM без тестов — средний риск, дешёвое закрытие на существующих фейках.

## Implementation links
- commit: 4dff990b
- files:
  - feature/dictionaries/src/test/.../accounts/AccountEditViewModelTest.kt (extended, +368)
  - feature/dictionaries/src/test/.../accounts/AccountsListViewModelTest.kt (new)
  - feature/dictionaries/src/test/.../currencies/CurrenciesListViewModelTest.kt (new)
  - feature/dictionaries/src/test/.../categories/CategoryEditViewModelTest.kt (extended, +301)
- result: 62 new unit tests green (:feature:dictionaries:testDebugUnitTest); createdAt aspect skipped (deferred to audit7-forms-hardening-02). Epic audit6-quality-gates now COMPLETE (01-05 all shipped).
