# AdGateway: ленивое согласие UMP, загрузка и показ rewarded, маппинг состояний
Epic: support-rewarded-ads
Order: 03 of 06
Status: done
Depends-on: 02
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В :core:ads появляется интерфейс AdGateway и его AdMob-реализация. Гейтвей умеет ровно четыре вещи: лениво получить согласие UMP (форма показывается при первом тапе «посмотреть рекламу», не на старте приложения), запросить у сервера одноразовый custom_data-токен, загрузить rewarded-ролик и показать его, вернув однозначный результат. Все отказы превращаются в конечный набор состояний — нет заполнения, нет сети, согласие не дано, показ отменён пользователем, регион не обслуживается — и ни одно из них не является «ошибкой» в смысле краша или пустого экрана. Ни при каком исходе гейтвей не утверждает, что награда начислена: он сообщает только «ролик досмотрен», подтверждение остаётся за сервером.
LAYERS: data
CHANGED_HINT:
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/AdGateway.kt — НОВЫЙ: `suspend fun ensureConsent(activity): ConsentResult`, `suspend fun loadRewarded(): AdLoadResult`, `suspend fun showRewarded(activity): AdShowResult`, `fun availability(): StateFlow<AdAvailability>` (assumption: точная сигнатура)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/AdAvailability.kt — НОВЫЙ: `Available | Loading | NoFill | RegionUnavailable | Offline | ConsentRequired | Disabled` (G26 — регион обязан быть отдельным состоянием, не ошибкой)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/admob/AdMobAdGateway.kt — НОВЫЙ: ленивая `MobileAds.initialize` при первом обращении (НЕ на старте, см. CONSTRAINTS SPEC 02), загрузка `RewardedAd` с `ADMOB_REWARDED_UNIT_ID` из BuildConfig (G41), `ServerSideVerificationOptions` с `customData` из токена
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/admob/AdErrorMapper.kt — НОВЫЙ: `LoadAdError.code` → `AdAvailability`; `ERROR_CODE_NO_FILL` → `NoFill`, `ERROR_CODE_NETWORK_ERROR` → `Offline`, остальное → `NoFill` с записью в Sentry (assumption O2: только ошибки, без аналитики воронки)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/admob/NoFillStreak.kt — НОВЫЙ: сессионный счётчик подряд идущих `NO_FILL`; при достижении порога `availability()` переключается на `RegionUnavailable` (D3 + D5: только в памяти, ничего не персистится)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/consent/UmpConsentGateway.kt — НОВЫЙ: `UserMessagingPlatform` — запрос информации о согласии, показ формы по требованию, различение «согласие дано» / «отказ» / «форма недоступна»; при отказе реклама запрашивается как неперсонализированная (требование эпика: ленивая форма UMP; assumption: путь и имя класса)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/token/RewardTokenSource.kt — НОВЫЙ: POST в Edge Function `create-ad-reward-token`, ответ `{custom_data, expires_at}` (G16/G17), запрос идёт через `SupabaseHttpTransport` с Bearer из `SupabaseSharedAuth.accessToken()` (G19)
  - core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/di/AdsModule.kt — НОВЫЙ: `@Module @InstallIn(SingletonComponent)`, `@Singleton` на гейтвей, диспетчер через `@Named` — конвенция Hilt из `AGENTS.md` («Hilt DI conventions»: `Dispatchers.IO` напрямую в классе запрещён); assumption: путь модуля
  - core/ads/build.gradle.kts — зависимости на `:core:domain`, `:core:common`, `:core:network`, okhttp/serialization (правится также в SPEC 02 — этот второй)
TEST_TYPES: unit
CONSTRAINTS:
  - `core/ads/build.gradle.kts` уже правился в SPEC 02 — этот SPEC идёт строго после.
  - Форма согласия показывается **только** по явному тапу пользователя, никогда на старте приложения
    и никогда при простом открытии экрана поддержки.
  - `custom_data`-токен запрашивается **перед каждой** загрузкой ролика: TTL по умолчанию 600 с
    (G17), просроченный токен = награда не будет начислена сервером.
  - Гейтвей возвращает «ролик досмотрен», а не «награда начислена». Слово «начислено» на этом слое
    запрещено — источник истины только сервер (ADR-0010 D5, G22).
  - Отмена показа на середине — это нормальный исход `AdShowResult.Dismissed(rewardEarned = false)`,
    не ошибка и не повод показать баннер ошибки.
  - Порог серии `NO_FILL` для перехода в `RegionUnavailable` — константа модуля, а не «магическое
    число» в трёх местах; вердикт не персистится (D5).
  - У загрузки обязан быть таймаут: состояние `Loading` не может длиться неограниченно (H4).
  - Тесты — только Fakes, никаких MockK/Mockito (G39). AdMob SDK за интерфейсом, тестируется
    маппер ошибок, счётчик серии `NO_FILL`, поведение при отказе от согласия и логика токена.
  - Реклама доступна только авторизованным: без валидной сессии Supabase токен получить нельзя
    (G16), и гейтвей обязан вернуть отказ, а не пытаться показать ролик.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Гейтвей награждаемой рекламы

  Scenario: Согласие спрашивается лениво
    Given приложение запущено и пользователь открыл раздел поддержки
    Then форма согласия не показана
    When пользователь впервые нажимает «посмотреть рекламу»
    Then показывается форма согласия UMP

  Scenario: Отказ от персонализации не блокирует рекламу
    Given пользователь отказался от персонализированной рекламы
    When запрашивается ролик
    Then запрос уходит как неперсонализированный

  Scenario: Нет заполнения
    Given AdMob отвечает кодом «нет заполнения»
    When гейтвей обновляет доступность
    Then состояние равно «нет доступных роликов»
    And краша и баннера ошибки нет

  Scenario: Серия отказов трактуется как регион
    Given AdMob подряд отвечает «нет заполнения» столько раз, сколько задано порогом
    When гейтвей обновляет доступность
    Then состояние равно «реклама недоступна в регионе»
    And после перезапуска приложения состояние снова начинается с попытки загрузки

  Scenario: Отмена просмотра не даёт награды
    Given ролик показан и пользователь закрыл его до конца
    When показ завершается
    Then результат содержит признак «награда не заработана»

  Scenario: Гейтвей не утверждает начисление
    Given ролик досмотрен до конца
    When показ завершается
    Then результат сообщает только факт досмотра, без статуса начисления

  Scenario: Неавторизованный не получает токен
    Given сессия Supabase отсутствует
    When запрашивается ролик
    Then гейтвей возвращает отказ и ролик не загружается
```

## Gap / context
`:core:ads` по ADR-0010 D7 (G25) — обёртка AdMob SDK, загрузка/показ rewarded и контракт SSV. Без
`custom_data`-токена (G16/G17) сервер не сможет привязать награду к пользователю, а без честного
набора состояний (G26) пользователь в РФ увидит вечный спиннер или тост-ошибку — ровно то, что
ADR-0010 запрещает.

## Implementation links
- commit: `bb71373e`, `082bbbbd`, `ee32a69f`, `ac7055cf`, `7d4189ee`, `4714cd8d`, `e440e9ec`
- files: `core/ads/build.gradle.kts`; `core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/{AdAvailability.kt,AdGateway.kt}`; `core/ads/src/main/kotlin/com/kshavrin/mymoney/core/ads/{admob,consent,di,token}`; `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/{SupabaseHttpTransport.kt,SupabaseSupporterApi.kt}`; focused unit tests under `core/ads/src/test`, `core/network/src/test`, and `app/src/test/java/com/kshavrin/mymoney/CoreAdsWiringContractTest.kt`
