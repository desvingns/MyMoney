# Живой список записей: наблюдение таблицы вместо одноразовой загрузки
Epic: audit4-records
Order: 04 of 05
Status: done
Depends-on: audit4-records-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Список записей всегда отражает актуальное состояние БД: после правки/удаления в деталке возврат на список показывает свежие суммы и группы. ViewModel подписывается на изменения таблицы транзакций (ticker-паттерн дашборда) и перезагружает текущую выборку, сохраняя фильтры/таб/раскрытые группы.
LAYERS: presentation
CHANGED_HINT:
  - feature/transactionslist/.../list/TransactionsListViewModel.kt:66,80-88 — в init добавить `transactionRepository.observeRecent(limit = 1).collect { reload() }` (зеркало G6); reload() переиспользует текущие accountId/currencyId/период/таб/фильтр (G5)
  - тест: правка суммы через fake-репозиторий → состояние списка обновилось; раскрытые группы и активный таб сохранены
TEST_TYPES: unit
CONSTRAINTS:
  - Подписка через viewModelScope; первая эмиссия не должна дублировать стартовый load (distinct/drop(1) — (assumption) выбрать при реализации).
  - Swipe-delete/undo-флоу (существующий) не ломать — reload не сбрасывает pending-undo состояние.
  - `TransactionsListViewModel.kt` — общий файл со SPEC 01: выполняется ПОСЛЕ него.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Список записей не устаревает

  Scenario: Правка в деталке видна по возврату
    Given список открыт и содержит расход 100 «Еда»
    When пользователь открывает деталку, меняет сумму на 250 и возвращается
    Then строка показывает 250 и сумма группы «Еда» пересчитана

  Scenario: Удаление в деталке видно по возврату
    When пользователь удаляет запись в деталке и возвращается
    Then записи нет в списке и суммы обновлены

  Scenario: Фильтры переживают обновление
    Given активен фильтр по категории и раскрыта группа
    When другая запись меняется в БД
    Then фильтр и раскрытие сохранены, данные свежие
```

## Gap / context
Баг M3 аудита: одноразовый load() в init (G5) — список не наблюдает таблицу, возврат из деталки
показывает старые данные. Дашборд уже решает это ticker-паттерном (G6).

## Implementation links
- commit: e7a49f63 (feat), 7803e553 (tests) — pushed to main 2026-06-13
- files:
  - feature/transactionslist/.../list/TransactionsListViewModel.kt — init observes transactionRepository.observeRecent(limit=1).drop(1) → reload(); reload() preserves activeTab/filter/expanded/pending-undo, refreshes groups+transfers
  - TransactionsListViewModelTest.kt + FakeTransactionRepository.kt — 4 reactivity tests + controllable observeRecent trigger
