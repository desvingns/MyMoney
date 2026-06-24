# Хранимое поле textColor + Room-миграция + авто-заполнение при сохранении/импорте
Epic: category-icon-text-color
Order: 02 of 04
Status: done
Depends-on: 01
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Добавить хранимый цвет текста категории и сделать оба цвета (colorHex + textColor) производными
от иконки.
  1) Домен `Category`: добавить `textColor: String` (hex `#RRGGBB`).
  2) Room `CategoryEntity`: добавить колонку `text_color` (TEXT NOT NULL). Обновить маппер
     `toDomain`/`toEntity`.
  3) Авто-заполнение при сохранении: в точке записи категории выставлять
     `colorHex = categoryIconDominantHex(iconKey)` (без контраст-коррекции, для долек/тинта — A1/D2)
     и `textColor = categoryTextColorHex(iconKey)` (с порогом контраста — D3), ИГНОРИРУЯ любые
     входящие значения цвета. Сделать это в одном месте (репозиторий `upsert` или доменный
     use-case сохранения), чтобы и форма (SPEC 04), и импорт шли одним путём.
  4) Импорт: путь создания/обновления категорий при импорте Monefy/MyMoney CSV должен так же
     выставлять colorHex+textColor из iconKey (через ту же функцию).
  5) Room-миграция `MIGRATION_(N)_(N+1)`: `ALTER TABLE category ADD COLUMN text_color TEXT NOT NULL
     DEFAULT ''`, затем построчный бэкфилл в Kotlin (курсор по `id, icon_key`): для каждой строки
     посчитать `categoryIconDominantHex(icon_key)` → UPDATE `color_hex`, и `categoryTextColorHex(
     icon_key)` → UPDATE `text_color`. Bump `SCHEMA_VERSION`. (Бэкфилл colorHex существующим — A1.)
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/src/main/.../model/Category.kt:5-15 — добавить `textColor: String` (G1)
  - core/database/src/main/.../entity/CategoryEntity.kt:12-22 — колонка `text_color` (G2)
  - core/database/src/main/.../mapper/Mappers.kt:193-217 — маппинг textColor обе стороны (G3)
  - core/database/src/main/.../repository/CategoryRepositoryImpl.kt:34-38 — в upsert выставлять
    colorHex+textColor из iconKey через :core:common (SPEC 01); либо доменный use-case сохранения (G4)
  - core/database/src/main/.../migration/Migrations.kt — НОВАЯ `MIGRATION_(N)_(N+1)` (ADD COLUMN +
    построчный бэкфилл по icon_key), регистрация в DatabaseModule.kt; bump `MoneyDatabase.SCHEMA_VERSION`
    (прочитать текущее значение — после currency-exchange-rate ≈ 6)
  - импорт: путь создания категорий при импорте (BackupRepositoryImpl / MonefyCsvImport*) выставляет
    colorHex+textColor из iconKey
TEST_TYPES: instrumented, unit
CONSTRAINTS:
  - `BackupRepositoryImpl.kt` — ГОРЯЧИЙ файл (память: ≥3 предыдущих серийных правки); правка только
    аддитивная (новая колонка/поле), не ломать CSV round-trip и формат бэкапа.
  - CSV-экспорт/импорт категорий: новая колонка АДДИТИВНА — старый формат должен импортироваться
    (textColor отсутствует → вычислить из iconKey, не падать).
  - Миграция тестируется ИНСТРУМЕНТАЛЬНО (MigrationTestHelper, реальный Room,
    `:core:database:connectedDebugAndroidTest`) — нужен подключённый девайс; runner-скрипт :core:*
    пропускает, проверять вручную. ktlintFormat перед коммитом (G22).
  - Бэкфилл colorHex в миграции ПЕРЕЗАПИШЕТ ранее выбранные пользователем цвета — это и есть A1
    («авто из иконки для всех»), осознанное решение, не баг.
  - Облачный restore = бинарная копия БД; нормализация цветов хранимой БД делается этой миграцией
    при открытии восстановленной БД (как O1 в money-decimal).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Хранимый цвет текста категории и миграция

  Scenario: Сохранение категории заполняет оба цвета из иконки
    Given новая категория с иконкой "taxi"
    When она сохраняется
    Then в БД textColor = читаемый цвет иконки такси
    And colorHex = доминирующий цвет иконки такси
    And переданные извне значения цвета игнорируются

  Scenario: Миграция бэкфиллит существующие категории
    Given БД предыдущей версии с категорией icon_key="car", произвольным color_hex и без text_color
    When применяется MIGRATION_(N)_(N+1)
    Then у категории появляется text_color, вычисленный из "car"
    And color_hex пересчитан из "car"

  Scenario: Импорт старого CSV без цвета не падает
    Given CSV без колонки цвета
    When выполняется импорт
    Then категории создаются с colorHex/textColor, вычисленными из iconKey
```

## Gap / context
Сейчас цвет один (`colorHex`), задаётся пользователем и не связан с иконкой; цвета текста как
отдельной сущности нет. SPEC вводит хранимый производный `textColor`, привязывает оба цвета к иконке
и приводит уже накопленные данные к новой модели миграцией.

## Implementation links
- commit: d9a47249 (feat), 7603a07a (test)
- files:
- core/domain/.../model/Category.kt (textColor field), seed/InitialDataSeeder.kt
- core/database/.../entity/CategoryEntity.kt (text_color), mapper/Mappers.kt (toEntity auto-fill chokepoint), migration/Migrations.kt (MIGRATION_6_7), MoneyDatabase.kt (SCHEMA_VERSION=7), di/DatabaseModule.kt, schemas/7.json, repository/BackupRepositoryImpl.kt
- feature/dictionaries/.../CategoryEditViewModel.kt
- tests: core/database MoneyDatabaseMigration6To7Test (5/5 device), CategoryMappersTest + ~22 stale-test reconciliations
