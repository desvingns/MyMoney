# BudgetEvaluator: пороги на BigDecimal без Float-потерь
Epic: audit7-forms-hardening
Order: 03 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Статусы бюджета (Over/Warning/Ok) вычисляются точным BigDecimal-сравнением вместо Float-деления (~7 значащих цифр): на бюджете 10 000 000.00 ₽ потраченные 9 999 999.50 ошибочно классифицировались как Over. Добавляется явный guard limit ≤ 0.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BudgetEvaluator.kt:22-23 — заменить `spent.toFloat() / limit.toFloat()` на compareTo-логику по формуле ниже; guard limit.signum() <= 0 (G5)
  - core/domain/src/test/.../BudgetEvaluatorTest.kt — дополнить фикстурами из Calculation-блока (существующие кейсы не ломать)
TEST_TYPES: unit
CONSTRAINTS:
  - Семантика статусов и текущий warn-порог (константа в коде) НЕ меняются — меняется только арифметика.
  - Никаких divide() без необходимости — сравнение через умножение (исключает ArithmeticException на непериодических дробях).

### Calculation: статус бюджета
- Formula: `Over ⇔ spent ≥ limit`; `Warning ⇔ spent × 100 ≥ limit × W` (иначе Ok),
  где W — существующий warn-порог в процентах (текущая константа кода, например 80).
- Symbols: spent — BigDecimal ≥ 0 (потрачено за период); limit — BigDecimal (лимит бюджета);
  W — Int, проценты warn-порога; статус — enum Over/Warning/Ok.
- Precision: только compareTo/multiply — деления нет; scale операндов не важен (compareTo
  scale-нечувствителен).
- Edge: limit ≤ 0 → бюджет не оценивается (текущий контракт «нет бюджета», не исключение);
  spent = 0 → Ok; spent = limit → Over (граница включена — текущая семантика pct ≥ 1.0 сохранена).
- Worked examples (fixtures, W = 80):
  | spent          | limit          | статус   |
  |----------------|----------------|----------|
  | 9 999 999.50   | 10 000 000.00  | Warning  |
  | 10 000 000.00  | 10 000 000.00  | Over     |
  | 7 999 999.99   | 10 000 000.00  | Ok       |
  | 8 000 000.00   | 10 000 000.00  | Warning  |
  | любое          | 0              | не оценивается |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Точная оценка бюджета

  Scenario: Крупный бюджет на границе
    Given лимит бюджета 10000000.00 и потрачено 9999999.50
    Then статус бюджета — Warning, не Over

  Scenario: Ровно лимит
    Given потрачено ровно столько, сколько лимит
    Then статус — Over

  Scenario: Нулевой лимит
    Given лимит бюджета 0
    Then бюджет не оценивается и алерт не эмитится
```

## Gap / context
Баг M13 аудита (G5): float-деление теряет точность на суммах ≥10M — реалистичных для RUB.
Уточнение к warn-порогу: фактическое значение W взять из текущей константы кода при реализации.

## Implementation links
- commit: 0bb32674 (fix) + b911d6e2 (test: zero-spent boundary)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BudgetEvaluator.kt — Float division → BigDecimal compareTo/multiply; guard limit.signum() <= 0
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BudgetEvaluatorTest.kt — worked-example precision fixtures + zero_spent_is_under
- verified: :core:domain:test BUILD SUCCESSFUL (runner script false-negative on absent :app:detekt/jacoco)
