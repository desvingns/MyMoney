# Credit goal — down-payment + loan-term rework — epic overview
Epic: goals-credit-downpayment
Order: 00 of 03
Status: done
Depends-on: financial-goals (done)
Date: 2026-06-07

## Goal
Доработать вариант **«С кредитом»** на экране Финансовых целей (S29, `GoalEditScreen`/`GoalEditViewModel`):
добавить поле **первоначальный взнос**, заменить поле **«Дата погашения»** на **«Срок кредита (лет)»**,
ввести **двухфазную модель** (накопление взноса без процентов → кредит с процентами) и показывать внизу
**read-only** итог: сколько лет на накопление взноса и сколько всего лет до полного погашения (накопление +
срок кредита). Вариант «Без кредита» (savings) не трогаем; логика досрочного погашения (FG-4) из credit-модели
**удаляется** — ежемесячный взнос целиком идёт на накопление взноса, платёж фазы 2 = вычисленный аннуитет.

## Locked decisions
- **D1** — тело кредита `principal = targetAmount − max(downPayment, startingCapital)`: излишек стартового
  капитала сверх взноса дополнительно уменьшает долг; эквити на старте `E = max(downPayment, startingCapital)`.
  При `downPayment = 0` вырождается в текущую `target − startingCapital` (обратная совместимость).
- **D2** — «Срок кредита» вводится в **годах** (целое), внутри `termMonths = years × 12`; заменяет «Дату погашения».
- **D3** — фаза накопления: `accumulationMonths = ceil(max(0, downPayment − startingCapital) / monthlyContribution)`
  (CEILING), **без процентов**; `startingCapital ≥ downPayment → 0 мес`. Переиспользует формулу `GoalSavingsProjector`.
- **D4** — если `monthlyContribution ≤ 0 && startingCapital < downPayment` → статус UNREACHABLE, но **Save не блокируется**.
- **D5** — read-only внизу: (1) лет на накопление; (2) всего лет до погашения = накопление + срок; (3) ежемесячный
  платёж (аннуитет); (4) общая переплата/итог. **FG-4 overpayment удалён.**
- **D6** — фаза кредита: аннуитет `A = principal·i·(1+i)^n / ((1+i)^n − 1)`, при `i == 0 → A = principal/n`;
  `totalPaid = A·n`; `totalInterest = totalPaid − principal`.
- (assumption) **H3** — `underfunded` переосмыслено как информационное «аннуитет > ежемесячного взноса»; не блокирует Save.
- (assumption) **H4** — миграция аддитивная (`MIGRATION_3_4`, REBASED 2026-06-07: v2→3 занят contribution-breakdown):
  legacy-колонка `term_date` остаётся в схеме (nullable, неиспользуемая), domain `Goal` заменяет `termDate` на
  `termMonths` + `downPayment`; реальных данных нет (pet, до релиза).
- (assumption) **H6** — credit-цель валидна при `termYears ≥ 1`; иначе проекция не показывается и Save заблокирован по сроку.
- (assumption) **H7** — `LoanProjection` ужимается: удаляются `finalMonthlyPayment`/`interestSavedVsBaseline`/`overpaymentApplied`,
  добавляются `accumulationMonths: Int?`/`totalMonthsToPayoff: Int?`/`status`.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `goals-credit-downpayment-01-credit-projection-domain.md` | — | domain | Двухфазный расчёт + новые выходы; `principal = target − max(dp, capital)`; FG-4 удалён. |
| 02 | `goals-credit-downpayment-02-goal-persistence-downpayment-term.md` | — | data + domain-model | `Goal.downPayment`, `termDate→termMonths`; колонки + `MIGRATION_3_4` (v3→4, rebased). |
| 03 | `goals-credit-downpayment-03-credit-form-downpayment-term-years.md` | 01, 02 | presentation | Поле взноса, ввод срока в годах, новый read-only; VM+тесты. |

## Why this ordering
Foundation-first: чистый расчёт (01) и персист (02) до UI (03). 01 и 02 независимы (разные файлы, пересечений нет),
но 03 потребляет оба — новый `LoanProjection` (01) и `Goal.downPayment/termMonths` (02), поэтому `Depends-on: 01, 02`.
Пересечений по файлам между SPEC нет → ничего не нужно сериализовать сверх Order.

## Key facts (verified — см. pipeline/grounding.md)
- G1 — UI credit-ветки: `CreditFields` `GoalEditScreen.kt:281`, gate `:251`.
- G2 — `GoalLoanCalculator.invoke(LoanGoalInput): LoanProjection` `GoalLoanCalculator.kt:12`; текущая `principal = target − startingCapital` `:16`.
- G3 — модели `LoanGoalInput`/`LoanProjection` `GoalCalculation.kt:20`/`:28`.
- G4 — `GoalSavingsProjector` ceil-формула `GoalSavingsProjector.kt:13-33` (база для фазы накопления).
- G5/G6 — VM `recompute()` `GoalEditViewModel.kt:138`, события/состояние `:233-272`.
- G7/G8 — `GoalEntity` `:21-22`, domain `Goal` `Goal.kt:7-22`.
- G9 — мапперы `Mappers.kt:99`/`:116`.
- G10 — `MoneyDatabase version = 3` (`MoneyDatabase.kt:41`), `MIGRATION_1_2`/`MIGRATION_2_3` in `Migrations.kt`, регистрация in `DatabaseModule.kt`. (post-breakdown; re-read.)
- G11 — строки `feature/dictionaries/.../values/strings.xml:64-86` + values-ru.
- G12 — тесты: `GoalLoanCalculatorTest`, `GoalEditCreditViewModelTest`, `GoalEditCreditContentTest`; runner компилирует androidTest; money BigDecimal↔Double на границе Room.

## Implementation links
- commit: <hash>
- files: <changed files>
