# Сетевой фетч курсов (open.er-api.com) + проверка интернета
Epic: currency-exchange-rate
Order: 03 of 08
Status: done
Depends-on: 01
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Реальный сетевой слой для получения курсов. Retrofit-сервис к `open.er-api.com` (`GET /v6/latest/EUR`, без ключа — 1 запрос отдаёт EUR→все), DTO + base URL в `:core:network`. Лёгкий `ConnectivityChecker` (обёртка над `ConnectivityManager`) для fail-fast проверки интернета (в проекте его нет — G15). Data-источник/метод, который тянет ответ, маппит коды→`currencyId` (только активные/известные валюты) и делает `upsert` строк `CurrencyRate(EUR→X)` с `updatedAt=now` (инъект. `Clock`). Здесь — только «получить и сохранить»; решение «когда обновлять» — в SPEC 04.
LAYERS: data, network
CHANGED_HINT:
  - core/network/.../ExchangeRateApi.kt — НОВЫЙ Retrofit-интерфейс: `@GET("v6/latest/EUR") suspend fun latest(): ExchangeRateResponseDto`; DTO `{ result: String, time_last_update_unix: Long, base_code: String, rates: Map<String, Double> }` (kotlinx.serialization, `ignoreUnknownKeys` уже включён — G14)
  - core/network/.../HttpModule.kt:15-45 — задать base URL `https://open.er-api.com/` и предоставить `ExchangeRateApi` через существующий `Retrofit.Builder` (G14)
  - core/network/.../ConnectivityChecker.kt — НОВЫЙ `@Singleton` `fun isOnline(): Boolean` поверх `ConnectivityManager.getNetworkCapabilities` (`NET_CAPABILITY_INTERNET/VALIDATED`); закрывает отсутствие коннективности-хелпера (G15, H5)
  - core/database/.../repository/CurrencyRateRepositoryImpl.kt:16-44 — добавить путь массового сохранения курсов из сети: для каждой пары (code→rate) из ответа найти `currencyId` по `code` (`CurrencyRepository.findByCode` — G5), собрать `CurrencyRate(EUR_id, X_id, rate, updatedAt=Clock.now)` и `upsert` (валидация `rate>0`, `from≠to` уже есть — G4); коды без известной валюты — пропустить
  - DI: `@Named` IO-dispatcher уже конвенция (G4); сетевые ошибки маппить (как cloud-sync маппит в `SyncError` — G16) и не пробрасывать в UI как голый throwable
  - тесты: `ExchangeRateApi` DTO-парсинг (fake JSON ответ open.er-api.com), маппинг code→id + upsert (fakes-only — G19); `ConnectivityChecker` — инструментально/через фейк capabilities
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Реальных сетевых вызовов в тестах НЕ делать — DTO/маппинг на зафиксированном JSON; сеть мокается фейком сервиса (G19, fakes-only).
  - Инъектировать `Clock` для `updatedAt` (G17) — не `Instant.now()` напрямую, иначе staleness в SPEC 04 не протестировать.
  - `open.er-api.com` отдаёт `rates` как «1 EUR = N валюты» — ровно модель D2; сохранять без инверсии. `result != "success"` ⇒ ошибка, не сохранять.
  - **Clash:** SPEC 04 тоже расширяет курсовой data-слой, но через `ResolveRateUseCase`/`CurrencyRateRepository` (домен), а не `...Impl` сетевой путь — следить за слиянием правок `CurrencyRateRepositoryImpl.kt`. ktlintFormat перед коммитом (G20).
  - `:core:network`/`:core:database` тесты runner-скрипт может пропускать — проверять вручную (G19).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Получение курсов из интернета

  Scenario: Успешный ответ сохраняет курсы EUR→все
    Given API возвращает успешный ответ с rates для USD, RUB, RSD, KZT, AED
    When выполняется запрос курсов
    Then для каждой известной валюты сохраняется курс EUR→X с сегодняшней датой обновления

  Scenario: Коды неизвестных валют пропускаются
    Given в ответе есть код валюты, которой нет в приложении
    When ответ обрабатывается
    Then этот код пропускается, остальные курсы сохраняются

  Scenario: Нет интернета
    Given устройство оффлайн
    When проверяется доступность сети
    Then ConnectivityChecker возвращает «оффлайн» и запрос не выполняется

  Scenario: Неуспешный статус ответа
    Given API возвращает result, не равный "success"
    When ответ обрабатывается
    Then курсы не сохраняются и поднимается обрабатываемая ошибка (не крэш)
```

## Gap / context
`:core:network` — пустая заготовка (G14), проверки интернета нет (G15). Этот SPEC даёт реальный фетч курсов и коннективность, на которые опирается оркестрация обновления (SPEC 04).

## Implementation links
- commit: 57a15274 (feat network+refresh) + d075b68f (open seam, superseded) + 35b6d0e0 (extract ConnectivityChecker interface) + 6a6941e8 (manifest xmlns fix) + 4be350ad (tests + build config), pushed to main
- files:
  - core/network/.../ExchangeRateApi.kt (NEW — Retrofit GET v6/latest/EUR + ExchangeRateResponseDto {result, time_last_update_unix, base_code, rates: Map<String,Double>})
  - core/network/.../ConnectivityChecker.kt (now INTERFACE) + AndroidConnectivityChecker.kt (NEW impl) + ConnectivityModule.kt (NEW @Binds)
  - core/network/.../HttpModule.kt (base URL https://open.er-api.com/ + ExchangeRateApi provider)
  - core/network/src/main/AndroidManifest.xml (ACCESS_NETWORK_STATE + xmlns:android)
  - core/network/build.gradle.kts (kotlinx.serialization plugin + test deps)
  - core/database/.../repository/CurrencyRateRepositoryImpl.kt (refreshRatesFromNetwork: fetch→map code→id→upsert EUR→X, Clock.now, skip unknown/EUR-self, result!=success⇒failure) + di/ClockModule.kt (NEW) + build.gradle.kts (+:core:network)
  - core/domain/.../repository/CurrencyRateRepository.kt (+refreshRatesFromNetwork(): Result<Int>)
  - tests: core/network/.../ExchangeRateResponseDtoTest.kt; core/database/.../CurrencyRateRepositoryImplTest.kt (12 fakes-only); core/network/androidTest/.../ConnectivityCheckerTest.kt (2 instrumented, green emulator-5554); all module-local FakeCurrencyRateRepository updated (domain + transactionslist + transaction TransferVM/CurrencyRateVM tests)
- note: ConnectivityChecker had to become an INTERFACE — concrete class resolves ConnectivityManager from Context at construction so an `open class` fake NPEs on null Context. Network manifest was missing xmlns:android (passed JVM, failed processDebugManifest). DI-graph change → clean-assembled + launched + logcat smoke clean (pid alive, no Hilt CreationException). refreshRatesFromNetwork has NO consumer yet — wired by SPEC 04.
