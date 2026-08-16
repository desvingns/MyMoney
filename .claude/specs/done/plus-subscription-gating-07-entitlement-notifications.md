# Уведомления о состоянии подписки: канал, разрешение, воркер
Epic: plus-subscription-gating
Order: 07 of 10
Status: done
Depends-on: plus-subscription-gating-01, plus-subscription-gating-03, plus-subscription-gating-04 (маршрут paywall)
Date: 2026-08-12
Risk-signals: entitlement, persistence, di-wiring, concurrency
Acceptance-matrix: warning=trial_3d,grace_entered,expired_1d; permission=granted,denied; path=paid,ad_plus

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Завести в проекте первый notification-стек и использовать его для трёх обязательных
  предупреждений платного пути: за 3 дня до конца триала, при входе в Grace, за 1 день до Expired.
  Уведомление дополняет баннер из SPEC 05, а не заменяет его: при отказе от разрешения на уведомления
  пользователь всё равно видит баннер на экране Cloud sync.
LAYERS: data, presentation, docs
CHANGED_HINT:
  - `app/src/main/AndroidManifest.xml` — `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`.
    `minSdk 31`, поэтому на API 33+ это runtime-разрешение (G16). **Клэш с SPEC 03**
    (`com.android.vending.BILLING`) — этот SPEC идёт после него.
  - `TDD/MyMoney/MyMoney_TDD.md:2032` — строка `POST_NOTIFICATIONS` в таблице §8.2 переводится из
    `REMOVED | (decision Q-D3) — no notifications` в `KEEP | (ADR-0010 / plus-subscription-gating) —
    предупреждения об истечении подписки`. Решение Q-D3 «уведомлений нет» этим SPEC-ом отменяется,
    и TDD обязан это отражать — иначе спека будет описывать приложение без уведомлений, которые в
    нём есть.
  - `TDD/MyMoney/MyMoney_TDD.md:2040` и `:2113` — счётчик разрешений 6 → **7** в обоих местах
    (`support-hub-tip-01` перед этим привёл строку 2113 к шести; здесь двигаются обе).
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/notification/MyMoneyNotificationChannels.kt`
    (новый) — регистрация канала «Подписка» при старте приложения (`MyMoneyApp.kt`). Первый канал в
    проекте — заложить фабрику так, чтобы следующие каналы добавлялись без переписывания (G16: сейчас
    в коде нет ни одного `NotificationCompat`).
  - `core/sync/src/main/java/.../notification/EntitlementNotifier.kt` (новый) — интерфейс в
    `:core:domain` + impl здесь (конвенция G5); три уведомления по `EntitlementWarning` из SPEC 01;
    тап ведёт на paywall (deep link на маршрут `PaywallRoute`, добавленный SPEC 04).
  - `core/sync/src/main/java/.../worker/EntitlementWarningWorker.kt` (новый) — ежедневный воркер:
    `EntitlementRepository.refresh()` → `EntitlementStateMachine.warnings(previous, current, now)` →
    отправка. `previous` хранится в DataStore, чтобы `GRACE_ENTERED` сработал ровно один раз на фронте
    перехода (SPEC 01).
  - `core/sync/src/main/java/.../WorkSchedulerImpl.kt` — зарегистрировать воркер в
    `scheduleDailyJobs()` рядом с `RecurringWorker` и `PruneDeletedWorker` (G18). Существующая
    инфраструктура, новый планировщик не заводить.
  - `feature/support/.../paywall/` (создан SPEC 04) — точка запроса `POST_NOTIFICATIONS`: разрешение
    спрашивается **в момент старта триала/покупки**, а не на холодном старте приложения.
  - `app/src/main/res/values/strings.xml` + `values-ru/strings.xml` — заголовки и тексты трёх
    уведомлений и название канала.
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - **Разрешение спрашивается один раз и в осмысленный момент** — при оформлении подписки или старте
    триала. Не на первом запуске и не при открытии Cloud sync.
  - Отказ от разрешения — не ошибка: воркер продолжает считать состояние, предупреждение приходит
    только баннером (SPEC 05). Никаких повторных запросов и никаких блокирующих диалогов.
  - Каждое предупреждение отправляется **один раз на состояние**: дедупликация по паре
    (`warning`, `entitlement.expiresAt`) в DataStore. Ежедневный воркер не должен слать
    «истекает через 3 дня» три дня подряд.
  - Уведомления **только** для платного пути. У ad-Plus предупреждений нет вообще: там нет
    grace-периода, а по истечении 24 часов показывается диалог в приложении (SPEC 06, D6).
  - Воркер не является контролем доступа — он только уведомляет. Отказ сервера работает независимо
    (SPEC 02).
  - Тексты не хардкодятся, RU-перевод обязателен (gate `review-2026-07-05-missing-translation-gate`).
  - Канал создаётся идемпотентно при каждом старте (Android сам игнорирует повтор), но его id и
    важность фиксируются константами — переименование канала после релиза сбрасывает пользовательские
    настройки.
=== END SPEC ===

## Acceptance

```gherkin
Feature: Предупреждения о состоянии подписки
  Пользователь узнаёт о потере доступа заранее, а не постфактум.

  Scenario: Предупреждение за три дня до конца пробного периода
    Given пробный период заканчивается через три дня
    And пользователь разрешил уведомления
    When выполняется ежедневная проверка состояния
    Then приходит уведомление о скором окончании пробного периода
    And тап по нему открывает экран подписки

  Scenario: Предупреждение при входе в льготный период
    Given подписка только что истекла и начался льготный период
    When выполняется ежедневная проверка состояния
    Then приходит уведомление о льготном периоде

  Scenario: Предупреждение за день до окончательного истечения
    Given до конца льготного периода остался один день
    When выполняется ежедневная проверка состояния
    Then приходит уведомление о том, что доступ будет отключён завтра

  Scenario: Одно и то же предупреждение не повторяется
    Given уведомление о скором окончании пробного периода уже отправлено
    When ежедневная проверка выполняется на следующий день, а состояние не изменилось
    Then новое уведомление не отправляется

  Scenario: Пользователь отказался от уведомлений
    Given пользователь не дал разрешение на уведомления
    And подписка вошла в льготный период
    When он открывает экран облачной синхронизации
    Then он видит баннер о льготном периоде
    And повторного запроса разрешения не происходит

  Scenario: Разрешение спрашивается при оформлении подписки
    Given пользователь оформляет годовую подписку с пробным периодом
    When покупка подтверждается
    Then приложение один раз запрашивает разрешение на уведомления

  Scenario: Для Plus за рекламу предупреждений нет
    Given Plus получен за просмотр рекламы
    When выполняется ежедневная проверка состояния
    Then уведомлений о льготном периоде не отправляется
```

## Gap / context

Требование «уведомление плюс баннер» невыполнимо в текущем коде: в проекте нет ни одного
`NotificationCompat`, ни одного канала и ни строчки про `POST_NOTIFICATIONS` (G16). При этом
WorkManager-инфраструктура для периодических задач уже есть и обкатана (G18) — воркер ложится на неё
без нового планировщика.

## Implementation links
- commit: ad6d4ca1, 0017de26, 4e424eee, b977c554, cf1af331, 00aabc2b
- files:
  - TDD/MyMoney/MyMoney_TDD.md
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/kshavrin/mymoney/MainActivity.kt
  - app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt
  - app/src/test/java/com/kshavrin/mymoney/navigation/DestinationsTest.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/EntitlementWarningStore.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/di/DataStoreModule.kt
  - core/datastore/src/test/kotlin/com/kshavrin/mymoney/core/datastore/DataStoreEntitlementWarningStoreTest.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/notification/EntitlementNotifier.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/EntitlementStateMachine.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/EntitlementStateMachineTest.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/WorkSchedulerImpl.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/di/SyncModule.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/notification/EntitlementNotifierImpl.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/notification/MyMoneyNotificationChannels.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/EntitlementWarningWorker.kt
  - core/sync/src/test/java/com/kshavrin/mymoney/core/sync/worker/EntitlementWarningWorkerTest.kt
  - core/sync/src/main/res/drawable/ic_notification_subscription.xml
  - core/sync/src/main/res/values-ru/strings.xml
  - core/sync/src/main/res/values/strings.xml
  - feature/support/build.gradle.kts
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallAction.kt
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallViewModel.kt
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/paywall/PaywallViewModelTest.kt
