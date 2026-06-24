# Интеграция диалога курса в кросс-валютный перевод
Epic: currency-exchange-rate
Order: 06 of 08
Status: done
Depends-on: 05
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Вшить every-time диалог курса (SPEC 05) в поток перевода между счетами. Сейчас перевод РАБОТАЕТ (G8–G10), но курс запрашивается молча и экран курса всплывает только если курса НЕТ (`RateMissing→NavigateToRateSetup`). Новое поведение: при переводе между счетами в РАЗНЫХ валютах перед исполнением показывается диалог курса (single-режим) с данными из `ResolveRateUseCase` (SPEC 04) — дата, курс, поле разовой правки; авто-обновление по устареванию срабатывает прозрачно. Подтверждённый/правленый курс используется для расчёта `toAmount` именно этой операции (разовая правка — D5, в БД не пишется). Перевод в ОДНОЙ валюте — без диалога.
LAYERS: presentation
CHANGED_HINT:
  - feature/transaction/.../transfer/TransferViewModel.kt:37 — при разных валютах счетов вызвать `ResolveRateUseCase(from,to)` (SPEC 04) и эмитить one-shot Action «показать диалог курса» (паттерн Action `SharedFlow replay=0` — G13); по подтверждению считать `toAmount` через курс из диалога (использовать `ConvertMoneyUseCase` SPEC 02 / существующий путь `TransferExecutor` G10)
  - feature/transaction/.../transfer/TransferScreen.kt — показать `RateConfirmDialog` (SPEC 05) по Action; прокинуть `onConfirm`/`onRateEdited`/`onDismiss` обратно в VM; dismiss отменяет исполнение перевода
  - feature/transaction/.../transfer/TransferViewModel.kt — разовый правленый курс держать в state операции, НЕ upsert (D5); существующий `NavigateToRateSetup` при полном отсутствии курса можно сохранить как крайний случай или заменить на missing-режим диалога (решение разработчика, отметить)
  - тесты: VM-тесты (Turbine) — разные валюты → Action диалога; подтверждение → корректный `toAmount`; правка → `toAmount` по правленому курсу, без записи в репозиторий курса; одинаковые валюты → без диалога. Обновить существующие тесты перевода если меняется поток (G19, runner androidTest gate)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Переводы уже реализованы (G8–G10) — это ДОРАБОТКА потока, не постройка. Не ломать перевод в одной валюте и сохранение `exchangeRate`/`toAmount` в `Transaction` (G9).
  - Разовая правка курса (D5) не пишется в `CurrencyRateRepository`. Двойного тапа/двойного исполнения избегать (в проекте были double-tap гварды — соблюсти).
  - Action — `SharedFlow replay=0` (G13); собирать через lifecycle-aware хелпер, иначе one-shot Action может потеряться (известная ловушка проекта).
  - **Точность курса:** показанный в диалоге кросс-курс округлён до 2 знаков только ДЛЯ ОТОБРАЖЕНИЯ; подтверждение БЕЗ правки считает `toAmount` по полному (неокруглённому) кросс-курсу из `ResolveRateUseCase`/`ConvertMoneyUseCase` (SPEC 02/04), а ручная правка — по введённому пользователем значению. Округляется только итоговый `toAmount` (`toMoneyScale`, G18).
  - API-реворк потока ОБЯЗАН обновить совпадающие `androidTest` в том же проходе (runner компилирует androidTest). ktlintFormat (G20).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Курс при переводе между счетами

  Scenario: Перевод между счетами разных валют показывает диалог курса
    Given счёт-источник в USD и счёт-получатель в RUB
    When пользователь подтверждает перевод 100 USD
    Then показывается диалог курса с датой обновления, курсом USD→RUB и полем правки

  Scenario: Подтверждение курса исполняет перевод
    Given диалог курса USD→RUB открыт (кросс-курс ≈ 73.3994, показан как 73.40)
    When пользователь подтверждает без правки
    Then перевод считается по полному кросс-курсу и на счёт-получатель зачисляется 7339.94 RUB

  Scenario: Разовая правка применяется только к этому переводу
    Given диалог курса открыт
    When пользователь вводит свой курс и подтверждает
    Then перевод исполняется по введённому курсу
    And сохранённый курс USD→RUB в базе не меняется

  Scenario: Перевод в одной валюте идёт без диалога
    Given оба счёта в RUB
    When пользователь подтверждает перевод
    Then диалог курса не показывается и перевод исполняется один в один
```

## Gap / context
Кросс-валютный перевод считает курс молча и показывает экран курса только при его отсутствии (G10). Нужно показывать диалог «каждый раз» (D3) с разовой правкой и прозрачным авто-обновлением.

## Implementation links
- commit: 53f539d0 (feat) + 0d674fa3 (test)
- files:  TransferViewModel/State/Action/Event/Screen.kt, TransferExecutor.kt (+override params), ClockModule.kt (ZoneId provider), TransferViewModelTest/TransferExecutorTest/TransferScreenContractTest + androidTest updates
