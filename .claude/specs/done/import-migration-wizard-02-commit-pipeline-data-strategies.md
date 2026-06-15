# Импорт: разделить parse/commit + стратегии данных (ReplaceAll/Append/AppendDedup)
Epic: import-migration-wizard
Order: 02 of 06
Status: done
Depends-on: 01
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Разделить нынешний слитый импорт (G4) на две фазы и реализовать стратегии транзакций.
(1) `parseImport(documentUriString): Result<StagedImport>` — читает и парсит файл (оба формата: Monefy + MyMoney, G3/D2)
в память, БЕЗ записи в БД; возвращает `StagedImport` = распарсенные строки + `ImportPreview` (SPEC 01) для визарда.
(2) `commitImport(staged: StagedImport, plan: ImportPlan): Result<CsvImportFocus?>` — применяет выбранную
`ImportDataStrategy` в ОДНОМ `withTransaction`:
  • `Append` — текущее поведение (resolve + insert, G5/G6);
  • `AppendDedup` — перед вставкой отбросить строки, чьи `TransactionDedupKey` (SPEC 01) совпадают между собой
    и с уже существующими в БД (D3);
  • `ReplaceAll` — «чистый лист»: очистить транзакции + счета + категории приложения (валюты и AppSettings
    СОХРАНИТЬ, O2), затем импортировать как Append.
Категориальная стратегия в этом SPEC = текущее поведение Append (точные совпадения по имени реюзятся, G5) —
ReplaceCurrent/ManualMerge добавляет SPEC 03.
LAYERS: data
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/BackupRepository.kt:27 — заменить/дополнить
    `importTransactionsCsv` парой `parseImport` + `commitImport(staged, plan)`; сохранить старый метод как тонкую
    обёртку (Append-план) для обратной совместимости вызовов и тестов (G3)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt:171-469 — вынести
    парсинг из `importMonefyCsv`/`importMyMoneyCsv` в `parseImport`; commit-ветки по `ImportDataStrategy`; дедуп через
    SPEC 01; ReplaceAll-очистка перед импортом (G4 — сохранить один `withTransaction`)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/TransactionDao.kt:197 — НОВЫЕ: `deleteAll()` и
    запрос/набор для чтения существующих dedup-ключей (для AppendDedup и ReplaceAll); рядом с `countByCategory` (G10)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/AccountDao.kt — НОВЫЙ `deleteAll()` для ReplaceAll
    (assumption — проверить наличие; если нет, добавить аналогично CategoryDao в SPEC 03)
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt:34 — расширить:
    AppendDedup и ReplaceAll сценарии (G18)
TEST_TYPES: dao, unit
CONSTRAINTS:
  - **CLASH (G22):** `BackupRepositoryImpl.kt` — единственное место логики импорта, правился audit7-01 и audit9-03;
    этот SPEC правит его ПЕРЕД SPEC 03. Не трогать параллельно с 03.
  - **Без Room-миграции (G12):** это операции над данными через новые DAO-запросы; `SCHEMA_VERSION` не менять,
    уникальный индекс НЕ добавлять (дедуп — на коммите, D3).
  - Весь коммит — в одном `withTransaction` (G4): сбой откатывает всё (D8). Очистка ReplaceAll и вставка — в той же транзакции.
  - `ReplaceAll` (O2): удалить транзакции + счета + (не-валютные) категории; валюты и AppSettings оставить; затем импорт
    пересоздаёт счета/категории из файла (G5/G6). Деструктив → подтверждение делает UI (SPEC 04), data просто исполняет план.
  - Деньги BigDecimal в домене / Double в Room (G8); occurredAt epoch-millis через существующие TypeConverters; знак суммы → kind (G7).
  - Идемпотентность повторного импорта в Append остаётся прежней (известное поведение — дубли строк); AppendDedup её и чинит.
  - **CI (G18/G20):** Room-оркестрация покрывается ТОЛЬКО `:core:database:connectedDebugAndroidTest` (нужно устройство);
    чистый дедуп — JVM-юнит в SPEC 01. Runner-скрипт даёт ложный pass:false → верифицировать вручную. `:<module>:ktlintFormat` перед коммитом (G19).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Стратегии миграции данных при импорте

  Scenario: Append добавляет, как раньше
    Given в базе 3 транзакции
    When импортируется файл из 5 строк со стратегией Append
    Then в базе 8 транзакций

  Scenario: AppendDedup отбрасывает дубли против базы и внутри файла
    Given в базе есть транзакция T
    When импортируется файл, где одна строка совпадает с T, и две строки идентичны между собой
    Then добавляются только уникальные строки, T не задваивается, из пары идентичных остаётся одна

  Scenario: ReplaceAll очищает данные и импортирует с чистого листа
    Given в базе есть свои транзакции, счета и категории
    And настроены валюты
    When импортируется файл со стратегией ReplaceAll
    Then прежние транзакции/счета/категории удалены
    And в базе только импортированные данные
    And валюты сохранены

  Scenario: Парсинг отделён от записи
    When вызывается parseImport для валидного файла
    Then возвращается превью (кол-во строк, категории, счета, диапазон дат) без изменений в базе
```

## Gap / context
Сейчас `importTransactionsCsv` сразу пишет в БД append'ом, без выбора (G4/G11). Этот SPEC даёт backend-API
(parse → commit(plan)) и три стратегии данных, на которые опираются визард (04) и стратегии категорий (03).

## Implementation links
- commit: f1f210ad (prod), 6ec820ca (test)
- files: core/domain/.../csv/StagedImport.kt, core/domain/.../repository/BackupRepository.kt, core/database/.../projection/TransactionDedupRow.kt, core/database/.../dao/{Transaction,Account,Category}Dao.kt, core/database/.../repository/BackupRepositoryImpl.kt, core/database/src/androidTest/.../MonefyCsvImportE2ETest.kt
