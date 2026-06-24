# Курс валют — epic overview
Epic: currency-exchange-rate
Order: 00 of 08
Status: done (epic complete 2026-06-21)
Depends-on: —
Date: 2026-06-20

## Goal
Полноценная работа с курсами валют для 6 основных валют (EUR, USD, RUB, RSD, KZT, AED). Курсы хранятся относительно **базовой валюты EUR**, тянутся из бесплатного API `open.er-api.com` (без ключа, 1 запрос = EUR→все) с ленивым авто-обновлением по устареванию даты, а при каждом обращении к курсу показывается диалог (дата последнего обновления + курс + поле ручного разового ввода). Два потребителя: **кросс-валютные переводы между счетами** (уже работают — здесь доработка) и **«Все счета» → свести к одной валюте ИЛИ показать раздельно** (новое). Вне scope: смена базовой валюты, исторические курсы/графики, мультивалютные одиночные транзакции, парсинг выдачи Google, фоновый периодический Worker.

## Locked decisions
- **D1** Источник — бесплатный FX-API `open.er-api.com` (`GET /v6/latest/EUR`, без ключа). НЕ frankfurter.app (ЕЦБ не отдаёт RUB/RSD/KZT/AED).
- **D2** Модель — базовая валюта **EUR**; храним строки `CurrencyRate(EUR→X)`; кросс-курс `from→to = rate(EUR→to)/rate(EUR→from)` (переиспользуем существующую модель пар).
- **D3** Диалог курса показывается **каждый раз** при обращении к курсу: «дата последнего обновления» + «курс на эту дату» + «поле ручного ввода», предзаполнен, подтверждение/правка в 1 тап.
- **D4** Скоуп потребителей — только кросс-валютный перевод + «Все счета» → свести к одной.
- **D5** Ручная правка в диалоге — **разовая** (только текущая операция, в БД не пишется) → не двигает `updatedAt`, авто-обновление не ломается.
- **D6** «Все счета» раздельно — секции-карточки баланса стопкой на каждую валюту (донат опц.).
- **D7** «Свести к одной» — целевую валюту спрашивать каждый раз (не запоминать).
- **D8** В шторке — одна запись «Все счета» (по всем валютам); старую привязку `AllAccounts(currency)` убрать.
- **D9** Свёртка нескольких валют — один диалог-список курсов, подтверждение разом.
- **D10** Сид — +KZT +AED; стартовые курсы `EUR→{USD,RUB,RSD,KZT,AED}` реальными значениями на 2026-06-20; прочие валюты — лениво из API / ручной разовый.
- **D11** Авто-обновление — ленивое при обращении: дата ≠ сегодня (инъект. `Clock`) + интернет → 1 запросом обновить EUR→все, `updatedAt=сегодня`; оффлайн/ошибка → последний курс + его дата + поле разовой правки, не блокировать.
- `(assumption)` **O1** фоновый периодический Worker отложен (v1 — только лениво).
- `(assumption)` **O3** точный composable правой шторки разработчику подтвердить (grounding указал `DashboardState`/`DashboardViewModel`; имя `LeftDrawerContent` могло быть исторической путаницей лево/право).

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `currency-exchange-rate-01-currency-seed-rates-migration.md` | — | domain, data | +KZT +AED; сид стартовых курсов EUR→5 (значения 2026-06-20); Room-миграция для существующих БД |
| 02 | `currency-exchange-rate-02-convert-money-usecase.md` | 01 | domain | `ConvertMoneyUseCase(amount,from,to)` + кросс-курс через EUR; округление *(domain_math)* |
| 03 | `currency-exchange-rate-03-exchange-rate-network-fetch.md` | 01 | data, network | Retrofit-сервис open.er-api.com + DTO + base URL; `ConnectivityChecker`; маппинг API→upsert курсов |
| 04 | `currency-exchange-rate-04-rate-resolver-staleness.md` | 02, 03 | domain, data | `ResolveRateUseCase`: устаревание (`Clock`) + ленивый рефреш + фолбэк оффлайн; курс + мета |
| 05 | `currency-exchange-rate-05-rate-confirm-dialog.md` | 04 | presentation | Общий компонент диалога курса (single + list), правка разовая |
| 06 | `currency-exchange-rate-06-transfer-rate-dialog-integration.md` | 05 | presentation | Every-time диалог в кросс-валютном переводе |
| 07 | `currency-exchange-rate-07-all-accounts-convert-to-one.md` | 05, 02 | presentation, domain | Одна «Все счета» → диалог конвертация/раздельно; путь «свести к одной» |
| 08 | `currency-exchange-rate-08-all-accounts-separately-cards.md` | 07 | presentation, domain | Режим «раздельно»: карточки баланса по валютам стопкой |

## Why this ordering
Foundation-first. **01** — данные (валюты + сид-курсы + миграция), без них нечего конвертировать. **02** — чистая математика конвертации (юнит-тестируемая), фундамент для всех потребителей. **03** — сеть (фетч + коннективность), независима от 02. **04** — «мозг» (устаревание + рефреш + фолбэк), соединяет 02 и 03, отдаёт курс + мету в UI. **05** — общий компонент диалога (в `:core:designsystem`, т.к. используют и `:feature:transaction`, и `:feature:dashboard` — нельзя feature→feature). **06** интегрирует диалог в переводы. **07**→**08** — «Все счета»: сначала запись в шторке + диалог + путь «свести к одной» (07), затем режим «раздельно» (08). **Clash:** 07 и 08 правят одни файлы дашборда (`DashboardViewModel`/`State`) → строго последовательно (08 после 07). 03 и 04 могут править один файл `CurrencyRateRepositoryImpl.kt` (если 04 расширяет интерфейс `refreshFromNetwork()`) → 04 строго после 03, без параллельной правки Impl. 01 — единственный, кто меняет схему БД/миграции.

## Key facts (verified)
- G1: `Currency(id, code[A-Z]{3}, symbol, name, decimalDigits 0..8, isActive, sortOrder)` — `core/domain/.../model/Currency.kt:3-11`.
- G2: `CurrencyRate(id, fromCurrencyId, toCurrencyId, rate:Double>0, updatedAt:Instant)` — `core/domain/.../model/CurrencyRate.kt:5-11`. `updatedAt` уже есть.
- G3: `CurrencyRateEntity` — `rate:Double`, `updatedAt:Long` epoch-ms; FK на currency; **unique index (from_currency_id, to_currency_id)** — `core/database/.../entity/CurrencyRateEntity.kt:9-33`.
- G4: `CurrencyRateRepository.findRate(from,to):CurrencyRate?`, `observeAll()`, `upsert(rate):Long`, `deleteById` — `core/domain/.../repository/CurrencyRateRepository.kt:6-17`; impl `@Singleton`+`@IoDispatcher`, валидирует `rate>0`, `from≠to` — `CurrencyRateRepositoryImpl.kt:16-44`.
- G5: `CurrencyRepository.observeActive/All`, `findById/ByCode`, `upsert`, `upsertAll`, `setActive` — `core/domain/.../repository/CurrencyRepository.kt:6-23`; impl валидирует code `^[A-Z]{3}$`, symbol 1-4, decimalDigits 0-8.
- G6: `InitialDataSeeder` сеет 21 валюту (вкл. RSD); **KZT/AED нет**; курсы в сид не входят — `core/domain/.../seed/InitialDataSeeder.kt:119-142`.
- G7: экран+VM ручного ввода курса — `feature/transaction/.../rate/CurrencyRateViewModel.kt:78-128` (норм. «,»→«.», scale 2 HALF_UP, upsert c `Instant.now()`, `NavigateBackWithRate`).
- G8/G9/G10: перевод полностью реализован — `feature/transaction/.../transfer/TransferViewModel.kt:37`; `Transaction{toAccountId, toAmount, exchangeRate}` — `Transaction.kt:6-21`; `Account.currencyId` — `Account.kt:9`; `TransferExecutor.execute()` при разных валютах `findRate`→`toAmount=amount×rate`, иначе `RateMissing`→`NavigateToRateSetup`→NavHost CURRENCY_RATE — `TransferExecutor.kt:42-52`, `MyMoneyNavHost.kt:187-191`.
- G11: баланс вычисляемый; `BalanceCalculator.forAccounts()` суммирует только однотипные по валюте (`require()`) — `BalanceCalculator.kt:42-51`; `BalanceSnapshot{income,expense,net:Money}` одна валюта — `BalanceSnapshot.kt:3-8`.
- G12: «Все счета» = `DashboardSelection.AllAccounts(currency)`, фильтр `accounts.filter{currencyId==currency.id}` → `forAccounts()` — `DashboardState.kt:54-62`, `DashboardViewModel.kt:446-451`. Мультивалютной свёртки нет.
- G13: диалоги с дашборда — one-shot Actions (`SharedFlow replay=0`) → роуты NavHost (пример `NavigateToRateSetup`).
- G14: `:core:network/HttpModule` — Retrofit.Builder + OkHttp + kotlinx Json (`ignoreUnknownKeys`); **base URL/сервиса/вызовов нет** — `HttpModule.kt:15-45`.
- G15: **ConnectivityManager/NetworkMonitor отсутствует**; единственный механизм — WorkManager `Constraints.NetworkType` — `SyncSchedulerImpl.kt:29-34`.
- G16: WorkManager зрелый (`@HiltWorker`, `Result.retry`/MAX_RETRIES=3) — `WorkSchedulerImpl.kt:28-59`, `SyncWorker.kt:16-48`; облачный sync делает реальный HTTP через Dropbox/GDrive SDK.
- G17: время — `Instant`(UTC) домен / `Long` Room; паттерн инъекции `Clock` есть (`NormalizeLegacyUtcMidnightUseCase.kt:15-18`, дефолт `Clock.systemUTC()`), но не везде — проверку «дата ≠ сегодня» делать через инъект. `Clock`.
- G18: деньги — `BigDecimal` домен / `Double` Room; `BigDecimal.toMoneyScale(currency)` = scale min(decimalDigits,2) HALF_UP (эпик money-decimal-precision, 2026-06-15).
- G19: unit — fakes-only; instrumentation — реальный Room на устройстве; тест-таск `:core:domain` = `test`; runner-скрипт `:core:*`/`:feature:*` пропускает → проверять вручную.
- G20: ktlint-гейт `:app:ktlintCheck`; возможны контракт-тесты с захардкоженным числом валют/сид-данных → **+KZT/AED может уронить счётчик** — найти и обновить.
- G21 (migration infra, источник — verified facts эпика money-decimal-precision): `MoneyDatabase.SCHEMA_VERSION` — `MoneyDatabase.kt:67`; миграции — `migration/Migrations.kt`; регистрация — `DatabaseModule.kt:36`. **Текущую версию схемы разработчику считать с `SCHEMA_VERSION` и взять следующую** (последняя известная — `MIGRATION_4_5` из money-decimal-precision; вероятно head=5 → новая `MIGRATION_5_6`, число подтвердить) `(assumption)`.

## Implementation links
- commit: epic complete — all 8 SPECs in done/ (01..08), shipped & pushed to main 2026-06-21
- files:  see each currency-exchange-rate-0N-*.md in .claude/specs/done/
