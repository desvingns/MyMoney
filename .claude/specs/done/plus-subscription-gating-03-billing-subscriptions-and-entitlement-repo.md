# Подписки Plus в :core:billing и server-authoritative EntitlementRepository
Epic: plus-subscription-gating
Order: 03 of 10
Status: done
Depends-on: plus-subscription-gating-01, plus-subscription-gating-02, support-hub-tip-02 и support-hub-tip-03 (внешние)
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Расширить `:core:billing` и доменный `BillingGateway` (создаются эпиком `support-hub-tip`,
  SPEC-и 02 и 03, D2) на подписки: `plus_monthly`
  €1.99/мес и `plus_yearly` €12.99/год с 7-дневным триалом **только на годовом base-plan**; и
  реализовать `EntitlementRepository` из SPEC 01 так, чтобы источником истины был **сервер**
  (`get_my_entitlement()` из SPEC 02), а Play Billing использовался только для запуска покупки и
  восстановления. Добавить периодическую ре-валидацию на существующей WorkManager-инфраструктуре.
LAYERS: data
CHANGED_HINT:
  - `core/billing/src/main/java/com/kshavrin/mymoney/core/billing/PlusSku.kt` (новый) — каталог:
    `plus_monthly` / `plus_yearly` (подписки) рядом с уже существующими consumables из
    `support-hub-tip-03`. Идентификаторы — из ADR-0010 D3 (G19, `ADR-0010:70-80`).
  - `core/domain/.../billing/BillingGateway.kt` (заведён `support-hub-tip-02`) — расширить контракт
    подписочными операциями (`querySubscriptions`, `launchSubscriptionFlow`, `acknowledge`).
    Второй параллельный интерфейс не заводить.
  - `core/billing/src/main/java/com/kshavrin/mymoney/core/billing/PlusSubscriptionClient.kt` (новый) —
    `queryProductDetails` для `SUBS`, выбор base-plan/offer, `launchBillingFlow`, `acknowledgePurchase`,
    `queryPurchases` при старте (восстановление). Подписки **не consume** — в отличие от «кофе».
    Флаг сборки `BILLING_ENABLED` вводит `support-hub-tip-03`; переиспользовать его, не дублировать.
  - `core/billing/src/main/java/com/kshavrin/mymoney/core/billing/BillingAvailability.kt` (новый) —
    различает «биллинг недоступен в регионе» (`BILLING_UNAVAILABLE` / `SERVICE_UNAVAILABLE`) и
    обычную ошибку. Нужно для честной деградации paywall в России (ADR-0010 «Regional constraints»,
    :128-149). *(assumption — точный код ответа уточняется на устройстве)*
  - `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseEntitlementApi.kt`
    (новый) — вызов RPC `get_my_entitlement()` поверх существующего транспорта
    (`SupabaseHttpTransport.kt`, `SupabaseSharedWorkspaceRpc.kt` — паттерн для копирования).
    Маппинг ответа в `EntitlementSnapshot` (SPEC 01).
  - `core/billing/src/main/java/com/kshavrin/mymoney/core/billing/EntitlementRepositoryImpl.kt`
    (новый) — `@Singleton`, реализует `EntitlementRepository` (G5, `core/domain/.../repository/`):
    держит `StateFlow<UserEntitlement>`, пересчитывает через `EntitlementStateMachine.resolve(snapshot, now)`,
    кеширует последний снапшот в DataStore, `refresh()` идёт в `SupabaseEntitlementApi`.
    Методов, ветвящихся по `EntitlementSource`, репозиторий не имеет — объём прав от источника не
    зависит (D6, SPEC 01).
  - `core/datastore/src/main/java/.../EntitlementCache.kt` (новый или расширение существующего
    DataStore-хранилища) — последний известный снапшот + метка `lastValidatedAt`, чтобы приложение
    стартовало без сети с последним известным состоянием.
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/EntitlementRefreshWorker.kt`
    (новый) — периодическая ре-валидация; регистрируется в `WorkSchedulerImpl.scheduleDailyJobs()`
    рядом с `RecurringWorker`/`PruneDeletedWorker` (G18).
  - `app/src/main/AndroidManifest.xml` — вернуть `<uses-permission android:name="com.android.vending.BILLING"/>`
    (ADR-0010 Consequences :177-179). **Клэш с SPEC 07** (`POST_NOTIFICATIONS`) — этот SPEC идёт первым.
  - `gradle/libs.versions.toml` — `billing-ktx` (если ещё не добавлен эпиком `support-hub-tip`;
    сейчас Google Play Billing в каталоге отсутствует — G17).
  - `settings.gradle.kts:32-51` — `include(":core:billing")`, если модуль ещё не заведён эпиком
    `support-hub-tip` (сейчас его в списке нет — G17).
TEST_TYPES: unit, dao
CONSTRAINTS:
  - **Клиентская проверка — только косметика.** `EntitlementRepositoryImpl` не имеет права выдать
    Plus по локальному факту покупки: Plus появляется, только когда его подтвердил сервер
    (`get_my_entitlement()`). Локальный `queryPurchases` используется лишь для того, чтобы показать
    «покупка обрабатывается» и дёрнуть `refresh()`.
  - **Никакой локальной чеканки триала.** Признак `inTrial` приходит с сервера (Play RTDN, G12).
  - Работа с сетью — через `@Named` диспетчер, никогда не `Dispatchers.IO` напрямую (конвенция проекта).
  - Кеш в DataStore — оптимизация UX, а не источник прав: сервер уже отказывает истёкшему (SPEC 02),
    поэтому просроченный кеш не открывает доступ, а только показывает устаревший баннер.
  - Тесты — **только фейки** (реализующие интерфейс, на `StateFlow`), никаких MockK/Mockito.
  - Ручные пререквизиты Play Console (два base-plan, оффер «7 дней бесплатно» только на годовом,
    RTDN Pub/Sub-топик) — вне репозитория, выполняются до релиза. *(assumption, O2)*
  - Эпик зависит от `support-hub-tip`: если модуля `:core:billing` ещё нет (G17,
    `settings.gradle.kts:32-51`), SPEC не стартует — сначала выполняется тот эпик.
=== END SPEC ===

## Acceptance

```gherkin
Feature: Покупка подписки Plus
  Право даёт сервер; Google Play только принимает деньги.

  Scenario: Покупка годовой подписки с триалом
    Given пользователь без Plus открыл экран подписки
    When он оформляет годовую подписку
    Then Google Play предлагает семь дней бесплатно
    And после подтверждения покупки приложение перечитывает право у сервера
    And пользователь получает уровень Plus в состоянии «триал»

  Scenario: Месячная подписка идёт без триала
    Given пользователь без Plus открыл экран подписки
    When он оформляет месячную подписку
    Then бесплатный период не предлагается
    And после подтверждения право становится активным

  Scenario: Покупка прошла, но сервер её ещё не подтвердил
    Given покупка в Google Play завершилась успешно
    And сервер ещё не получил уведомление о ней
    When приложение пересчитывает права
    Then пользователь остаётся без Plus
    And ему показывается статус «покупка обрабатывается», а не ошибка

  Scenario: Восстановление подписки на новом устройстве
    Given у пользователя есть активная подписка, купленная на другом устройстве
    When он входит в тот же аккаунт и приложение перечитывает права
    Then он получает уровень Plus без повторной оплаты

  Scenario: Регион без биллинга
    Given Google Play Billing недоступен в регионе пользователя
    When он открывает экран подписки
    Then приложение сообщает, что покупки в его регионе недоступны
    And кнопка покупки не показывается как рабочая

  Scenario: Работа без сети
    Given приложение стартует без интернета
    And последнее известное состояние — активная подписка
    Then показывается последнее известное состояние
    And попытка воспользоваться сервером всё равно проверяется сервером
```

## Gap / context

Google Play Billing в проекте отсутствует полностью (G17), а entitlement обязан быть
server-authoritative: локальный факт покупки форгуем, а открывает он реальную серверную ёмкость
(та же логика, по которой ADR-0010 D5 отверг клиентский счётчик наград). Этот SPEC связывает три
уже готовые части — доменную модель (01), серверный RPC (02) и биллинг-модуль из `support-hub-tip` —
в один поток данных.

## Implementation links
- commit: 977778d8, 50bdfbdb
- files: :core:billing Play Billing subscription gateway and server-authoritative entitlement repository; DataStore cache; Supabase RPC client; WorkManager refresh; startup restore wiring; stale billing contract fixtures
