# Единая политика округления денег до ≤2 знаков (:core:domain)
Epic: money-decimal-precision
Order: 01 of 05
Status: done
Depends-on: —
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Ввести каноническую чистую функцию округления денег в :core:domain: `scale = min(currency.decimalDigits, 2)`, `RoundingMode.HALF_UP`. Это единый источник правды для всех денежных путей (баланс, формы, импорт) — SPEC-ы 02/03/04 её переиспользуют. Поведение совпадает с уже существующей арифметикой `Money` (которая округляет по `currency.decimalDigits`), но добавляет жёсткий потолок ≤2 знака.
LAYERS: domain
CHANGED_HINT:
  - core/domain/.../model/Money.kt:12,17,21 — рядом с существующими `setScale(currency.decimalDigits, HALF_UP)` добавить top-level/extension `fun BigDecimal.toMoneyScale(currency: Currency): BigDecimal = setScale(minOf(currency.decimalDigits, 2), RoundingMode.HALF_UP)` (зеркало паттерна G5)
  - core/domain/.../model/Money.kt — опционально применить `toMoneyScale` в самом `Money` (конструктор/арифметика), чтобы потолок ≤2 действовал и там; не менять публичную сигнатуру `Money`
  - тесты: новый `MoneyScaleTest` в :core:domain (фикстуры ниже)
TEST_TYPES: unit
CONSTRAINTS:
  - Чистая функция, без I/O и без `now()`. Деньги остаются `BigDecimal` в домене (конвенция CLAUDE.md, G4).
  - `:core:domain` тест-таск = `test`, НЕ `testDebugUnitTest` (G14). Сравнение BigDecimal в тестах через `compareTo`, не `equals` (G17).
  - ktlintFormat перед коммитом (G16). Фундамент эпика — ничего из 02/03/04 не должно дублировать правило округления, только звать эту функцию.
  - Calculation-блок ниже — источник фикстур.

### Calculation: money scale policy
- Formula: `scale = min(currency.decimalDigits, 2)`; `result = value.setScale(scale, RoundingMode.HALF_UP)`
- Symbols: `value` = исходная сумма (BigDecimal, любой знак/scale); `currency.decimalDigits` = знаков у валюты (Int, ≥0); `result` = округлённая сумма (BigDecimal, scale = вычисленный).
- Precision: округление только на границе, режим HALF_UP. Внутренних промежуточных вычислений нет (функция-обёртка).
- Edge: `decimalDigits = 0` → scale 0 (целое); `value` уже с меньшим числом знаков → дополняется нулями до scale (`setScale` нормализует); отрицательные суммы округляются так же (HALF_UP по модулю величины).
- Worked examples (fixtures):
  | value                 | decimalDigits | scale | expected result   |
  |-----------------------|---------------|-------|-------------------|
  | -1182337.0799999996   | 2             | 2     | -1182337.08       |
  | 10.005                | 2             | 2     | 10.01             |
  | 12.3456               | 3             | 2     | 12.35             |
  | 10.5                  | 0 (JPY)       | 0     | 11                |
  | 0                     | 2             | 2     | 0.00              |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Деньги округляются до максимум 2 знаков по политике валюты

  Scenario: FP-хвост обрезается до 2 знаков
    Given валюта с decimalDigits = 2
    When сумма -1182337.0799999996 нормализуется политикой
    Then результат равен -1182337.08

  Scenario: Потолок 2 знака при валюте с 3 знаками
    Given валюта с decimalDigits = 3
    When сумма 12.3456 нормализуется политикой
    Then результат равен 12.35

  Scenario: Валюта без дробной части
    Given валюта с decimalDigits = 0
    When сумма 10.5 нормализуется политикой
    Then результат равен 11
```

## Gap / context
Нет единой точки округления денег: `Money` округляет по `decimalDigits` без потолка, а вычисляемый баланс и парсеры импорта идут мимо округления вовсе. Этот SPEC даёт одну функцию, на которую опираются остальные.

## Implementation links
- commit: e98744a4, d5b75d85
- files:  core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/Money.kt; core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/model/MoneyScaleTest.kt; core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/model/MoneyTest.kt
