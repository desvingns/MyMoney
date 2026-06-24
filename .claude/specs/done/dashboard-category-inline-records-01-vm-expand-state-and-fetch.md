# Состояние раскрытия категории + ленивая загрузка операций
Epic: dashboard-category-inline-records
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Тап по плитке категории на дашборде раскрывает/сворачивает её операции прямо в состоянии, не уходя с экрана. `DashboardState` получает поля раскрытия (id раскрытой категории + её операции + флаг загрузки). Обработчик `DashboardEvent.SliceClicked(categoryId)` в `DashboardViewModel` ПЕРЕСТАЁТ эмитить `NavigateTransactionsByCategory` и вместо этого: при повторном тапе по уже раскрытой категории — сворачивает; иначе — выставляет её раскрытой и лениво тянет операции этой категории за `state.period` через существующий `GetCategoryRecordsUseCase` (`invoke(accountId, period, categoryId)` для `SpecificAccount`; `forAccounts(accounts, target-currency, period, categoryId)` для `AllAccounts.ConvertTo`), кладёт `group.transactions` в состояние. Режим `AllAccounts.Separate` и `OTHER_CATEGORY_ID` раскрытие не получают.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardState.kt:16-62 — в `DashboardState` добавить `expandedCategoryId: Long? = null`, `expandedRecords: List<Transaction> = emptyList()`, `expandedRecordsLoading: Boolean = false` (G5; тип `Transaction` — G6)
  - feature/dashboard/.../DashboardViewModel.kt:814-845 — переписать ветку `is DashboardEvent.SliceClicked`: сохранить early-return на `OTHER_CATEGORY_ID` (G3); если `categoryId == expandedCategoryId` → свернуть (`expandedCategoryId=null, expandedRecords=emptyList()`); иначе выставить `expandedCategoryId`, `expandedRecordsLoading=true`, запустить загрузку и по результату положить `expandedRecords`. Источник: `SpecificAccount` → `getCategoryRecords.invoke(account.id, period, categoryId)`; `AllAccounts.ConvertTo` → `getCategoryRecords.forAccounts(accounts, mode.target, period, categoryId)`; `AllAccounts.Separate`/`null` → без раскрытия (G3). Брать `group.transactions` из элемента с этим `categoryId` (G4)
  - feature/dashboard/.../DashboardViewModel.kt — внедрить `GetCategoryRecordsUseCase` в конструктор (`@Inject`), загрузку выполнять на `@IoDispatcher`/во `viewModelScope` (G4; convention G9)
  - тест: DashboardViewModelTest — тап раскрывает (state несёт categoryId+операции), повторный тап сворачивает, переключение на другую категорию; используется fake `GetCategoryRecordsUseCase`/репозитория (fakes-only)
TEST_TYPES: unit
CONSTRAINTS:
  - `GetCategoryRecordsUseCase` уже существует — НЕ создавать новый репозиторный метод (G4).
  - После этого SPEC `DashboardAction.NavigateTransactionsByCategory` и ветка NavHost (`MyMoneyNavHost.kt:94-104`) становятся неиспользуемыми для тапа по плитке — НЕ удалять их в этом SPEC (route-contract тест может проверять маршрут; cleanup вне фичи).
  - `DashboardViewModel.kt` также правится в SPEC 03 — этот первым (same-file clash).
  - Только реальные категории: `OTHER_CATEGORY_ID` early-return сохранить; «Прочее» в текущем дизайне отсутствует.
  - Деньги/время — доменные типы `BigDecimal`/`Instant` (G6, G8); UI-форматирование не здесь.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Раскрытие операций категории в состоянии дашборда

  Scenario: Тап по плитке раскрывает операции категории
    Given на дашборде выбран конкретный счёт и период «Месяц»
    And у категории «Еда» есть операции за месяц
    When пользователь тапает плитку «Еда»
    Then состояние помечает «Еда» раскрытой
    And содержит операции «Еды» только за выбранный месяц
    And действие перехода на экран записей НЕ эмитится

  Scenario: Повторный тап сворачивает
    Given плитка «Еда» раскрыта
    When пользователь тапает «Еда» ещё раз
    Then раскрытой категории нет и список операций пуст

  Scenario: Переключение между категориями
    Given плитка «Еда» раскрыта
    When пользователь тапает плитку «Транспорт»
    Then раскрыта «Транспорт» с её операциями, «Еда» свёрнута

  Scenario: Все счета с конвертацией
    Given выбрано «Все счета → в валюту EUR», период «Год»
    When пользователь тапает плитку «Еда»
    Then состояние содержит операции «Еды» по всем счетам за год
```

## Gap / context
Сейчас тап по плитке (`SliceClicked`) уводит на отдельный экран записей (`NavigateTransactionsByCategory`, `DashboardViewModel.kt:814-845`). Этот SPEC закрывает поведенческое ядро: раскрытие/сворачивание и ленивую подгрузку операций категории в состоянии — без UI.

## Implementation links
- commit: 604501df (prod: DashboardState + DashboardViewModel), 305cf2cc (test)
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt (98 tests)
