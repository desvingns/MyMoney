# ResolveRateUseCase — устаревание + ленивый рефреш + фолбэк
Epic: currency-exchange-rate
Order: 04 of 08
Status: done
Depends-on: 02, 03
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: «Мозг» курса. Юзкейс, который по нужной паре (from, to) отдаёт актуальный курс ВМЕСТЕ с метаданными для диалога: дата последнего обновления, значение курса на эту дату, флаг устаревания, признак «обновлено из сети сейчас». Логика D11: если дата хранимого курса (через `EUR→from`/`EUR→to`) ≠ сегодня (локальная дата, инъект. `Clock`) И `ConnectivityChecker.isOnline()` → выполнить 1 сетевой запрос (SPEC 03), обновить EUR→все, затем вернуть свежий курс; иначе вернуть последний сохранённый курс с его датой. Кросс-курс считает через `ConvertMoneyUseCase`/общую формулу (SPEC 02). Никогда не блокирует: оффлайн/ошибка сети → отдаёт последний сохранённый (или сигнал «курса нет» для ручного ввода).
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/.../usecase/ResolveRateUseCase.kt — НОВЫЙ. `suspend fun invoke(from: Currency, to: Currency): RateInfo`; `RateInfo { crossRate: BigDecimal?, lastUpdated: LocalDate?, refreshedNow: Boolean, stale: Boolean, missing: Boolean }`; staleness = `lastUpdated != today(clock)` (G17); рефреш через сетевой путь SPEC 03; кросс-курс через SPEC 02
  - core/domain/.../repository/CurrencyRateRepository.kt:6-17 — при необходимости добавить `refreshFromNetwork(): Result<Unit>` (массовый upsert из 03) и/или `observeRate`/`findRate` уже хватает (G4); решение о расширении интерфейса — здесь
  - core/database/.../repository/CurrencyRateRepositoryImpl.kt:16-44 — если интерфейс расширяется `refreshFromNetwork()`, реализовать его здесь поверх сетевого пути SPEC 03 (G4) — **тот же файл, что правит SPEC 03 → строго после 03, единая правка**
  - core/domain/.../usecase/ResolveRateUseCase.kt — «дата последнего обновления» берётся из `CurrencyRate.updatedAt` (G2), приводится к `LocalDate` системной зоны для сравнения с «сегодня»
  - тесты: `ResolveRateUseCaseTest` (fakes-only — G19): свежий курс (без сети), устаревший+онлайн (рефреш вызван, дата=сегодня), устаревший+оффлайн (старый курс + stale=true), курса нет (missing=true). `Clock.fixed()` для детерминизма (G17)
TEST_TYPES: unit
CONSTRAINTS:
  - **Ручная правка курса в диалоге (D5) сюда НЕ пишется** — она разовая и живёт только в UI-операции (SPEC 05/06/07). Этот юзкейс меняет хранимый курс ТОЛЬКО через сетевой рефреш, не через пользовательский ввод.
  - `today` = локальная дата через инъект. `Clock` (G17) — НЕ `LocalDate.now()` напрямую. Без I/O помимо вызова репозитория/сети.
  - Один сетевой запрос обновляет EUR→все (D11) — не дёргать по паре. Оффлайн/ошибка не бросают наружу — возвращают последний сохранённый + `stale`/`missing`.
  - **Clash:** если интерфейс расширяется `refreshFromNetwork()`, то `CurrencyRateRepositoryImpl.kt` — **тот же файл, что правит SPEC 03** → выполнять строго ПОСЛЕ 03, без параллельной правки Impl. `:core:domain` тест-таск = `test`; ktlintFormat (G19, G20).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Разрешение курса с авто-обновлением

  Scenario: Свежий курс отдаётся без обращения к сети
    Given сохранённый курс с датой обновления = сегодня
    When запрашивается курс from→to
    Then возвращается сохранённый курс, без сетевого запроса

  Scenario: Устаревший курс при наличии интернета обновляется
    Given сохранённый курс с датой обновления ≠ сегодня
    And устройство онлайн
    When запрашивается курс from→to
    Then выполняется один сетевой запрос, курс обновляется и дата становится сегодняшней

  Scenario: Устаревший курс без интернета не блокирует
    Given сохранённый курс с датой ≠ сегодня
    And устройство оффлайн
    When запрашивается курс from→to
    Then возвращается последний сохранённый курс с его датой и флагом «устарел»

  Scenario: Курс отсутствует
    Given для пары нет сохранённого курса
    When запрашивается курс from→to
    Then возвращается признак «курса нет» для ручного ввода
```

## Gap / context
Нет места, которое решает «когда тянуть курс из сети и что показать оффлайн». Этот SPEC соединяет конвертацию (02) и фетч (03) в единый источник для диалога курса (05).

## Implementation links
- commit: 7877dbff (feat ResolveRateUseCase) + 506b41c0 (tests), pushed to main
- files:
  - core/domain/.../usecase/ResolveRateUseCase.kt (NEW — suspend invoke(from,to): RateInfo(crossRate, lastUpdated: LocalDate?, refreshedNow, stale, missing); @Inject(CurrencyRateRepository, CurrencyRepository, ConvertMoneyUseCase, Clock, ZoneId=systemDefault); stale = stored updatedAt→LocalDate(zone) != today(Clock); on stale calls refreshRatesFromNetwork() and uses Result.isSuccess; reuses ConvertMoneyUseCase for crossRate; EUR implicit base rate=1; never throws)
  - core/domain/.../usecase/ResolveRateUseCaseTest.kt (NEW — fakes-only, Clock.fixed; fresh/stale-online/stale-offline/missing/cross-rate)
- note: did NOT inject ConnectivityChecker into domain (would leak :core:network android module into :core:domain) — online fail-fast lives in the data-layer Impl (SPEC 03), use case interprets refreshRatesFromNetwork() Result. No interface/Impl change needed (03 already added refreshRatesFromNetwork). NO consumer yet — wired by SPEC 05/06/07. ⚠ test gotcha: `BigDecimal(double)` CONSTRUCTOR captures binary noise (84.18124500000000254…) → use `BigDecimal.valueOf(double)` for exact-equality expectations matching production.
