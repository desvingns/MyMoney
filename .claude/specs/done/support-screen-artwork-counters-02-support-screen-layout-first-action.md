# Перекомпоновка экрана поддержки и постоянный first-action gate
Epic: support-screen-artwork-counters
Order: 02 of 02
Status: done
Depends-on: support-screen-artwork-counters-01-support-screen-artwork-refresh
Date: 2026-08-23
Risk-signals: visual, persistence, billing-state

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Убрать только hero-чашку, сохранить заголовок и описание, поставить карточку аватара со счётчиками сразу под ними, а затем рекламу, маленький кофе, большой кофе и Plus; карточка скрыта до первого подтверждённого действия и после него остаётся видимой через перезапуск и окончание подписки.
LAYERS: [domain] [data] [presentation]
CHANGED_HINT:
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:74-195` — сохранить `SupportBackRow` и `SupportHeadline`, убрать только `Image(support_neon_hero_cup)`, перестроить колонку в порядке headline → conditional `SupporterGratitude` → `adSlot` → coffee purchase → `plusSlot`; consumers пяти новых artwork IDs не менять (G2, G3, G12).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:399-507` — оставить avatar и три отдельных counter labels/values; не рендерить всю карточку до gate, после gate отображать нули для ещё не использованных действий (G4, G13).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportState.kt:8-14` — добавить immutable presentation state для исторического `hasSupportActivity`/эквивалентного visibility contract, не вычислять его только по `badgeEarned` или текущему entitlement (G5, D4, D8).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportViewModel.kt:36-128` — продолжить collect supporter/ad state, добавить наблюдение `ObserveEntitlementUseCase`, и после authoritative ad/coffee/subscription confirmation обновлять монотонный support-activity state; не выдавать visibility на запуске рекламы, pending-покупке или не подтверждённой подписке (G6, G8, G10, D5).
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupporterState.kt:3-8` и `SupporterRepository.kt:6-18` — расширить Supporter-owned state/API историческим монотонным признаком поддержки либо эквивалентным контрактом, чтобы feature не обращался напрямую к DataStore (G7, G16, G17).
  - `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt:20-26` — добавить persisted monotonic support-activity field с default `false`, совместимый с существующими supporter counters и без сброса при старой миграции (G16, D8).
  - `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImpl.kt:25-71` — читать/записывать исторический флаг, выставлять его при подтверждённой покупке или remote state, и выполнить backfill из существующих coffee counters; ad/subscription confirmation должен использовать тот же Supporter-owned write contract (G17, H4).
  - `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt:35-79,100-109,150-157` — сериализовать новый флаг и защищать его от перехода `true → false`, как остальные monotonic supporter fields (G18).
  - `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt:38-108,347-405` — переписать проверки hero/order/gratitude: заголовок остаётся, hero отсутствует, карточка скрыта при отсутствии activity и показывается после activity; проверить порядок и нулевые/ненулевые counters (G13).
  - `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportViewModelTest.kt:59-153` и `core/datastore/src/test/kotlin/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImplTest.kt` — добавить тесты authoritative ad/coffee/subscription gates, monotonic persistence, cold-start/backfill и отсутствие показа для pending/unconfirmed states (G13, G17).
TEST_TYPES: [unit, datastore, compose-ui, visual-device]
CONSTRAINTS:
  - «Первое действие» — только подтверждённое состояние: `AdRewardState.totalWatched > 0`, reconciled coffee counter > 0 или active subscription entitlement с source `SUBSCRIPTION_MONTHLY`/`SUBSCRIPTION_YEARLY`. Opening Paywall, starting video, pending purchase и ad awaiting confirmation не открывают карточку (D5, G8, G9, G10).
  - После первого действия visibility monotonic: окончание/отмена/истечение подписки не скрывает уже показанную карточку; app reinstall/import/sync semantics не должны обнулить флаг без явного factory reset (D4, D8, G18).
  - Coffee counters остаются раздельными: small espresso увеличивает только `smallCoffeeCount`, large takeaway увеличивает только `largeCoffeeCount`; рекламный total не смешивается с coffee counters (G4, G17).
  - Не менять back navigation, purchase products, rewarded-ad state machine, Paywall callback, billing availability или серверные правила; менять только composition, state observation и durable historical visibility (G1, G6, G12).
  - Не удалять старый hero PNG или строки/файлы физически; убрать его consumer и обновить тестовые контракты по проектному archive-only правилу.
  - Это visual-surface change: пройти Pixel 5/API 34 device gate и проверить порядок/visibility на cold start, до action и после каждого из трёх qualifying actions (G15).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Верхняя часть экрана поддержки и first-action card

  Scenario: Новый пользователь не видит карточку до действия
    Given нет подтверждённых просмотров рекламы, покупок кофе или подписки
    When открыт экран поддержки
    Then видны строка назад, заголовок и описание
    And hero-чашка отсутствует
    And карточка аватара и счётчиков отсутствует

  Scenario: Заголовок предшествует карточке после первого действия
    Given подтверждена покупка большого кофе
    When открыт экран поддержки
    Then сначала отображаются заголовок и описание
    And сразу после них отображается аватар с тремя счётчиками
    And далее идут реклама, маленький кофе, большой кофе и Plus
    And счётчик большого кофе увеличен, а остальные нулевые счётчики не скрыты

  Scenario: Любое подтверждённое действие открывает карточку
    Given карточка ещё скрыта
    When сервер подтверждает просмотр рекламы или активную подписку
    Then карточка становится видимой
    And отображает актуальные значения счётчиков

  Scenario: Видимость переживает окончание подписки и перезапуск
    Given карточка уже была открыта подтверждённой подпиской
    When подписка истекла и приложение запущено снова
    Then карточка остаётся видимой
    And counters сохраняют значения из durable state
```

## Gap / context
Текущий экран всегда показывает благодарность и hero-чашку в порядке `hero → ads → coffee → Plus → gratitude`, а SupportState не хранит исторический факт первого действия. Требуется сохранить copy, заменить только порядок/visibility и сделать gate устойчивым к cold start и expiration.

## Implementation links
- commits: d17ff0be, 4fa76689, 51c7d840, 59741951
- files: SupportScreen.kt, SupportState.kt, SupportViewModel.kt, SupporterRepository.kt, SupporterState.kt, SupporterRepositoryImpl.kt, SupportPurchaseStore.kt, AppSettings.kt, AppSettingsKeys.kt, AppSettingsRepositoryImpl.kt, RecordSupportActivityUseCase.kt, and focused support/datastore/domain tests
