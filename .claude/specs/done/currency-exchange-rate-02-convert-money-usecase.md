# ConvertMoneyUseCase — конвертация через базовую валюту EUR
Epic: currency-exchange-rate
Order: 02 of 08
Status: done
Depends-on: 01
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Чистый домен-юзкейс конвертации суммы из валюты `from` в валюту `to` через базовую валюту EUR, используя хранимые курсы `CurrencyRate(EUR→X)`. Это переиспользуемая «функция курс валют» в части математики (без сети и без диалога — их добавляют 03/04/05). Отдаёт результат как `Result`/sealed-статус, а не бросает исключение при отсутствии курса. Также экспонирует расчёт отображаемого кросс-курса `from→to` для диалога.
LAYERS: domain
CHANGED_HINT:
  - core/domain/.../usecase/ConvertMoneyUseCase.kt — НОВЫЙ. `operator fun invoke(amount: BigDecimal, from: Currency, to: Currency, rates: RatesLookup): ConversionResult`; вычисляет `crossRate = rate(EUR→to)/rate(EUR→from)` и `converted = amount × crossRate`, округляет `toMoneyScale(to)` HALF_UP на границе (G18); identity при from==to (G1)
  - core/domain/.../usecase/ConvertMoneyUseCase.kt — `RatesLookup` абстракция чтения курса EUR→X (обёртка над `CurrencyRateRepository.findRate(EUR_id, X_id)` — G4) или передача готовой map; `ConversionResult = Converted(Money, crossRate) | RateMissing(currencyCode)`
  - тесты: новый `ConvertMoneyUseCaseTest` в :core:domain (фикстуры из Calculation-блока), fakes-only (G19)
TEST_TYPES: unit
CONSTRAINTS:
  - Чистая функция: без I/O, без `now()`, deterministic (G19). Деньги — `BigDecimal` (G18). Сравнение BigDecimal в тестах через `compareTo`.
  - Промежуточная конвертация в EUR и деление — с запасом точности (`MathContext.DECIMAL64` / scale ≥10), округление `toMoneyScale(to)` ТОЛЬКО на финальной границе (O2).
  - `:core:domain` тест-таск = `test` (G19). ktlintFormat перед коммитом (G20).
  - Не дублировать логику в `TransferExecutor` (G10): по возможности `TransferExecutor` позже звать этот юзкейс (рефактор не обязателен в этом SPEC, но не плодить второй алгоритм).

### Calculation: конвертация через EUR-базу
- Formula: `crossRate(from→to) = rate(EUR→to) / rate(EUR→from)`; `converted = amount · crossRate`. Частные случаи: `from==EUR` ⇒ `crossRate = rate(EUR→to)`; `to==EUR` ⇒ `crossRate = 1 / rate(EUR→from)`; `from==to` ⇒ identity (`converted = amount`).
- Symbols: `amount` = исходная сумма (BigDecimal, ≥0); `rate(EUR→X)` = единиц X за 1 EUR (BigDecimal, >0); `crossRate` (BigDecimal, точность ≥10); `converted` = результат (BigDecimal, scale = `min(to.decimalDigits, 2)`).
- Precision: `MathContext.DECIMAL64` на промежутке; финальное `setScale(min(to.decimalDigits,2), HALF_UP)` на границе (G18, O2).
- Edge: отсутствует `rate(EUR→from)` или `rate(EUR→to)` ⇒ `RateMissing(код)` (НЕ крэш); `rate ≤ 0` ⇒ невалидно (отвергнуть на уровне репозитория, G4); `amount == 0` ⇒ `0.00` целевой валюты; `from==to` ⇒ `amount` с приведением scale.
- Worked examples (fixtures; курсы из SPEC 01):
  | amount | from | to  | rate(EUR→from) | rate(EUR→to) | expected         |
  |--------|------|-----|----------------|--------------|------------------|
  | 100    | USD  | RUB | 1.146893       | 84.181245    | 7339.94 RUB      |
  | 1000   | RUB  | EUR | 84.181245      | 1 (EUR)      | 11.88 EUR        |
  | 100    | EUR  | KZT | 1 (EUR)        | 559.417885   | 55941.79 KZT     |
  | 50     | USD  | USD | —              | —            | 50.00 USD (identity) |
  | 100    | USD  | RUB | (нет курса)    | —            | RateMissing("USD") |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Конвертация суммы между валютами через EUR

  Scenario: Кросс-курс между двумя не-базовыми валютами
    Given курсы EUR→USD = 1.146893 и EUR→RUB = 84.181245
    When 100 USD конвертируются в RUB
    Then результат равен 7339.94 RUB

  Scenario: Конвертация в базовую валюту
    Given курс EUR→RUB = 84.181245
    When 1000 RUB конвертируются в EUR
    Then результат равен 11.88 EUR

  Scenario: Одинаковая валюта возвращается без изменения
    When 50 USD конвертируются в USD
    Then результат равен 50.00 USD

  Scenario: Отсутствующий курс не роняет приложение
    Given курс для исходной валюты отсутствует
    When запрашивается конвертация
    Then возвращается статус «курс отсутствует» с кодом валюты
```

## Gap / context
Отдельной функции конвертации `(amount, from, to) → amount` нет — логика зашита в `TransferExecutor` (G10). Этот SPEC даёт юнит-тестируемое ядро для всех потребителей курса.

## Implementation links
- commit: d38fc09b (feat ConvertMoneyUseCase) + 2757cb88 (tests)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/ConvertMoneyUseCase.kt (NEW — RatesLookup abstraction + ConversionResult sealed: Converted(Money, crossRate) | RateMissing(code); cross via EUR, MathContext.DECIMAL64 intermediate, final setScale(min(decimalDigits,2), HALF_UP))
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/ConvertMoneyUseCaseTest.kt (NEW — 16 tests, fakes-only, all SPEC worked examples + edges green via :core:domain:test)
- note: pure domain, no consumers yet by design (wired by SPECs 04/06/07). Verifier pass (foundation). EUR is implicit base rate=1 (no stored EUR→EUR row).
