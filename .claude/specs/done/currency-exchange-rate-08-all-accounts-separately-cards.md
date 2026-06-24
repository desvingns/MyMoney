# «Все счета» раздельно: карточки баланса по валютам стопкой
Epic: currency-exchange-rate
Order: 08 of 08
Status: done
Depends-on: 07
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Реализовать ветку «показать раздельно» из развилки SPEC 07 (D6). Когда пользователь выбрал «раздельно», дашборд «Все счета» показывает мультивалютный вид: на КАЖДУЮ валюту, в которой есть счета, своя карточка баланса (доход / расход / остаток в этой валюте), карточки идут вертикальной стопкой со скроллом. Никакой конвертации и курсов здесь нет — каждая валюта считается сама по себе (`BalanceCalculator.forAccounts` по группе валюты, G11). Донат в этом режиме можно убрать/свернуть (D6), чтобы несколько валют не утяжеляли экран.
LAYERS: presentation, domain
CHANGED_HINT:
  - feature/dashboard/.../DashboardState.kt — добавить состояние «раздельный» вид: `List<CurrencyBalanceCard>` (валюта + `BalanceSnapshot` в этой валюте) (расширяет состояние из SPEC 07; G12)
  - feature/dashboard/.../DashboardViewModel.kt — для режима Separate сгруппировать счета по валюте и на каждую группу посчитать `BalanceCalculator.forAccounts()` (однотипные по валюте — G11); отдать список карточек, отсортированных по валюте/остатку
  - feature/dashboard/.../DashboardScreen.kt — отрисовать вертикальную стопку карточек баланса (одна на валюту) со скроллом; в этом режиме скрыть/свернуть донат (D6); переиспользовать существующие токены/негативно-красный баланс
  - строки EN+RU; тесты: VM (Turbine) — Separate даёт по карточке на каждую валюту с верным балансом; Compose-UI — стопка карточек, донат скрыт
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **Clash с SPEC 07:** правит те же `DashboardViewModel`/`DashboardState`/UI дашборда — выполнять СТРОГО после 07 (не параллельно).
  - Без конвертации и без курса — каждая валюта самостоятельна; не звать `ConvertMoneyUseCase`/`ResolveRateUseCase` в этом режиме.
  - `BalanceCalculator.forAccounts()` — только однотипные по валюте счета (`require()` — G11): группировать строго по `Account.currencyId` (G9).
  - API-реворк дашборда обязан обновить совпадающие `androidTest`; ktlintFormat (G20). Визуально перепроверить (semantics не ловят перекрытие/скролл-обрезку).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Все счета — показать раздельно

  Scenario: По карточке на каждую валюту
    Given у пользователя есть счета в EUR, USD и RUB
    And выбран режим «показать раздельно»
    When открывается дашборд «Все счета»
    Then показаны три карточки баланса — по одной для EUR, USD и RUB

  Scenario: Баланс каждой валюты считается без конвертации
    Given в EUR доход 100 и расход 30
    When показывается карточка EUR
    Then остаток EUR равен 70, без пересчёта в другую валюту

  Scenario: Донат скрыт в раздельном режиме
    Given выбран режим «показать раздельно»
    When открывается дашборд «Все счета»
    Then общий донат не показывается, видны только карточки по валютам

  Scenario: Валюты без счетов не показываются
    Given счета есть только в EUR и USD
    When открывается раздельный вид
    Then карточки создаются только для EUR и USD
```

## Gap / context
Ветка «раздельно» из развилки SPEC 07 нуждается в конкретном виде. Стопка карточек баланса по валютам даёт мультивалютную картину без конвертации и без курсового трения.

## Implementation links
- commit: 0f6487cd (tokens) + c1973e05 (feat) + acec6ff0 + 7572b9ab (test fixes) + d0562f61 (tests); pushed to main
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/{Spacing,Color,Typography}.kt — per-currency card tokens
  - feature/dashboard/.../DashboardState.kt — CurrencyBalanceCard + currencyCards + isSeparateMode
  - feature/dashboard/.../DashboardViewModel.kt — Separate mode groups accounts by currencyId, forAccounts() per group, sorted
  - feature/dashboard/.../DashboardScreen.kt — Separate branch renders card stack, donut hidden (D6)
  - feature/dashboard/.../components/CurrencyBalanceCardList.kt (new) — vertical per-currency balance card stack
  - feature/dashboard/src/main/res/values{,-ru}/strings.xml — dashboard_currency_card_income/expense/balance
  - tests: DashboardViewModelTest.kt (Turbine), CurrencyBalanceCardListUiTest.kt (new), DashboardContentUiTest.kt — 42 instrumented green on emulator-5554
