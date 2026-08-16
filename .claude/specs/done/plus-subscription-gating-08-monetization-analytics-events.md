# Шесть событий монетизации поверх AnalyticsGateway
Epic: plus-subscription-gating
Order: 08 of 10
Status: done
Depends-on: support-hub-tip-06 (внешний — вводит `AnalyticsGateway`), plus-subscription-gating-04, plus-subscription-gating-05, plus-subscription-gating-06
Date: 2026-08-12
Risk-signals: entitlement, cross-module-data-flow
Acceptance-matrix: event=paywall_shown,trial_started,subscription_purchased,subscription_cancelled,grace_entered,shared_detached; firebase=enabled,disabled

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Добавить в уже существующий (после `support-hub-tip-06`) доменный `AnalyticsGateway` шесть
  событий воронки подписки — показ paywall, старт триала, покупка, отмена, вход в grace, отцепка — и
  расставить их вызовы. Сама инфраструктура аналитики (интерфейс, `FirebaseAnalyticsGateway`,
  `NoOpAnalyticsGateway`, `FakeAnalyticsGateway`, артефакт `firebase-analytics` в каталоге) здесь
  **не создаётся**: её везёт `support-hub-tip-06`.
LAYERS: domain, data
CHANGED_HINT:
  - `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/analytics/AnalyticsGateway.kt` —
    расширить `sealed interface AnalyticsEvent` шестью вариантами:
    `PaywallShown(entryPoint)`, `TrialStarted(productId)`,
    `SubscriptionPurchased(productId, isTrialConversion)`, `SubscriptionCancelled(productId, reason)`,
    `GraceEntered(productId)`, `SharedDetached(reason)`. Файл и три существующих события
    (`SupportOpened`, `SupportPurchaseStarted`, `SupportPurchaseCompleted`) заводит `support-hub-tip-06`.
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/analytics/FirebaseAnalyticsGateway.kt` —
    маппинг новых событий в `logEvent`. Guard `HAS_FIREBASE` и ленивый доступ к SDK уже реализованы
    там же (образец — `RemoteConfigRepositoryImpl.kt:16-32`, G3); повторно их не вводить.
  - `feature/support/.../paywall/PaywallViewModel.kt` (SPEC 04) — `PaywallShown(entryPoint)`.
  - `core/billing/.../EntitlementRepositoryImpl.kt` (SPEC 03) — `TrialStarted`,
    `SubscriptionPurchased`, `SubscriptionCancelled` по подтверждению сервера.
  - `core/sync/.../worker/EntitlementWarningWorker.kt` (SPEC 07) — `GraceEntered` на фронте перехода
    (`previous != GRACE`, SPEC 01).
  - `core/sync/.../shared/SharedSyncCoordinatorImpl.detachToLocalOnly` (SPEC 06) —
    `SharedDetached(reason)` с причиной `EntitlementExpired | RemoteKillswitch | AdRewardWindowEnded`.
  - `core/testing/src/main/kotlin/.../fake/FakeAnalyticsGateway.kt` — фейк уже есть
    (`support-hub-tip-06`); использовать его в тестах, не заводить второй.
TEST_TYPES: unit
CONSTRAINTS:
  - **Инфраструктуру аналитики не дублировать.** `support-hub-tip-06` вводит `AnalyticsGateway`,
    обе реализации, DI-выбор по `HAS_FIREBASE` и фейк. Если тот SPEC ещё не в `done/` — этот не
    стартует. Никакого параллельного `AnalyticsRepository`.
  - **Никаких персональных и финансовых данных в параметрах** (то же правило, что в
    `support-hub-tip-06`): ни сумм, ни валют, ни названий счетов и категорий, ни идентификаторов
    воркспейса, ни email. Разрешены только идентификатор товара, точка входа, причина отцепки и
    признак конверсии триала.
  - `HAS_FIREBASE == false` → ни одного обращения к классам Firebase (G3). Guard уже стоит в
    `FirebaseAnalyticsGateway`; новые ветки маппинга не должны его обходить.
  - Событие отправляется **из одного места на событие**. Дубли (`PaywallShown` и из экрана, и из
    навигации) делают воронку нечитаемой.
  - `SubscriptionCancelled` фиксируется по серверному факту (RTDN-состояние, G12), а не по нажатию в
    приложении: отмена происходит в Google Play, вне приложения.
  - Описание сбора аналитики в политике конфиденциальности — зона `support-hub-tip-08` (Block 2
    черновика `docs/legal/privacy-policy-monetization-draft.md:63-139`), **не** этого SPEC-а и не
    SPEC 10. Дублировать абзац нельзя.
  - Форма Data Safety в Play Console обновляется вручную. *(assumption, O3)*
=== END SPEC ===

## Acceptance

```gherkin
Feature: Аналитика воронки подписки
  Шесть событий поверх уже существующего шлюза аналитики.

  Scenario: Показ экрана подписки
    Given пользователь открывает экран подписки из раздела поддержки
    Then записывается событие показа экрана подписки с указанием точки входа

  Scenario: Старт пробного периода
    Given пользователь оформил годовую подписку с пробным периодом
    When сервер подтверждает право
    Then записывается событие старта пробного периода с идентификатором товара

  Scenario: Покупка
    Given подписка подтверждена сервером
    Then записывается событие покупки с идентификатором товара и признаком конверсии из пробного периода

  Scenario: Отмена фиксируется по серверному факту
    Given пользователь отменил подписку в Google Play
    When сервер получает уведомление об отмене
    Then записывается событие отмены

  Scenario: Вход в льготный период
    Given подписка истекла и начался льготный период
    When ежедневная проверка обнаруживает переход
    Then событие входа в льготный период записывается ровно один раз

  Scenario: Отцепка
    Given пользователь переведён в локальный режим
    Then записывается событие отцепки с указанием причины

  Scenario: Сборка без Firebase не падает
    Given приложение собрано без конфигурации Firebase
    When происходит любое из шести событий
    Then обращения к Firebase SDK не происходит
    And приложение работает без сбоев

  Scenario: В событиях нет персональных данных
    Given записано любое из шести событий
    Then его параметры не содержат сумм, валют, названий счетов и категорий, адресов почты и идентификаторов воркспейса

  Scenario: События поддержки не сломаны
    Given три события раздела поддержки, введённые ранее
    When добавлены шесть событий подписки
    Then прежние события продолжают записываться в том же виде
```

## Gap / context

Без воронки невозможно понять, где отваливаются пользователи — на показе paywall, на триале или при
продлении, — а именно это решает, окупает ли подписка расходы на Supabase, ради чего эпик и
существует. Шлюз аналитики к этому моменту уже есть (`support-hub-tip-06`), поэтому работа сводится
к типам событий и точкам вызова.

## Implementation links
- commits: `cc19802f` (feat: log subscription funnel analytics events), `c5d19a9a` (test: cover subscription funnel analytics events and fix compile), `c4d3f921` (test: assert cancellation propagation explicitly in worker test), `8a9015ae` (test: pin ACTIVE->GRACE emits no SubscriptionCancelled)
- files:
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/analytics/AnalyticsGateway.kt`
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/analytics/FirebaseAnalyticsGateway.kt`
  - `core/billing/src/main/java/com/kshavrin/mymoney/core/billing/EntitlementRepositoryImpl.kt`
  - `core/billing/src/main/java/com/kshavrin/mymoney/core/billing/SubscriptionFunnelTracker.kt` (new)
  - `core/billing/build.gradle.kts`
  - `core/billing/src/test/java/com/kshavrin/mymoney/core/billing/SubscriptionFunnelTrackerTest.kt` (new)
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/EntitlementWarningWorker.kt`
  - `core/sync/src/test/java/com/kshavrin/mymoney/core/sync/worker/EntitlementWarningWorkerTest.kt`
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinatorImpl.kt`
  - `core/sync/src/test/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinatorImplTest.kt`
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallViewModel.kt`
  - `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/paywall/PaywallViewModelTest.kt`

## Deferred hardening
Two non-blocking warnings from the independent critic, deferred rather than fixed inline (different
failure class from the repair cycle's compile/coverage blockers, per the plus-subscription-gating-06
precedent of only inlining same-class warnings):
- `EntitlementRepositoryImpl.refresh()`: `funnelTracker.onServerConfirmed()` runs inside the same
  `mapCatching` block as `cache.update()`. If it ever threw (currently unreachable — every
  `AnalyticsGateway.log()` impl returns `Unit`), `mapCatching` would turn a successful cache write
  into `Result.failure()`, risking a retry and a duplicate analytics event. Consider moving the call
  to an `.onSuccess { }` after the `mapCatching` chain.
- `SharedSyncCoordinatorImplTest`: the already-local-only idempotency test for `detachToLocalOnly`
  confirms no `SharedDetached` fires via code-path inspection but doesn't assert
  `analytics.events.isEmpty()` explicitly.
