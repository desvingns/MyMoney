# SupportState: счётчик роликов и раздельные счётчики кофе
Epic: support-screen-redesign
Order: 04 of 07
Status: done
Depends-on: support-screen-redesign-02
Date: 2026-08-20
Acceptance-matrix: reward_state=absent,present; supporter=fresh,with_purchases
Risk-signals: state, viewmodel

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: `SupportState` начинает нести все три числа, которые показывает карточка благодарности:
суммарное число просмотренных роликов и раздельные счётчики маленького/большого кофе.
Число роликов `SupportViewModel` получает напрямую из `ObserveAdRewardStateUseCase` (G36) — а не
через слот-композабл из `RewardedAdViewModel` (D13), — счётчики кофе приходят из уже расширенного
в SPEC-02 `SupporterState`. Вёрстка в этом SPEC не меняется: SPEC готовит данные, SPEC-07 их
показывает.
LAYERS: presentation
CHANGED_HINT:
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportState.kt:6-11 — добавить `adsWatchedTotal: Int = 0` в `SupportState`; счётчики кофе НЕ дублировать полями — они уже внутри `supporterState` после SPEC-02 (G14, G33)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportViewModel.kt:34-59 — добавить в конструктор `observeAdRewardState: ObserveAdRewardStateUseCase` и подписку в `init`, обновляющую `adsWatchedTotal` из `AdRewardState.totalWatched` (G36, G17)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportViewModel.kt — подписка идёт в `viewModelScope` рядом с существующим `observeSupporterState()`, ошибки — тем же путём `reportToSentry`, что и остальные потоки VM (G17)
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportViewModelTest.kt — тесты: `adsWatchedTotal` приходит из use-case и обновляется при эмиссии; отсутствие данных даёт 0; счётчики кофе прокидываются из `SupporterState`
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/ — при отсутствии фейка `ObserveAdRewardStateUseCase`/его источника завести StateFlow-based фейк рядом с существующими (fakes only, без MockK) (assumption)
TEST_TYPES: unit
CONSTRAINTS:
  - `ObserveAdRewardStateUseCase` живёт в `:core:domain`, а `feature/support` уже от него зависит
    (G31) — новых зависимостей модуля не добавлять.
  - `RewardedAdViewModel` не трогать: у него собственная подписка на тот же use-case (G36), и две
    независимые подписки — это нормально, они читают один источник.
  - Не заводить в `SupportState` дублирующие поля `smallCoffeeCount`/`largeCoffeeCount`: единственный
    источник — `supporterState`, иначе появятся два расходящихся представления одних данных.
  - `AnalyticsEvent.SupportOpened` в `init` и порядок существующих подписок не менять (G17) —
    новая подписка добавляется, существующие не переставляются.
  - Тесты — Turbine + фейки, без MockK/Mockito; `@Ignore` и ослабление ассертов запрещены.
  - SPEC зависит от SPEC-02: до него `SupporterState` не содержит раздельных счётчиков и тесты
    не скомпилируются.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Данные счётчиков на экране поддержки

  Scenario: Число просмотренных роликов попадает в состояние экрана
    Given источник состояния наград сообщает о двенадцати просмотренных роликах
    When экран поддержки открывается
    Then состояние экрана содержит двенадцать просмотренных роликов

  Scenario: Обновление наград обновляет состояние экрана
    Given экран поддержки открыт
    When число просмотренных роликов увеличивается
    Then состояние экрана отражает новое значение без переоткрытия экрана

  Scenario: Отсутствие данных о наградах даёт ноль
    Given источник состояния наград ещё ничего не сообщил
    Then состояние экрана содержит ноль просмотренных роликов

  Scenario: Счётчики кофе приходят из состояния сторонника
    Given у пользователя две покупки маленького кофе и одна большого
    Then состояние экрана отдаёт эти значения без собственных дублирующих полей
```

## Gap / context
Карточка благодарности из макета показывает три счётчика, но `SupportState` сейчас знает только
`supporterState.purchaseCount` (G14), а число роликов живёт в отдельном `RewardedAdViewModel`
и попадает на экран через слот `TotalAdsWatchedBadge` (G11), который редизайн удаляет (D4).
Этот SPEC переносит источник данных в `SupportViewModel` до того, как SPEC-07 нарисует карточку.

## Implementation links
- commit: 56b18a6a6dd4b506bf3ad622b4f47f210508910a + 547900461ef7b9cde2e5695b1d4fae8b568db09b
- files: feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportState.kt; feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportViewModel.kt; feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportViewModelTest.kt
