# Таб «Переводы» на экране записей
Epic: audit4-records
Order: 01 of 05
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Экран записей (S12) получает два таба: «Операции» (текущий группированный список, без изменений) и «Переводы» — список переводов выбранного периода/счёта строками «Счёт A → Счёт B, сумма, дата»; тап открывает существующую деталку (редактирование/удаление переводов становится доступно из UI). Суммы и группы таба «Операции» не меняются; донат дашборда по-прежнему не содержит переводов — закрепляется контракт-тестом.
LAYERS: data, domain, presentation
CHANGED_HINT:
  - core/database/.../dao/TransactionDao.kt — НОВЫЙ запрос getTransfers(accountId?, currencyId?, from, to): kind='transfer', счёт совпадает с from-счётом ИЛИ to-счётом (G1 — текущий getCategoryGroups не трогать)
  - core/domain — модель строки перевода (from/to имена счетов, суммы, дата) + метод репозитория/use case (assumption: форма по образцу GetCategoryRecordsUseCase)
  - feature/transactionslist/.../list/TransactionsListViewModel.kt — состояние activeTab + загрузка переводов (G5 — затронуть минимально, реактивность придёт в SPEC 04)
  - feature/transactionslist/.../list/TransactionsListScreen.kt — TabRow над списком; рендер строк переводов; тап → существующий onOpenDetail(id) (G13)
  - feature/transactionslist/src/main/res/values{,-ru}/strings.xml — «Операции»/«Переводы» (G10)
  - feature/dashboard/src/test/.../DashboardViewModelTest.kt — контракт-тест: слайсы доната не содержат переводов при наличии transfer-строк (G11, D4a)
TEST_TYPES: unit, dao, compose-ui
CONSTRAINTS:
  - Таб «Операции» бит-в-бит прежний: фильтр категории (chip), группировка, суммы — без регрессий; категорийный фильтр на таб «Переводы» не действует.
  - При фильтре по счёту таб показывает только переводы, где счёт является источником или получателем.
  - Баланс конкретного счёта продолжает учитывать переводы (D4a) — НЕ трогать computeBalance.
  - `TransactionsListViewModel.kt` правится также в SPEC 04 — этот первый.
  - Строки без хардкода, EN+RU (конвенция).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Переводы видимы и управляемы

  Scenario: Перевод появляется на своём табе
    Given существует перевод 500 с «Наличные» на «Карта» за текущий месяц
    When пользователь открывает записи и выбирает таб «Переводы»
    Then в списке есть строка «Наличные → Карта» на 500
    And таб «Операции» этой строки не содержит

  Scenario: Редактирование перевода из списка
    Given таб «Переводы» открыт
    When пользователь нажимает строку перевода
    Then открывается деталка этой транзакции с возможностью изменить или удалить её

  Scenario: Донат не знает о переводах
    Given за период есть расходы и переводы
    Then доли доната рассчитаны только по расходам

  Scenario: Пустое состояние
    Given за период переводов нет
    Then таб «Переводы» показывает пустое состояние без ошибки
```

## Gap / context
Баг M1 аудита: переводы (categoryId NULL) отфильтрованы из всех групп (G1) — после сохранения
перевод недостижим в UI (кроме поиска по заметке). Решение D4 из grill — отдельный таб.

## Implementation links
- commits: 5f0b0a4d (M3 transfer tokens) · 8eb28745 (feat: transfers tab S12) · fea12fff (tests)
- files (prod): core/database dao/TransactionDao.kt, projection/TransferRow.kt, mapper/Mappers.kt, repository/TransactionRepositoryImpl.kt; core/domain model/TransferRecord.kt, repository/TransactionRepository.kt, usecase/GetTransferRecordsUseCase.kt; feature/transactionslist list/{TransactionsListViewModel,Screen,UiState,Event}.kt, list/TransactionListItem.kt, res/values{,-ru}/strings.xml; core/ui theme/{Color,Typography}.kt
- files (test): GetTransferRecordsUseCaseTest, TransactionDaoGetTransfersTest (androidTest), TransactionRepositoryImplTest, TransactionsListViewModelTest, TransactionsListContentTest, TransactionsListContentUiTest (app androidTest), DashboardViewModelTest (donut contract)
- verification: 372 JVM unit tests across :core:domain/:core:database/:feature:transactionslist/:feature:dashboard — 0 failures; all androidTest sources compile. Reviewer pass, Verifier pass. Pushed to main.
- note: no Room migration (getTransfers is a SELECT; TransferRow is a projection, not an entity). Reactivity (live list) deferred to SPEC 04 per design.
