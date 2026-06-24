# Валюты KZT/AED + сид стартовых курсов EUR-базы + миграция
Epic: currency-exchange-rate
Order: 01 of 08
Status: done
Depends-on: —
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Добавить валюты **KZT** (тенге, decimalDigits=2) и **AED** (дирхам ОАЭ, decimalDigits=2) в встроенный список и засеять стартовые курсы относительно базовой валюты **EUR**: строки `CurrencyRate(EUR→X)` для X ∈ {USD, RUB, RSD, KZT, AED} с реальными значениями на 2026-06-20 и `updatedAt` = этой даты. Для НОВЫХ установок — через `InitialDataSeeder`; для СУЩЕСТВУЮЩИХ БД (сидер повторно не запускается) — через Room-миграцию, которая идемпотентно вставляет недостающие валюты и курсы. Это фундамент данных: 02/03/04 опираются на наличие курсов EUR→X.
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/.../seed/InitialDataSeeder.kt:119-142 — в `DEFAULT_CURRENCIES` добавить KZT (symbol «₸», decimalDigits=2) и AED (symbol «AED»/«د.إ», decimalDigits=2); привести `sortOrder`/`isActive` к существующему паттерну (G6)
  - core/domain/.../seed/InitialDataSeeder.kt — засеять `CurrencyRate(fromCurrencyId=EUR, toCurrencyId=X, rate, updatedAt=2026-06-20)` для 5 целевых валют; брать id валют по `code` (EUR — база) через уже доступный путь сидера; курсы сеять ТОЛЬКО если их ещё нет (идемпотентно, как валюты) (G2, G4, G6)
  - core/database/.../migration/Migrations.kt — добавить `MIGRATION_<cur>_<cur+1>`: `INSERT OR IGNORE` валют KZT/AED в таблицу валют + `INSERT OR IGNORE` 5 строк курсов EUR→X (значения ниже, `updated_at` = epoch-ms 2026-06-20); зеркалить стиль соседних миграций (assumption — номер версии)
  - core/database/.../MoneyDatabase.kt:67 — поднять `SCHEMA_VERSION` на 1 (G21)
  - core/database/.../di/DatabaseModule.kt:36 — зарегистрировать новую миграцию в списке (G21)
  - тесты: расширить сид-тест (валют стало 23, есть KZT/AED + 5 курсов EUR→X); инструментальный `MigrationTest` (реальный Room, MigrationTestHelper) на новую миграцию (G19)
TEST_TYPES: unit, instrumented (migration), dao
CONSTRAINTS:
  - **Контракт-тесты на число валют (G20):** найти и обновить любые ассерты с захардкоженным «21» (теперь 23). `:app:ktlintCheck` — формат перед коммитом.
  - Курсы храним как `EUR→X` (1 EUR = rate·X), все `fromCurrencyId` = EUR (D2). Уникальный индекс (from,to) (G3) — миграция не должна создавать дубли (`INSERT OR IGNORE`).
  - `updatedAt` сид-курсов = фиксированная дата снимка 2026-06-20 (НЕ дата установки/миграции) — чтобы при первом онлайн-обращении после установки авто-обновление (SPEC 04) сработало по устареванию (D11).
  - Миграция инструментально тестируется на устройстве (`:core:database:connectedDebugAndroidTest`); `:core:domain` тест-таск = `test` (G19).
  - decimalDigits KZT/AED = 2 (ISO 4217 minor unit) — влияет на `toMoneyScale` (G18).

### Reference: стартовые курсы (EUR-база, снимок open.er-api.com на 2026-06-20)
| from | to  | rate (1 EUR =) |
|------|-----|----------------|
| EUR  | USD | 1.146893       |
| EUR  | RUB | 84.181245      |
| EUR  | RSD | 117.390388     |
| EUR  | KZT | 559.417885     |
| EUR  | AED | 4.211961       |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Базовые валюты и стартовые курсы

  Scenario: Новая установка содержит KZT и AED
    Given свежая установка приложения
    When открывается список валют
    Then в нём присутствуют тенге (KZT) и дирхам ОАЭ (AED)
    And всего активных валют 23

  Scenario: Стартовые курсы засеяны относительно EUR
    Given свежая установка приложения
    When запрашивается курс EUR→RUB
    Then возвращается 84.181245 с датой обновления 2026-06-20

  Scenario: Существующая база получает новые валюты при обновлении
    Given база версии до этого эпика без KZT/AED и без курсов
    When приложение обновляется и применяется миграция
    Then KZT, AED и 5 стартовых курсов EUR→X появляются без дублей

  Scenario: Повторная миграция не создаёт дубликаты
    Given база, где KZT/AED и курсы EUR→X уже есть
    When миграция применяется повторно
    Then новых строк валют и курсов не добавляется
```

## Gap / context
Сейчас 21 валюта без KZT/AED, таблица курсов пуста до ручного ввода. Эпику нужны 6 валют и стартовые курсы относительно EUR как стартовая точка для конвертации и авто-обновления.

## Implementation links
- commit: 4207aa92 (feat seed) + 92c39c30 (fix const) + 7f7c0c4e (fix idempotent currency insert) + c59d0c4c (tests), pushed to main
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/seed/InitialDataSeeder.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/migration/Migrations.kt (MIGRATION_5_6)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/MoneyDatabase.kt (SCHEMA_VERSION 5→6)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/di/DatabaseModule.kt (register MIGRATION_5_6)
  - core/database/schemas/com.kshavrin.mymoney.core.database.MoneyDatabase/6.json (new schema export)
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/seed/InitialDataSeederTest.kt (21→23 + 8 new tests)
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MoneyDatabaseMigration5To6Test.kt (new, 11 tests, green on emulator-5554)
- note: currency.code has NO unique index → currency rows inserted via `INSERT … SELECT … WHERE NOT EXISTS`; rate rows idempotent via currency_rate unique (from,to). updatedAt seed = 1781913600000L (UTC midnight 2026-06-20).
