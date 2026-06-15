# Округление вычисляемого баланса в корне (BalanceCalculator)
Epic: money-decimal-precision
Order: 02 of 05
Status: done
Depends-on: money-decimal-precision-01
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Устранить FP-артефакт вычисляемого баланса в корне. `BalanceCalculator` суммирует `SUM(t.amount)` (Double) в SQL и конвертирует результат `BigDecimal.valueOf(double)` без округления — отсюда `-1182337.0799999996`. После SPEC баланс (и любые промежуточные суммы из `CategorySummaryRow.total`) нормализуются политикой 01 `toMoneyScale(currency)` на границе домена → ≤2 знака. Это и есть фикс видимого со скрина значения.
LAYERS: domain
CHANGED_HINT:
  - core/domain/.../usecase/BalanceCalculator.kt:71 — обернуть `BigDecimal.valueOf(total)` в `.toMoneyScale(currency)` (политика 01); проверить ВСЕ места в файле, где SQL-Double превращается в BigDecimal (доходы/расходы/итог), а не только строку 71 (G2, G3)
  - core/domain/.../usecase/BalanceCalculator.kt:27-39 — итоговый `BalanceSnapshot` должен содержать уже округлённые значения (G1)
  - тесты: расширить существующий `BalanceCalculatorTest` кейсами FP-суммы (фикстуры ниже)
TEST_TYPES: unit
CONSTRAINTS:
  - Округлять на границе домена (после чтения SQL-суммы), НЕ внутри SQL — точную валюту знает домен. Само хранилище не трогаем (это делает SPEC-05 миграцией).
  - Нужна валюта счёта для `decimalDigits` — взять из уже доступного в `BalanceCalculator` контекста (счёт/валюта); не добавлять новый репозиторный вызов, если валюта уже есть.
  - `:core:domain` тест-таск = `test` (G14); BigDecimal сравнивать через `compareTo` И проверять scale==2 отдельно (G17).
  - Этого SPEC достаточно, чтобы артефакт со скрина исчез; 03 — дополнительно форматирование/ввод.

### Calculation: rounded derived balance
- Formula: `balance = (Σ signed_amount_i).toMoneyScale(currency)`, где суммирование делает SQL (Double), а `toMoneyScale` = политика SPEC-01 на границе.
- Symbols: `signed_amount_i` = сумма транзакции со знаком (доход +, расход −), хранится Double; `currency.decimalDigits` (Int, ≥0); `balance` = BigDecimal, scale = min(decimalDigits, 2).
- Precision: единичное округление HALF_UP на границе чтения, не на каждой транзакции.
- Edge: нет транзакций → `SUM` NULL → трактовать как 0 → `0.00`; одна транзакция с чистым значением → без изменения величины, только нормализация scale.
- Worked examples (fixtures):
  | транзакции (Double)           | сырой SUM (Double)     | balance (после политики) |
  |-------------------------------|------------------------|--------------------------|
  | [0.1, 0.2]                    | 0.30000000000000004    | 0.30                     |
  | набор, дающий FP-хвост        | -1182337.0799999996    | -1182337.08              |
  | []                            | NULL → 0               | 0.00                     |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Вычисляемый баланс не содержит артефактов плавающей точки

  Scenario: FP-хвост суммы обрезается
    Given счёт с транзакциями, сумма которых в Double даёт -1182337.0799999996
    When BalanceCalculator вычисляет баланс
    Then баланс равен -1182337.08 со scale 2

  Scenario: Классический случай 0.1 + 0.2
    Given счёт с транзакциями 0.10 и 0.20
    When BalanceCalculator вычисляет баланс
    Then баланс равен 0.30, а не 0.30000000000000004

  Scenario: Пустой счёт
    Given счёт без транзакций
    When BalanceCalculator вычисляет баланс
    Then баланс равен 0.00
```

## Gap / context
Корень бага со скрина «Финансовые цели»: `SUM(t.amount)` Double → `BigDecimal.valueOf` без округления показывал `-1182337.0799999996`. Округление политикой 01 на границе домена устраняет это у источника.

## Implementation links
- commit: be8df9b8, 6e101cae
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceCalculator.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceCalculatorTest.kt
