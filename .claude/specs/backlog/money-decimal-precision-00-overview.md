# Точность денег: максимум 2 знака после запятой — epic overview
Epic: money-decimal-precision
Order: 00 of 05
Status: backlog
Depends-on: —
Date: 2026-06-15

## Goal
Деньги в приложении могут показываться с более чем 2 знаками после запятой — на экране «Финансовые цели» баланс счёта виден как `-1182337.0799999996 ₽`. Корень: вычисляемый баланс суммируется как `SUM(t.amount)` Double прямо в SQL (FP-погрешность) и конвертируется в BigDecimal без округления, а денежные строки экрана целей печатают сырой BigDecimal мимо `MoneyFormatter`. Эпик: (а) единая политика округления денег до ≤2 знаков, (б) округление вычисляемого баланса в корне, (в) форматирование/валидация на формах, (г) округление при импорте, (д) миграция, чистящая уже хранимые «грязные» значения. Вне scope: смена хранения Double→другой тип в Room, округление неденежных полей (ставка %).

## Locked decisions
- Точность = `scale = min(currency.decimalDigits, 2)` — по валюте, но не более 2 знаков (D2).
- Режим = `RoundingMode.HALF_UP` (Kotlin), `ROUND(col, 2)` (SQLite-миграция) — совпадает с существующей арифметикой `Money` (D3).
- Полный фикс, включая корень (BalanceCalculator + экран целей через MoneyFormatter) — без него артефакт со скрина останется (D1).
- Облачный restore = бинарная копия БД; округление хранимых значений делает только миграция `4→5` при открытии восстановленной БД (assumption O1).
- Миграция округляет ТОЛЬКО денежные колонки; `GoalEntity.annualRatePercent` (ставка %) исключена (H3).

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `money-decimal-precision-01-domain-money-scale-policy.md` | — | domain | Каноническая функция округления `min(decimalDigits,2)` HALF_UP в :core:domain |
| 02 | `money-decimal-precision-02-balance-calculator-rounding.md` | 01 | domain | BalanceCalculator округляет SQL-сумму — устраняет FP-артефакт в корне |
| 03 | `money-decimal-precision-03-form-money-display-and-input-scale.md` | 01 | presentation | Экран целей через MoneyFormatter; setScale ввода в Goal/Account/CurrencyRate |
| 04 | `money-decimal-precision-04-import-amount-rounding.md` | 01 | data, domain | Округление сумм при импорте Monefy CSV + MyMoney CSV |
| 05 | `money-decimal-precision-05-migration-round-stored-money.md` | — | data | MIGRATION_4_5: округление хранимых денежных Double-колонок до 2 знаков |

## Why this ordering
01 — фундамент: вводит единое правило округления, на которое опираются 02/03/04 (поэтому первый). 02 — именно он убирает видимый артефакт со скрина (округляет вычисляемый баланс), и его достаточно для исчезновения `-1182337.0799999996`; 03 — защита-в-глубину (форматирование с разрядами + округление ввода). 04 не даёт «грязным» суммам попасть в БД при будущих импортах. 05 чистит уже накопленные значения и не зависит от 01 (SQL, не Kotlin). Файлы между SPEC-ами не пересекаются — параллельных правок одного файла нет; последовательность диктуется только `Depends-on` (01 первым).

## Key facts (verified)
- G1: баланс вычисляемый — `BalanceCalculator.invoke(accountId, period): BalanceSnapshot` — `core/domain/.../usecase/BalanceCalculator.kt:27-39`.
- G2/G3: `SUM(t.amount) AS total` (Double) → `BigDecimal.valueOf(double)` без округления — `core/database/.../dao/TransactionDao.kt:92`, `core/domain/.../usecase/BalanceCalculator.kt:71`.
- G5: `Money.plus/minus/times` уже `setScale(currency.decimalDigits, HALF_UP)` — `core/domain/.../model/Money.kt:12,17,21`.
- G6: `MoneyFormatter.format(...)` ограничивает дробную часть — `core/common/.../money/MoneyFormatter.kt:10-31` (экран целей идёт мимо).
- G7/G8: импорт Monefy/MyMoney CSV пишет суммы без setScale — `core/domain/.../csv/MonefyCsvImportParser.kt:95-108`, `core/database/.../repository/BackupRepositoryImpl.kt:392-494,668-705`.
- G10/G11: `MoneyDatabase.SCHEMA_VERSION = 4` (`MoneyDatabase.kt:67`), миграции в `migration/Migrations.kt:6-42`, регистрация в `DatabaseModule.kt:36`; денежные Double-колонки — `TransactionEntity.amount/toAmount`, `AccountEntity.initialBalance`, `GoalEntity.targetAmount/startingCapital/monthlyContribution/downPayment`, `BudgetEntity.amount`.
- G14/G15/G16: `:core:domain` тест-таск = `test`; миграции тестируются инструментально (`:core:database:connectedDebugAndroidTest`, MigrationTestHelper, реальный Room); ktlintFormat перед коммитом; runner-скрипт `:core:*`/`:feature:*` пропускает — проверять вручную.

## Implementation links
- commit: <hash>
- files:  <changed files>
