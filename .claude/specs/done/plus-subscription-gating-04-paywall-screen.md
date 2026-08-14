# Единый paywall-экран Plus с двумя точками входа
Epic: plus-subscription-gating
Order: 04 of 10
Status: done
Depends-on: plus-subscription-gating-03, support-hub-tip-07 (внешний — заводит `:feature:support` и его маршрут)
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Один экран paywall в `:feature:support` (модуль, маршрут и пункт drawer заводит
  `support-hub-tip-07`, D2), на который
  ведут **две** точки входа: раздел «Поддержать проект» и попытка включить Shared-режим на экране
  Cloud sync. Экран показывает два тарифа (€1.99/мес, €12.99/год с 7 днями бесплатно только на
  годовом), объясняет, что именно даёт Plus и что остаётся бесплатным навсегда, явно проговаривает
  правило «платит владелец воркспейса, участники бесплатны» и честно деградирует там, где биллинг
  недоступен.
LAYERS: presentation
CHANGED_HINT:
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt`
    (новый) — единый экран; параметр `entryPoint: PaywallEntryPoint { SupportSection, SharedSyncGate }`
    меняет только заголовок и подводку, набор тарифов и текстов одинаков.
  - `.../paywall/PaywallState.kt`, `PaywallEvent.kt`, `PaywallAction.kt` (новые) — UDF по конвенции
    проекта: `StateFlow<PaywallState>`, `SharedFlow<PaywallAction>` с `replay = 0`. Зеркалить форму
    `CloudSyncState`/`CloudSyncEvent`/`CloudSyncAction` (`feature/cloudsync/.../`, G9) — это
    ближайший образец.
  - `.../paywall/PaywallViewModel.kt` (новый) — `@HiltViewModel`; читает `EntitlementRepository`
    (SPEC 01/03) и каталог тарифов из `:core:billing`; состояния экрана: загрузка цен, тарифы,
    «покупка обрабатывается», «биллинг недоступен в регионе», ошибка.
  - `app/src/main/java/com/kshavrin/mymoney/navigation/` — новый type-safe маршрут `PaywallRoute(entryPoint)`
    (конвенция type-safe navigation закреплена SPEC-ом `review-2026-07-19-type-safe-navigation`) и
    вход из раздела «Поддержать проект». **Клэш с SPEC 05**, который добавит второй вход с Cloud sync —
    этот SPEC идёт первым.
  - `feature/support/src/main/res/values/strings.xml` + `values-ru/strings.xml` — все тексты
    (EN default + RU перевод, конвенция проекта; работает существующий gate
    `review-2026-07-05-missing-translation-gate`).
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **Экран ровно один.** Никаких «paywall для Cloud sync» и «paywall для поддержки» — различие
    только в `entryPoint` и вводном тексте. Дублирование экрана — регресс требования.
  - Экран обязан явно называть **что остаётся бесплатным навсегда**: всё локальное ведение финансов
    и приватный бэкап в свой Dropbox/Google Drive (ADR-0010 D2, :58-68). Free — не урезанная версия.
  - Правило «платит владелец, участники бесплатны» проговаривается на самом paywall, а не только на
    экране Cloud sync: пользователь, пришедший из «Поддержать проект», тоже должен его увидеть.
  - Триал показывается **только** на годовом тарифе (ADR-0010 D3, G19). Показ «7 дней бесплатно»
    рядом с месячным — дефект.
  - Регион без биллинга: экран объясняет, что покупки недоступны в регионе, и **не** показывает
    неработающую кнопку и не сыплет тостами с ошибкой (ADR-0010 :140-143). Локальные возможности при
    этом описываются как доступные.
  - Цены берутся из `ProductDetails` Google Play (локализованная строка), а не хардкодятся. €1.99 /
    €12.99 в текстах — только как фолбэк, когда каталог не загрузился. *(assumption)*
  - `:feature:support` зависит только на `:core:*` — никаких `:feature:*` → `:feature:*` (конвенция
    модульности проекта).
  - a11y: touch-target ≥ 48dp, contentDescription у иконок тарифов, поддержка увеличенного шрифта —
    проект уже держит эти гейты (`review-2026-07-13*`, `audit8-hygiene-04-a11y-pass`).
=== END SPEC ===

## Acceptance

```gherkin
Feature: Экран подписки MyMoney Plus
  Одна витрина для обеих точек входа.

  Scenario: Вход из раздела поддержки
    Given пользователь без Plus открыл раздел «Поддержать проект»
    When он выбирает оформление подписки
    Then открывается экран подписки с обоими тарифами
    And на нём объяснено, что локальный учёт и приватный бэкап остаются бесплатными

  Scenario: Вход из попытки включить Shared
    Given пользователь без Plus пытается включить общий воркспейс на экране облачной синхронизации
    When приложение перехватывает попытку
    Then открывается тот же самый экран подписки
    And подводка объясняет, что подписка нужна именно для общего воркспейса

  Scenario: Триал только на годовом тарифе
    Given экран подписки загрузил цены
    Then у годового тарифа указано «7 дней бесплатно»
    And у месячного тарифа бесплатный период не упоминается

  Scenario: Правило оплаты видно до покупки
    Given пользователь открыл экран подписки из любой точки входа
    Then на экране сказано, что подписку оплачивает владелец воркспейса
    And что приглашённые участники пользуются им бесплатно

  Scenario: Регион без биллинга
    Given Google Play Billing недоступен в регионе пользователя
    When он открывает экран подписки
    Then вместо кнопок покупки показано объяснение, что покупки в регионе недоступны
    And сообщение об ошибке не показывается

  Scenario: Цены ещё не загрузились
    Given каталог Google Play отвечает медленно
    When пользователь открывает экран подписки
    Then показывается состояние загрузки вместо пустых карточек тарифов

  Scenario: Пользователь уже имеет Plus
    Given у пользователя активная подписка
    When он открывает экран подписки
    Then вместо кнопки покупки показан текущий статус и дата следующего списания
```

## Gap / context

Точек, где пользователь упирается в платную возможность, две, а витрина должна быть одна — иначе
тексты, цены и объяснение правила оплаты немедленно разъедутся. Экран также единственное место, где
честно проговаривается региональное ограничение (ADR-0010 принимает его как следствие, а не как баг).

## Implementation links
- commits: `4b13522f`, `a5963fbc`, `33b0ed74`, `774b37ab`, `7ab7e3f4`, `cd4ce166`
- files: `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt`, `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/navigation/Destinations.kt`, `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/`, `feature/support/src/main/res/values*/strings.xml`, and the paywall/navigation tests
- verification: reviewer pass, semantic reviewer pass, runner `2182 passed / 0 failed / 0 skipped`, detekt `ok`, lint `ok`, verifier pass
- scope note: the Cloud sync `SharedSyncGate` caller and entitlement gate remain intentionally owned by SPEC 05; SPEC 04 delivers the shared paywall route and SupportSection entry.
