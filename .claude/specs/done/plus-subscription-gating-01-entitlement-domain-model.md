# Доменная модель UserEntitlement и машина состояний Plus
Epic: plus-subscription-gating
Order: 01 of 10
Status: done
Depends-on: —
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: `:core:domain` получает единственный источник истины для гейтинга — `UserEntitlement (Free | Plus)`
  с типом источника (`Subscription | AdReward | Whitelist`), чистую машину состояний
  `None → Trial → Active → Grace → Expired → LocalOnly` и чистый калькулятор порогов предупреждений.
  Никакой сети, UI и Android-зависимостей: слой, который потом одинаково используют `:core:billing`,
  `:feature:support`, `:feature:cloudsync` и `:core:sync`. Тип источника **хранится, но на объём прав
  не влияет вообще** (D6): ad-Plus, платная подписка и whitelist дают одинаковый доступ, включая
  создание воркспейса с участниками. Источник влияет только на **срок** — длительность grace.
LAYERS: domain
CHANGED_HINT:
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/UserEntitlement.kt` (новый) —
    `sealed interface UserEntitlement { data object Free; data class Plus(source, state, startsAt,
    expiresAt, graceEndsAt) }`, `enum class EntitlementSource { SUBSCRIPTION_MONTHLY,
    SUBSCRIPTION_YEARLY, AD_REWARD, WHITELIST }`, `enum class EntitlementState { NONE, TRIAL, ACTIVE,
    GRACE, EXPIRED, LOCAL_ONLY }`. Money-типов здесь нет; время — `java.time.Instant` (G5 — конвенция
    доменного слоя).
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/EntitlementSnapshot.kt` (новый) —
    сырое представление серверной записи (`source`, `startsAt`, `expiresAt: Instant?`, `inTrial`,
    `revokedAt: Instant?`), из которого считается состояние. Поля 1:1 повторяют колонки
    `public.entitlements` (G11), чтобы маппинг был тривиальным.
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/EntitlementStateMachine.kt`
    (новый) — чистая функция `resolve(snapshot: EntitlementSnapshot?, now: Instant): UserEntitlement`
    + `warnings(previous: EntitlementState?, current: UserEntitlement, now: Instant): Set<EntitlementWarning>`.
    См. блок Calculation.
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/EntitlementWarning.kt` (новый) —
    `enum { TRIAL_ENDING_3D, GRACE_ENTERED, EXPIRY_IMMINENT_1D }`.
  - `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/EntitlementRepository.kt`
    (новый) — интерфейс `val entitlement: StateFlow<UserEntitlement>`, `suspend fun refresh(): Result<Unit>`.
    Метода вида `canOwnSharedWorkspaceWithMembers()` здесь **нет**: объём прав от источника не
    зависит (D6), поэтому единственная проверка потребителя — `entitlement is Plus` в состоянии
    `TRIAL`/`ACTIVE`/`GRACE`. Impl приедет в SPEC 03. Интерфейс живёт в
    `:core:domain` именно затем, чтобы `:feature:cloudsync` не зависел от `:core:billing` (G19,
    ADR-0010:124-126).
  - `core/domain/src/test/kotlin/.../usecase/EntitlementStateMachineTest.kt` (новый) — фикстуры из
    блока Calculation, один-в-один.
TEST_TYPES: unit
CONSTRAINTS:
  - **Никакого `Instant.now()` внутри.** `now` — параметр каждой функции (детерминизм, требование
    domain-math). Часы инжектятся вызывающим слоем.
  - `:core:domain` не зависит от Android SDK, Room, Firebase и `:core:billing`. Проверяется
    существующим reviewer'ом слоёв.
  - `LOCAL_ONLY` — **не** состояние подписки, а следствие: машина возвращает его только когда
    вызывающий передал флаг «отцепка уже выполнена» (или killswitch активен). Сама по себе истёкшая
    подписка даёт `EXPIRED`; переход в `LOCAL_ONLY` выполняет SPEC 06.
  - Grace длится 7 дней **только** для `SUBSCRIPTION_*`. Для `AD_REWARD` grace-периода нет вообще
    (`Duration.ZERO`), для `WHITELIST` состояние всегда `ACTIVE` и `expiresAt == null`.
  - **Объём прав не зависит от источника (D6).** Ни одна функция не должна ветвиться по
    `EntitlementSource` при ответе на вопрос «что можно делать» — `AD_REWARD` даёт ровно тот же
    доступ, что `SUBSCRIPTION_*` и `WHITELIST`, включая владение воркспейсом с участниками и выпуск
    приглашений. Источник разрешено читать **только** при вычислении срока (`graceDuration`) и для
    аналитики/UI-текста.
  - Модель обязана допускать v2-сценарий «участник перехватывает оплату» **без миграции**: она уже
    ничего не знает о том, чей это entitlement — плательщик хранится на стороне воркспейса (D5,
    SPEC 02).
  - Клиентская проверка — **только косметика**. Ни одна функция здесь не является контролем доступа;
    контроль — на сервере (SPEC 02).

  ### Calculation: разрешение состояния entitlement и порогов предупреждений
  - Формула состояния (`resolve`), при `snapshot == null || revokedAt != null` → `Free`:
    ```
    graceDuration = if (source in {SUBSCRIPTION_MONTHLY, SUBSCRIPTION_YEARLY}) 7 дней else ZERO
    graceEndsAt   = expiresAt + graceDuration          (null, если expiresAt == null)
    state =
      expiresAt == null              -> ACTIVE          // whitelist, бессрочно
      now <  expiresAt && inTrial    -> TRIAL
      now <  expiresAt && !inTrial   -> ACTIVE
      now >= expiresAt && now < graceEndsAt -> GRACE
      иначе                          -> EXPIRED
    ```
  - Формула предупреждений (`warnings`), все пороги включительные:
    ```
    TRIAL_ENDING_3D   : state == TRIAL && (expiresAt   - now) <= 3 дня  && (expiresAt   - now) > 0
    GRACE_ENTERED     : state == GRACE && previous != GRACE             // фронт перехода
    EXPIRY_IMMINENT_1D: state == GRACE && (graceEndsAt - now) <= 1 день && (graceEndsAt - now) > 0
    ```
  - Символы: `now: Instant` (инжектируется); `startsAt: Instant`; `expiresAt: Instant?`
    (`null` = бессрочно); `inTrial: Boolean`; `revokedAt: Instant?`; `previous: EntitlementState?`
    (`null` при холодном старте — тогда `GRACE_ENTERED` не выдаётся). Все сравнения — по эпохе в
    секундах (`Duration.between`), без плавающей точки и без локальной таймзоны.
  - Точность: время — `Instant` в UTC, разности — `java.time.Duration`; никакого округления, границы
    сравниваются `<=` / `>` строго как в формуле.
  - Edge-кейсы (возвращают значение, **не** бросают):
    `snapshot == null` → `Free`, состояние `NONE`;
    `revokedAt != null` → `Free` независимо от `expiresAt`;
    `expiresAt <= startsAt` (битая запись) → `Free` + событие в Sentry, не крэш;
    несколько активных записей → берётся запись с максимальным `graceEndsAt` (при равенстве —
    `SUBSCRIPTION_*` приоритетнее `AD_REWARD`), источник победителя фиксируется, права те же;
    `now` раньше `startsAt` (перевод часов назад) → состояние считается по формуле как обычно;
    авторитет — серверные метки, клиентские часы не влияют на доступ.
  - Worked examples (фикстуры теста, все метки UTC):
    | # | source | startsAt | expiresAt | inTrial | now | expected state | expected warnings |
    |---|---|---|---|---|---|---|---|
    | 1 | — (snapshot = null) | — | — | — | 2026-08-12T00:00:00Z | `NONE` (Free) | ∅ |
    | 2 | SUBSCRIPTION_YEARLY | 2026-01-01T00:00:00Z | 2027-01-01T00:00:00Z | false | 2026-08-12T00:00:00Z | `ACTIVE` | ∅ |
    | 3 | SUBSCRIPTION_YEARLY | 2026-08-10T12:00:00Z | 2026-08-17T12:00:00Z | true | 2026-08-14T12:00:00Z | `TRIAL` | `TRIAL_ENDING_3D` (ровно 3 дня, граница включительно) |
    | 4 | SUBSCRIPTION_YEARLY | 2026-08-10T12:00:00Z | 2026-08-17T12:00:00Z | true | 2026-08-14T11:59:59Z | `TRIAL` | ∅ (3 дня + 1 с) |
    | 5 | SUBSCRIPTION_MONTHLY | 2026-07-12T00:00:00Z | 2026-08-12T00:00:00Z | false | 2026-08-15T00:00:00Z | `GRACE` (до 2026-08-19T00:00:00Z) | `GRACE_ENTERED` при `previous = ACTIVE` |
    | 6 | SUBSCRIPTION_MONTHLY | 2026-07-12T00:00:00Z | 2026-08-12T00:00:00Z | false | 2026-08-18T00:00:00Z | `GRACE` | `EXPIRY_IMMINENT_1D` (ровно 1 день) |
    | 7 | SUBSCRIPTION_MONTHLY | 2026-07-12T00:00:00Z | 2026-08-12T00:00:00Z | false | 2026-08-19T00:00:00Z | `EXPIRED` | ∅ |
    | 8 | AD_REWARD | 2026-08-11T10:00:00Z | 2026-08-12T10:00:00Z | false | 2026-08-12T10:00:01Z | `EXPIRED` (grace = 0) | ∅ |
    | 9 | WHITELIST | 2026-01-01T00:00:00Z | null | false | 2030-01-01T00:00:00Z | `ACTIVE` | ∅ |
=== END SPEC ===

## Acceptance

```gherkin
Feature: Доменная модель entitlement
  Единственный источник истины для гейтинга Shared-синхронизации.

  Scenario: Пользователь без записи entitlement считается бесплатным
    Given у пользователя нет ни одной записи Plus
    When приложение вычисляет его права
    Then он получает уровень Free и состояние NONE
    And Shared-возможности ему недоступны

  Scenario: Годовая подписка в триале за три дня до конца выдаёт предупреждение
    Given у пользователя годовая подписка с семидневным триалом, который заканчивается через ровно три дня
    When приложение пересчитывает состояние
    Then состояние равно TRIAL
    And выдаётся предупреждение «триал заканчивается»

  Scenario: Просроченная месячная подписка попадает в Grace, а не сразу в Expired
    Given месячная подписка истекла три дня назад
    When приложение пересчитывает состояние
    Then состояние равно GRACE
    And срок Grace истекает через четыре дня

  Scenario: Награда за рекламу не имеет Grace
    Given источник Plus — награда за рекламу, а её срок истёк секунду назад
    When приложение пересчитывает состояние
    Then состояние равно EXPIRED
    And предупреждений о Grace не выдаётся

  Scenario: Ad-Plus даёт тот же объём прав, что платная подписка
    Given источник Plus — награда за рекламу и он активен
    When приложение сравнивает его права с правами активной платной подписки
    Then объём прав совпадает полностью
    And владение воркспейсом с участниками разрешено обоим

  Scenario: Битая серверная запись не роняет приложение
    Given серверная запись entitlement имеет срок окончания раньше срока начала
    When приложение пересчитывает состояние
    Then пользователь получает уровень Free
    And ошибка уходит в систему отчётов о сбоях
```

## Gap / context

Гейтинг нельзя строить на «есть ли покупка в Google Play»: источников Plus три (подписка, награда за
рекламу, whitelist), а прав — один набор. Без общего доменного типа каждый потребитель
(`:feature:cloudsync`, `:feature:support`, `:core:sync`) начал бы считать состояние по-своему, и
пороги предупреждений разъехались бы. Этот SPEC закрывает словарь и арифметику до того, как
появится хоть один вызов сети.

## Implementation links
- commits: e67584db, 05f8e729, 0080a311, 4c8d4214
- files: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/EntitlementSnapshot.kt; core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/EntitlementWarning.kt; core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/UserEntitlement.kt; core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/EntitlementRepository.kt; core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/EntitlementStateMachine.kt; core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/EntitlementStateMachineTest.kt
