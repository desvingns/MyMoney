# «Все счета»: диалог конвертация/раздельно + свести к одной валюте
Epic: currency-exchange-rate
Order: 07 of 08
Status: done
Depends-on: 05, 02
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Превратить «Все счета» в шторке в одну запись «по всем валютам» (D8), которая открывает диалог выбора: **свести к одной валюте** или **показать раздельно**. Этот SPEC реализует запись + диалог-развилку + путь «свести к одной»: спросить целевую валюту КАЖДЫЙ раз (D7), посчитать агрегированный баланс всех счетов в целевой валюте (конвертация через SPEC 02), а курсы подтвердить одним диалогом-списком (SPEC 05/D9) по всем исходным валютам сразу. Путь «раздельно» — в SPEC 08 (здесь только развилка ведёт туда). Старую привязку `AllAccounts(currency)` = «все счета валюты X» убрать (D8).
LAYERS: presentation, domain
CHANGED_HINT:
  - feature/dashboard/.../DashboardState.kt:54-62 — заменить `DashboardSelection.AllAccounts(currency)` на `AllAccounts` (все валюты) + состояние режима свёртки `{ ConvertTo(target) | Separate }` (G12); убрать привязку к одной валюте
  - feature/dashboard/.../DashboardViewModel.kt:446-451 — при выборе «Все счета» эмитить Action «диалог конвертация/раздельно» (`SharedFlow replay=0` — G13); для «свести к одной» — Action выбора целевой валюты (каждый раз, D7), затем собрать нужные курсы через `ResolveRateUseCase` (SPEC 04) и показать `RateConfirmDialog` list-режим (SPEC 05/D9)
  - feature/dashboard/.../DashboardViewModel.kt — агрег*ация: сгруппировать счета по валюте, посчитать баланс на группу (`BalanceCalculator.forAccounts` — однотипные по валюте, G11), затем сконвертировать каждую группу в целевую валюту (`ConvertMoneyUseCase` SPEC 02) и суммировать; итог — `BalanceSnapshot` в целевой валюте
  - feature/dashboard/.../ (правый drawer / account selector composable) — одна запись «Все счета» сверху (O3 — точный файл подтвердить: grounding указал `DashboardState/ViewModel`, имя `LeftDrawerContent.AccountDropdown` могло быть путаницей лево/право)
  - строки EN+RU; тесты: VM (Turbine) — выбор «Все счета»→Action развилки; «свести к одной»→выбор цели→список курсов→корректный суммарный баланс в целевой валюте; разовая правка курса не пишется в БД (D5)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **Clash с SPEC 08:** оба правят `DashboardViewModel`/`DashboardState` (и UI дашборда). 07 идёт ПЕРВЫМ, 08 — после; не править эти файлы параллельно.
  - `BalanceCalculator.forAccounts()` требует однотипные по валюте счета (`require()` — G11) — конвертацию делать ПОСЛЕ расчёта баланса каждой валютной группы, не суммировать разные валюты до конвертации.
  - Целевую валюту спрашивать каждый раз, НЕ запоминать (D7). Разовая правка курса (D5) — в БД не писать.
  - Action — `replay=0`, собирать lifecycle-aware (ловушка проекта). API-реворк дашборда обязан обновить совпадающие `androidTest`. ktlintFormat (G20).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Все счета — свести к одной валюте

  Scenario: Выбор «Все счета» открывает развилку
    Given у пользователя есть счета в EUR, USD и RUB
    When в шторке выбирается «Все счета»
    Then открывается диалог с выбором «свести к одной валюте» или «показать раздельно»

  Scenario: Свести к одной — выбор целевой валюты каждый раз
    Given выбран режим «свести к одной валюте»
    When пользователь выбирает целевую валюту RUB
    Then показывается один диалог-список курсов для USD и EUR

  Scenario: Итоговый баланс в целевой валюте
    Given балансы 100 EUR, 100 USD, 1000 RUB и подтверждённые курсы
    When свёртка к RUB подтверждена
    Then показывается суммарный остаток в RUB, сконвертированный по подтверждённым курсам

  Scenario: Целевая валюта не запоминается
    Given в прошлый раз сводили к RUB
    When снова выбирается «свести к одной валюте»
    Then целевая валюта спрашивается заново
```

## Gap / context
Сейчас «Все счета» привязаны к одной валюте и суммируют только её счета — мультивалютной свёртки нет (G12). Нужны развилка и корректная конвертация всех валют к выбранной.

## Implementation links
- commit: d3345386 (feat) + 145b7ce8 (fix: rate math) + dca8184c (test)
- files:  DashboardState/Action/ViewModel/Screen.kt, components/AllAccountsConversionDialog.kt (new), components/LeftDrawerContent.kt, MyMoneyNavHost.kt, EN+RU strings; DashboardViewModelTest + AllAccountsConversionDialogHostUiTest + androidTest updates
