# Goal persistence — down payment + loan term (months)
Epic: goals-credit-downpayment
Order: 02 of 03
Status: done
Depends-on: —
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Расширить хранение цели под новую credit-модель: добавить **первоначальный взнос** (`downPayment`) и
заменить **дату погашения** на **срок кредита в месяцах** (`termMonths`). В domain-модели `Goal` `termDate`
заменяется на `termMonths: Int?` + добавляется `downPayment: BigDecimal?`. В Room добавляются колонки
`down_payment` и `term_months`; legacy-колонка `term_date` остаётся в схеме (nullable, неиспользуемая) —
аддитивная миграция без разрушительного rebuild (H4). Версия БД 3 → 4 + `MIGRATION_3_4`.
**REBASE-NOTE (2026-06-07):** schema уже на v3 (`MIGRATION_2_3` занят эпиком contribution-breakdown — он
добавил колонку `contribution_breakdown` + domain `Goal.contributionBreakdown`). Поэтому эта миграция —
**3 → 4 (`MIGRATION_3_4`)**, а НЕ 2→3. Line-refs (G7/G8/G9/G10) могли сместиться после breakdown-эпика —
читать файлы заново, а не по номерам строк.
LAYERS: data, domain (model)
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/Goal.kt — (G8) в `data class Goal`
    УДАЛИТЬ `termDate: LocalDate?`, ДОБАВИТЬ `downPayment: BigDecimal?` и `termMonths: Int?`. (Импорт
    `java.time.LocalDate` убрать, если больше не используется.)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/entity/GoalEntity.kt — (G7) ДОБАВИТЬ
    `@ColumnInfo(name = "down_payment") val downPayment: Double?` и `@ColumnInfo(name = "term_months")
    val termMonths: Int?`. Колонку `term_date` (`:22`) ОСТАВИТЬ как есть (legacy, nullable) — схема v3 её сохраняет.
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/mapper/Mappers.kt — (G9) в `GoalEntity.toDomain()`
    (`:99`) маппить `downPayment = downPayment?.toBigDecimal()`, `termMonths = termMonths`; убрать чтение `termDate`.
    В `Goal.toEntity()` (`:116`) писать `downPayment = downPayment?.toDouble()`, `termMonths = termMonths`,
    `termDate = null` (legacy-колонка больше не заполняется).
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/migration/Migrations.kt — ДОБАВИТЬ (G10)
    `val MIGRATION_3_4 = object : Migration(3, 4) { … }` с
    `ALTER TABLE goal ADD COLUMN down_payment REAL` и `ALTER TABLE goal ADD COLUMN term_months INTEGER`
    (обе nullable, без NOT NULL/DEFAULT — Room допускает nullable добавление). `MIGRATION_2_3`
    (`contribution_breakdown`, эпик breakdown) уже существует — НЕ трогать его.
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/MoneyDatabase.kt — (G10) `version = 3` → `version = 4`.
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/di/DatabaseModule.kt — (G10)
    `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` + импорт `MIGRATION_3_4`.
  - core/database/src/test/.../ (G12, G13) — DAO/round-trip тест на `down_payment`/`term_months` сохраняются и
    читаются; миграционный тест 3→4 (room-testing, instrumented) — открыть БД v3, применить MIGRATION_3_4, убедиться
    что колонки появились и старые строки читаются (term_date=NULL ок). Если существующий schema/DAO-тест хардкодит
    число колонок цели — обновить (G13).
TEST_TYPES: dao, instrumented, unit
CONSTRAINTS:
  - Деньги в Room — `Double`, в domain — `BigDecimal`; конвертация на границе маппера (`?.toBigDecimal()`/`?.toDouble()`).
    `term_months` — целое (Int? ↔ INTEGER), НЕ деньги.
  - Удаление `Goal.termDate` ломает компиляцию VM/тестов, которые его читают (`GoalEditViewModel.kt:66,100,155,214`,
    `GoalEditCreditViewModelTest`) — это зона SPEC 03 (Depends-on этого SPEC); здесь оставить `:core:database` и
    `:core:domain` зелёными, presentation чинит SPEC 03.
  - Миграция аддитивная: НЕ удалять/переименовывать `term_date` (избегаем table-rebuild). Schema v3 должна совпасть
    с `GoalEntity` (все три credit-поля + legacy term_date) — иначе Room упадёт на validateMigration.
  - `MIGRATION_1_2` не трогать. Без комментариев кроме WHY (например, почему term_date оставлен).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Хранение кредитной цели с взносом и сроком

  Scenario: Round-trip новой credit-цели
    Given Goal(variant=CREDIT, downPayment=1 000 000, termMonths=240, annualRatePercent=12)
    When он сохранён через upsert и прочитан findById
    Then downPayment = 1 000 000 and termMonths = 240
    And termDate отсутствует в domain-модели

  Scenario: Миграция 3 → 4 добавляет колонки и не теряет данные
    Given БД на схеме версии 3 со строкой goal (term_date заполнен)
    When применяется MIGRATION_3_4
    Then в таблице goal есть колонки down_payment и term_months
    And существующая строка читается (down_payment = NULL, term_months = NULL)

  Scenario: Savings-цель не несёт credit-полей
    Given Goal(variant=SAVINGS)
    When он сохранён и прочитан
    Then downPayment = null and termMonths = null
```

## Gap / context
Schema v3 (`MIGRATION_1_2` создал goal, `MIGRATION_2_3` добавил `contribution_breakdown`, G10) хранит `term_date`,
не знает про взнос и срок-в-месяцах. SPEC переносит модель на `downPayment` + `termMonths` аддитивной миграцией
`MIGRATION_3_4`, оставляя legacy `term_date` ради безопасности (pet, до релиза, H4).

## Implementation links
- commit: 46811914 (persist downpayment + term months, migrate db v4)
- files: core/domain/.../model/Goal.kt, core/database/.../entity/GoalEntity.kt, core/database/.../mapper/Mappers.kt, core/database/.../migration/Migrations.kt, core/database/.../MoneyDatabase.kt, core/database/.../di/DatabaseModule.kt, core/database/schemas/.../4.json, core/database/.../GoalMapperTest.kt, core/database/.../GoalRepositoryImplTest.kt, core/database/androidTest GoalDaoDownPaymentTermMonthsTest.kt + MoneyDatabaseMigration3To4Test.kt
- note: REBASED v2→3 → v3→4 (MIGRATION_3_4) because contribution-breakdown epic took MIGRATION_2_3. term_date column kept (legacy nullable). Migration + DAO round-trip are instrumented (run on device).
