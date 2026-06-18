# MIGRATION_4_5: округление уже хранимых денежных значений до 2 знаков
Epic: money-decimal-precision
Order: 05 of 05
Status: done
Depends-on: —
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Одноразовая Room-миграция, чистящая уже накопленные «грязные» денежные значения (длинные FP-хвосты, импорт до фикса) в существующих БД. Добавить `MIGRATION_4_5`, которая `UPDATE ... SET col = ROUND(col, 2)` по всем ДЕНЕЖНЫМ Double-колонкам; bump `SCHEMA_VERSION` 4→5; зарегистрировать в `DatabaseModule`. Это первая миграция проекта, трансформирующая данные, а не схему. Также чистит старые бинарные бэкапы при их restore (миграция прогоняется при открытии БД версии 4).
LAYERS: data
CHANGED_HINT:
  - core/database/.../migration/Migrations.kt — добавить `val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db) { ... ROUND(col,2) ... } }` рядом с `MIGRATION_3_4` (G6/G10)
  - core/database/.../MoneyDatabase.kt:67 — `SCHEMA_VERSION = 5` и bump `@Database(version = 5)` (G10)
  - core/database/.../di/DatabaseModule.kt:36 — добавить `MIGRATION_4_5` в `.addMigrations(...)` (G10)
  - тесты: инструментальный `MigrationTest` (MigrationTestHelper) — БД v4 со значением -1182337.0799999996 → после миграции -1182337.08
TEST_TYPES: instrumented
CONSTRAINTS:
  - Округлять ТОЛЬКО денежные колонки: `transactions.amount`, `transactions.toAmount`, `accounts.initialBalance` (initial_balance), `goals.targetAmount`, `goals.startingCapital`, `goals.monthlyContribution`, `goals.downPayment`, `budgets.amount` — точные имена столбцов взять из @Entity (G11). НЕ трогать `goals.annualRatePercent` (ставка %, H3), `term_months`, и любые неденежные/идентификаторные поля.
  - SQLite `ROUND(x, 2)` округляет «от нуля» (≈ HALF_UP, D3), но подвержена двоичному представлению Double; основная задача — срезать длинные FP-хвосты (`.0799999996` → `.08`), что ROUND делает корректно. Редкие точные половинки 3-го знака — приемлемое отклонение; зафиксировать в тесте репрезентативные значения.
  - Миграция ОБЯЗАТЕЛЬНА (не `fallbackToDestructiveMigration`) — иначе данные пользователя сотрутся. Тестировать инструментально на устройстве реальным Room через MigrationTestHelper (G15), не моками. Пустые/NULL-колонки переживают `ROUND(NULL)` (NULL остаётся NULL).
  - :core:database тесты runner пропускает (G14) — гонять `:core:database:connectedDebugAndroidTest` на Pixel_5_API_34 вручную; ktlintFormat (G16).

### Calculation: stored-value rounding (SQL)
- Formula: для каждой денежной колонки `col`: `UPDATE <table> SET <col> = ROUND(<col>, 2) WHERE <col> IS NOT NULL`.
- Symbols: `col` = хранимое значение Double; результат — Double с ≤2 значащими знаками дробной части (по правилу SQLite ROUND, округление от нуля).
- Precision: округление до 2 знаков; режим — нативный SQLite ROUND (away-from-zero ≈ HALF_UP).
- Edge: `col IS NULL` → не трогаем (остаётся NULL); `col` уже ≤2 знака → значение не меняется; отрицательные → округляются по модулю от нуля.
- Worked examples (fixtures, проверяются инструментально):
  | до миграции            | ROUND(.,2)     |
  |------------------------|----------------|
  | -1182337.0799999996    | -1182337.08    |
  | 0.30000000000000004    | 0.30           |
  | 100.50                 | 100.50         |
  | NULL                   | NULL           |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Существующие хранимые суммы округляются миграцией до 2 знаков

  Scenario: Длинный FP-хвост в транзакции
    Given БД схемы 4 с transactions.amount = -1182337.0799999996
    When применяется MIGRATION_4_5 (открытие БД версии 5)
    Then transactions.amount = -1182337.08

  Scenario: Денежные поля цели округлены
    Given БД схемы 4 с goals.startingCapital = 0.30000000000000004
    When применяется MIGRATION_4_5
    Then goals.startingCapital = 0.30

  Scenario: Ставка не округляется
    Given БД схемы 4 с goals.annualRatePercent = 12.345
    When применяется MIGRATION_4_5
    Then goals.annualRatePercent осталось 12.345
```

## Gap / context
Прод-фиксы (02/03/04) чистят новые значения, но в БД пользователя уже лежат «грязные» суммы. Эта миграция нормализует существующие данные одноразово и закрывает путь восстановления старых бинарных бэкапов.

## Implementation links
- commit: af59efc1, 70f4da8b, c673669f
- files:  core/database/schemas/com.kshavrin.mymoney.core.database.MoneyDatabase/5.json; core/database/src/main/java/com/kshavrin/mymoney/core/database/MoneyDatabase.kt; core/database/src/main/java/com/kshavrin/mymoney/core/database/di/DatabaseModule.kt; core/database/src/main/java/com/kshavrin/mymoney/core/database/migration/Migrations.kt; core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MoneyDatabaseMigration4To5Test.kt
