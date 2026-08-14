# Состояние награды: доменный контракт, чтение RPC и ожидание подтверждения
Epic: support-rewarded-ads
Order: 04 of 06
Status: done
Depends-on: 01, 02
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Появляется доменный контракт «сколько просмотров засчитано и активен ли Plus»: интерфейс AdRewardRepository в :core:domain, неизменяемая модель AdRewardState и реализация, читающая единственную серверную RPC get_ad_reward_state(). Плюс сценарий ожидания: после досмотра ролика клиент опрашивает сервер с нарастающей паузой примерно до 30 секунд и возвращает один из трёх исходов — прогресс вырос, Plus выдан, подтверждения пока нет. Клиент ничего не пересчитывает сам и ни при каком исходе не выдаёт entitlement.
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/ads/AdRewardState.kt — НОВЫЙ: `progress: Int`, `required: Int`, `frozen: Boolean`, `frozenReason: FrozenReason?`, `plusActive: Boolean`, `plusProvider: String?`, `plusExpiresAt: Instant?` — зеркалит контракт RPC из SPEC 01
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/ads/AdRewardRepository.kt — НОВЫЙ: `suspend fun refresh(): Result<AdRewardState>`, `val state: StateFlow<AdRewardState?>`, `suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome` — интерфейс живёт в `:core:domain` по ADR-0010 D7 (G25), доменные операции возвращают `kotlin.Result` по конвенции `AGENTS.md` («Architecture pattern»); assumption: точная сигнатура
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/data/SupabaseAdRewardRepository.kt — НОВЫЙ: POST на `rpc/get_ad_reward_state` через `SupabaseHttpTransport` (G19: OkHttp, не Retrofit) с Bearer из `SupabaseSharedAuth.accessToken()` (G19)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/data/AdRewardBackoff.kt — НОВЫЙ: расписание поллинга (нарастающая пауза, общий бюджет ~30 с), выделено отдельно, чтобы тестироваться без сети (D4)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/di/AdsModule.kt — привязка репозитория, `@Singleton`, `@Named`-диспетчер (правится также в SPEC 03 — этот после)
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeAdRewardRepository.kt — НОВЫЙ: Fake на `StateFlow`, по образцу `FakeCurrencyRepository` (G39: `core/testing/src/main/kotlin/.../fake/FakeCurrencyRepository.kt:9-45`)
TEST_TYPES: unit
CONSTRAINTS:
  - Контракт RPC приходит из SPEC 01 — этот SPEC не имеет права вычислять прогресс или предикат
    заморозки на клиенте (D7). Любая арифметика «сколько осталось» — только форматирование того,
    что вернул сервер.
  - Исход «подтверждения пока нет» — легальный и обязателен: SSV асинхронный, колбэк мог не дойти.
    Возвращать «начислено» без подтверждения сервером запрещено (ADR-0010 D5, G22).
  - Ошибка сети при поллинге не отменяет уже досмотренный ролик: состояние остаётся «ждём
    подтверждения», а не «награда потеряна».
  - Поллинг ограничен по времени и обязан прекращаться при уходе экрана — никаких вечных корутин.
  - Только Fakes, никаких моков (G39). Тесты на: разбор ответа RPC, расписание backoff,
    три исхода ожидания, поведение при 401 (сессия истекла).
  - Ответ RPC разбирается строго; неизвестное значение `frozenReason` не должно ронять разбор —
    неизвестная причина трактуется как «заморожено без деталей».
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Состояние награды на клиенте

  Scenario: Прогресс приходит с сервера
    Given сервер сообщает, что засчитано 3 из 5
    When клиент обновляет состояние
    Then состояние содержит прогресс 3 и порог 5

  Scenario: Подтверждение дошло во время ожидания
    Given ролик досмотрен и прогресс был 2
    When сервер во время ожидания сообщает прогресс 3
    Then исход ожидания — «прогресс вырос»

  Scenario: Пятый ролик выдал Plus
    Given ролик досмотрен и прогресс был 4
    When сервер во время ожидания сообщает, что Plus активен
    Then исход ожидания — «Plus выдан»

  Scenario: Подтверждение не дошло
    Given ролик досмотрен
    When бюджет ожидания исчерпан, а состояние не изменилось
    Then исход ожидания — «подтверждения пока нет»
    And клиент не считает награду начисленной

  Scenario: Заморозка приходит с сервера
    Given у пользователя активен платный Plus
    When клиент обновляет состояние
    Then состояние помечено как замороженное с указанной причиной
    And клиент не пытается пересчитать прогресс самостоятельно

  Scenario: Обрыв сети не теряет ролик
    Given ролик досмотрен и сеть пропала во время ожидания
    When ожидание завершается
    Then исход — «подтверждения пока нет», а не ошибка потери награды
```

## Gap / context
До SPEC 01 в схеме нет ни RPC, ни view для прогресса (G18) — клиенту нечего показывать. Этот SPEC
даёт единственный путь чтения и явно фиксирует, что «начислено» может сказать только сервер, чем
закрывает требование «UI не должен врать, пока SSV не подтвердил».

## Implementation links
- commit: ceaf30c7, 453b01c1, 4e4bf17d, 32d43396, e1b9711b, 726734e4
- files: domain ad-reward contract/state, server-authoritative Supabase repository and bounded polling, auth-session lifecycle invalidation, Hilt wiring, transport error normalization, fakes, and unit/wiring regressions
