# Credit goal projection — two-phase domain calculator
Epic: goals-credit-downpayment
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Переписать `GoalLoanCalculator` под двухфазную credit-модель. **Фаза 1** — накопление
первоначального взноса БЕЗ процентов; **фаза 2** — аннуитетный кредит С процентами. Тело кредита
`principal = targetAmount − max(downPayment, startingCapital)` (D1). Новые выходы: `accumulationMonths`
(сколько месяцев копить взнос), `totalMonthsToPayoff` (накопление + срок). Логика досрочного погашения
(FG-4 overpayment-цикл) полностью удаляется — ежемесячный взнос идёт только на фазу 1, платёж фазы 2 =
вычисленный аннуитет. Чистая, юнит-тестируемая функция; `today`/clock не нужен (всё в месяцах).
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/GoalCalculation.kt — (G3)
    в `LoanGoalInput` добавить `downPayment: BigDecimal` и заменить `termMonths: Int` оставить как есть
    (presentation передаёт `years × 12`). В `LoanProjection` (G3): ДОБАВИТЬ `accumulationMonths: Int?`,
    `totalMonthsToPayoff: Int?`, `status: GoalStatus`; УДАЛИТЬ `finalMonthlyPayment`,
    `interestSavedVsBaseline`, `overpaymentApplied` (H7). Переиспользовать существующий `enum GoalStatus`
    (`ON_TRACK`/`ALREADY_ACHIEVED`/`UNREACHABLE`) из этого же файла.
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculator.kt — (G2)
    переписать `invoke`: phase-1 `needed = max(0, downPayment − startingCapital)`,
    `accumulationMonths = if (needed == 0) 0 else if (monthlyContribution ≤ 0) null else ceil(needed / monthly)`
    (RoundingMode.CEILING — зеркало `GoalSavingsProjector` G4); `equity = max(downPayment, startingCapital)`;
    `principal = max(0, target − equity)`; phase-2 аннуитет `A` (как сейчас, но БЕЗ overpayment-цикла
    `:71-105`): `i = rate/100/12`, `A = principal·i·(1+i)^n / ((1+i)^n − 1)`, при `i==0 → A = principal/n`,
    при `principal==0 → A=0`; `totalPaid = A·n`, `totalInterest = totalPaid − principal`;
    `totalMonthsToPayoff = accumulationMonths?.plus(termMonths)`; `underfunded = A > monthlyContribution`
    (информационно, H3); `status = UNREACHABLE` если `accumulationMonths == null`, иначе `ON_TRACK`.
    Сохранить `require(termMonths >= 1)`. MathContext.DECIMAL64 внутри, HALF_UP 2dp на границе.
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculatorTest.kt — (G12)
    переписать под новый контракт: 4 worked-example фикстуры из Calculation-блока ниже + проверки крайних
    случаев (capital ≥ downPayment → accumulationMonths=0 и surplus уменьшает principal; monthly=0 &
    capital<downPayment → status=UNREACHABLE, accumulationMonths/totalMonthsToPayoff=null; principal=0 → A=0).
TEST_TYPES: unit
CONSTRAINTS:
  - Money — `BigDecimal` (никогда Double в domain). Месяцы — CEILING. Платёж/итоги — HALF_UP 2dp на границе.
  - Удаление полей `LoanProjection` (H7) ломает компиляцию VM/Screen/их тестов — это зона SPEC 03 (Depends-on),
    НЕ трогать здесь; этот SPEC оставляет :core:domain зелёным (`:core:domain:testDebugUnitTest`).
  - Чистая функция: без `now()`, без I/O. `:feature:* → :feature:*` не появляется. Без комментариев кроме WHY.
  - Calculation-блок ниже — единственный источник математики; фикстуры переносятся в тест дословно.
  - Calculation block — Read prompt rubrics/domain-math.
=== END SPEC ===

### Calculation: CreditGoalProjection
- **Symbols / units:**
  - `P_t` = targetAmount (BigDecimal, ≥0) — цена покупки/цель
  - `C` = startingCapital (BigDecimal, ≥0)
  - `D` = downPayment (BigDecimal, ≥0) — первоначальный взнос
  - `M` = monthlyContribution (BigDecimal, ≥0) — ежемесячный взнос (фаза накопления)
  - `r` = annualRatePercent (BigDecimal, ≥0); `i = r / 100 / 12` (месячная ставка)
  - `Y` = срок в годах (Int, ≥1, вводится в UI); `n = Y × 12` = termMonths (Int, ≥1)
  - Выходы: `a` = accumulationMonths (Int?), `A` = baseMonthlyPayment (BigDecimal, 2dp),
    `principal`/`totalInterest`/`totalPaid` (BigDecimal, 2dp), `totalMonthsToPayoff` (Int?), `status`, `underfunded`
- **Phase 1 (накопление, без %):**
  `needed = max(0, D − C)`;
  `a = if (needed == 0) 0 else if (M ≤ 0) null else ceil(needed / M)` (RoundingMode.CEILING)
- **Equity / principal:** `E = max(D, C)`; `principal = max(0, P_t − E)`
- **Phase 2 (кредит, с %):**
  `if principal == 0 → A = 0, totalPaid = 0, totalInterest = 0`
  `else if i == 0 → A = principal / n`
  `else → A = principal·i·(1+i)^n / ((1+i)^n − 1)`
  `totalPaid = A·n`; `totalInterest = totalPaid − principal`
- **Aggregate:** `totalMonthsToPayoff = if (a == null) null else a + n`;
  `status = if (a == null) UNREACHABLE else ON_TRACK`; `underfunded = A > M`
- **Precision:** MathContext.DECIMAL64 внутри; `A`/деньги округляются HALF_UP до 2dp на границе; месяцы CEILING.
- **Edge:** `D=0 → needed=0, a=0, principal = target − C` (как раньше); `C ≥ target → principal=0, A=0`;
  `M ≤ 0 & C < D → UNREACHABLE` (a=null, Save не блокируется — D4); `n < 1 → reject (require)`.
- **Worked examples (фикстуры — перенести в тест дословно):**

| # | P_t | C | D | M | r% | Y(n) | a | principal | A | totalPaid | totalInterest | totalMonths | status |
|---|-----|---|---|---|----|------|---|-----------|---|-----------|---------------|-------------|--------|
| 1 | 2 000 000 | 0 | 500 000 | 50 000 | 0 | 10 (120) | 10 | 1 500 000.00 | 12 500.00 | 1 500 000.00 | 0.00 | 130 | ON_TRACK |
| 2 | 3 000 000 | 1 200 000 | 1 000 000 | 30 000 | 0 | 5 (60) | 0 | 1 800 000.00 | 30 000.00 | 1 800 000.00 | 0.00 | 60 | ON_TRACK |
| 3 | 4 000 000 | 100 000 | 800 000 | 0 | 10 | 15 (180) | null | 3 200 000.00 | (инф.) | — | — | null | UNREACHABLE |
| 4 | 1 000 000 | 0 | 0 | 100 000 | 12 | 1 (12) | 0 | 1 000 000.00 | 88 848.79 | 1 066 185.48 | 66 185.48 | 12 | ON_TRACK |

  - #1 проверяет накопление (10 мес) + i=0 аннуитет.
  - #2 проверяет D1: `C(1.2М) > D(1.0М)` → `a=0` и эквити=1.2М уменьшает principal (surplus учтён через `max`).
  - #3 проверяет D4: накопление недостижимо (M=0), но это не reject — `a=null`, `totalMonths=null`, principal всё равно определён.
  - #4 — каноничный аннуитет (1М, 12%/год, 12 мес → 88 848.79), плюс обратная совместимость `D=0`.

## Acceptance
```gherkin
Feature: Двухфазный расчёт кредитной цели

  Scenario: Накопление взноса, затем кредит без процентов (фикстура 1)
    Given target=2 000 000, startingCapital=0, downPayment=500 000, monthly=50 000, rate=0%, termYears=10
    When проекция рассчитана
    Then accumulationMonths = 10
    And principal = 1 500 000.00
    And baseMonthlyPayment = 12 500.00
    And totalMonthsToPayoff = 130
    And status = ON_TRACK

  Scenario: Капитал больше взноса — излишек уменьшает тело кредита (D1, фикстура 2)
    Given target=3 000 000, startingCapital=1 200 000, downPayment=1 000 000, monthly=30 000, rate=0%, termYears=5
    When проекция рассчитана
    Then accumulationMonths = 0
    And principal = 1 800 000.00

  Scenario: Взнос недостижим без ежемесячного пополнения — но расчёт не падает (D4, фикстура 3)
    Given startingCapital < downPayment and monthly = 0
    When проекция рассчитана
    Then status = UNREACHABLE
    And accumulationMonths is null
    And totalMonthsToPayoff is null

  Scenario: Аннуитет с процентами (фикстура 4)
    Given target=1 000 000, startingCapital=0, downPayment=0, monthly=100 000, rate=12%, termYears=1
    When проекция рассчитана
    Then baseMonthlyPayment = 88 848.79
    And totalInterest = 66 185.48

  Scenario: Нулевой/отрицательный срок отвергается
    Given termMonths < 1
    When вызывается калькулятор
    Then бросается IllegalArgumentException
```

## Gap / context
Текущий `GoalLoanCalculator` (G2) знает только одну фазу (`principal = target − startingCapital`) и несёт
FG-4 overpayment-цикл. Этот SPEC вводит фазу накопления взноса и новые выходы в годах, удаляя overpayment.
Чистый расчёт — фундамент для persistence (02) и UI (03).

## Implementation links
- commit: ccd1d600 (two-phase calculator) + 4a2263c5 (derive loan totals from rounded monthly payment)
- files: core/domain/.../model/GoalCalculation.kt, core/domain/.../usecase/GoalLoanCalculator.kt, core/domain/.../usecase/GoalLoanCalculatorTest.kt
- note: 127 :core:domain unit tests green. LoanProjection lost finalMonthlyPayment/interestSavedVsBaseline/overpaymentApplied/monthsToPayoff; gained accumulationMonths/totalMonthsToPayoff/status. Rounding: monthly payment HALF_UP 2dp first, totals derived from it.
