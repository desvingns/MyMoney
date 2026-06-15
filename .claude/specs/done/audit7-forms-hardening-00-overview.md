# Эпик: audit7-forms-hardening — импорт, валидация форм, доменные мелочи
Epic: audit7-forms-hardening
Order: 00 of 04 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Средние дефекты данных/валидации из аудита (`docs/audit/2026-06-10-project-audit.md`):
(M12) Monefy-импорт цепляет счёт по имени без проверки валюты — USD-строки прилипают к рублёвому
счёту и баланс суммирует яблоки с метрами; (M14) форма целей превращает «10000,50» в 0 без ошибки;
(L4) каждое редактирование затирает createdAt; (M13) BudgetEvaluator сравнивает порог через Float;
(L1/L6) сидер не атомарен и Splash без обработки ошибок; (L1) MoneyFormatter округляет HALF_EVEN
против доменного HALF_UP; (L2) калькулятор тихо даёт 0 при делении на ноль.

## Заблокированные решения (из grill)

- Паттерн валидации денег — «ошибка вместо нуля» (как AccountEdit), плюс приём запятой как
  десятичного разделителя (RU-ввод).
- **O2 (assumption):** калькулятор при ÷0 сохраняет левый операнд и сбрасывает pending-операцию —
  без краша и без тихого нуля.
- Mismatch валюты на импорте → создаётся новый счёт с суффиксом валюты (« (USD)»).

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit7-forms-hardening-01-monefy-import-currency.md` | audit4-records-02 | data | счёт по имени+валюте; mismatch → новый счёт |
| 02 | `audit7-forms-hardening-02-money-input-validation.md` | audit2-save-integrity-02 | presentation | ошибка вместо нуля + запятая; createdAt сохраняется |
| 03 | `audit7-forms-hardening-03-budget-evaluator-bigdecimal.md` | — | domain | BigDecimal-пороги + guard limit≤0 |
| 04 | `audit7-forms-hardening-04-domain-misc-fixes.md` | — | domain | сидер атомарен + Splash-ошибки; HALF_UP; ÷0 |

## Почему такой порядок

01 ждёт audit4-02 (общий `BackupRepositoryImpl.kt`; дальше его же правит audit9-03). 02 ждёт
audit2-02 (общие GoalEdit/AccountEdit VM). 03/04 независимы.

## Ключевые факты (verified, из grounding)

- G1: Monefy-путь `resolveAccountId(name, currencyId)` НЕ сверяет валюту счёта — `core/database/.../repository/BackupRepositoryImpl.kt:276-294`; MyMoney-путь сверяет — :184-186.
- G2: `GoalEditViewModel.parseMoney = trim().toBigDecimalOrNull() ?: BigDecimal.ZERO` — `feature/dictionaries/.../goals/GoalEditViewModel.kt:316-317`.
- G3: паттерн «ошибка вместо нуля» — `AccountEditViewModel.kt:102-105`.
- G4: затирание `createdAt = now` при каждом редактировании — `GoalEditViewModel.kt:305`, `AccountEditViewModel.kt:121`.
- G5: `BudgetEvaluator`: `pct = spent.toFloat() / limit.toFloat()` — `core/domain/.../usecase/BudgetEvaluator.kt:22-23` (float ~7 значащих цифр; явного guard limit=0 в функции нет).
- G6: `InitialDataSeeder.seedIfNeeded` — check-then-act без withTransaction — :29-33; `SplashViewModel` зовёт сидер без try/catch — `feature/onboarding/.../SplashViewModel.kt:24-26`.
- G7: `MoneyFormatter` — DecimalFormat без явного roundingMode (дефолт HALF_EVEN) — `core/common/.../money/MoneyFormatter.kt:19-23`; домен/калькулятор — HALF_UP.
- G8: `CalculatorEngine`: ÷0 тихо даёт ZERO — `core/common/.../calculator/CalculatorEngine.kt:155-159`; BR-7 (точка раз на операнд) не трогаем.
- G9: запятая отклоняется в CurrencyRate — `CurrencyRateViewModel.kt:75` (toDoubleOrNull).

## Implementation links
- 01 monefy-import-currency — SHIPPED 2026-06-13 (63291630 + f20222e6)
- 02 money-input-validation — SHIPPED 2026-06-13 (15a32338 + 8695932e auto-fix); GoalEdit/AccountEdit/CurrencyRate comma+error-instead-of-zero, createdAt preserved on edit; blank optionals stay 0
- 03 budget-evaluator-bigdecimal — SHIPPED 2026-06-13 (0bb32674 fix + b911d6e2 test); BudgetEvaluator Float-division → BigDecimal compareTo/multiply, guard limit.signum()<=0; :core:domain:test green
- 04 domain-misc-fixes — SHIPPED 2026-06-13 (03943e6e + a7b9573b + 2f5e19a5); atomic seeding via TransactionRunner/Room withTransaction, recoverable Splash seeder failures with retry UI, MoneyFormatter HALF_UP, CalculatorEngine ÷0 preserves left operand
