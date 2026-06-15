# Импорт: стратегии категорий — ReplaceCurrent (+сироты) и ManualMerge (reassign)
Epic: import-migration-wizard
Order: 03 of 06
Status: done
Depends-on: 02
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Реализовать в `commitImport` (SPEC 02) выбор `ImportCategoryStrategy` (SPEC 01) поверх стратегии данных.
(1) `Append` — текущее поведение (точные совпадения имени реюзятся, G5) — уже есть после SPEC 02.
(2) `ReplaceCurrent` — удалить текущие категории, оставить только импортные. Перед удалением для каждой
существующей категории с транзакциями применить `OrphanDecision` (D5, приходит из UI в `ImportPlan`):
  • `KeepCategory` → категория НЕ удаляется;
  • `DeleteTransactions` → удалить транзакции категории, затем категорию.
Категории без транзакций удаляются без вопроса. (UI-диалог собирает решения — SPEC 04; data исполняет.)
(3) `AppendManualMerge(mappings)` — применить `CategoryMergeMapping` (SPEC 01): для `MergeInto(target, resultName)`
импортные строки этой категории резолвятся в target-категорию (реюз id), target переименовывается в resultName,
дубликат категории не создаётся; для `CreateNew` — обычное создание. Записи объединяются за счёт общего categoryId.
Также экспонировать read-метод «существующие категории + счётчики транзакций» для превью/диалога визарда.
LAYERS: data
CHANGED_HINT:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt:327-469 — ветки
    по `ImportCategoryStrategy` в commit; для ManualMerge — направить `resolveCategoryId` импортной категории на target id
    и переименовать target; для ReplaceCurrent — удаление с учётом OrphanDecision (G5/G22)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/CategoryDao.kt — НОВЫЕ: `deleteByIds(ids)` /
    `deleteAll()` (сейчас ОТСУТСТВУЮТ — только upsert+archive, G9); `getAllOnce()` если нужно для плана
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/TransactionDao.kt:197-198 — НОВЫЕ:
    `reassignCategory(oldId, newId)` (bulk `UPDATE transaction SET category_id=:newId WHERE category_id=:oldId`,
    допустимо т.к. categoryId nullable + FK SET_NULL, G10) и `deleteByCategory(categoryId)` для D5
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt:34 — расширить:
    ReplaceCurrent (Keep и DeleteTransactions ветки) + ManualMerge (объединение «Еда»→«Продукты», записи слиты) (G18)
TEST_TYPES: dao
CONSTRAINTS:
  - **CLASH (G22):** правит `BackupRepositoryImpl.kt` ПОСЛЕ SPEC 02 (тот же файл). Строго последовательно, не параллельно.
  - **Без Room-миграции (G12):** только новые DAO-запросы; `SCHEMA_VERSION` не менять.
  - Merge должен переносить ВСЕ записи импортной категории в target (общий categoryId), а target переименовать в
    resultName; не плодить дубликат категории (D6). Точные совпадения имени по-прежнему авто-merge (G5) — ManualMerge
    лишь добавляет ручные пары для РАЗНЫХ имён.
  - ReplaceCurrent НЕ трогает транзакции категорий с `KeepCategory`; `DeleteTransactions` удаляет их транзакции до
    удаления категории; категории без транзакций удаляются молча (D5). Осиротевших по иным причинам не создавать
    (все решения — в плане; «по умолчанию» в UI = Оставить).
  - Всё в одном `withTransaction` с коммитом данных из SPEC 02 (G4): один атомарный коммит на весь план (D8).
  - Деньги BigDecimal/Double (G8); occurredAt epoch-millis; matching по `normalizeName` (G7).
  - **CI (G18/G20):** покрытие — `:core:database:connectedDebugAndroidTest` (устройство обязательно); runner ложно
    падает → верифицировать вручную; `:<module>:ktlintFormat` перед коммитом (G19).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Стратегии миграции категорий при импорте

  Scenario: ManualMerge объединяет разные по имени категории
    Given в базе есть категория "Продукты" с 4 транзакциями
    And в импорте есть категория "Еда" с 3 строками
    And задан маппинг "Еда" -> объединить в "Продукты" с именем "Продукты"
    When выполняется коммит
    Then новая категория "Еда" не создаётся
    And все 7 транзакций ссылаются на категорию "Продукты"

  Scenario: ReplaceCurrent с решением "Оставить категорию"
    Given в базе есть категория "Зарплата" с транзакциями, отсутствующая в импорте
    And по ней выбрано "Оставить категорию"
    When выполняется ReplaceCurrent
    Then категория "Зарплата" и её транзакции сохранены
    And остальные текущие категории заменены импортными

  Scenario: ReplaceCurrent с решением "Удалить транзакции"
    Given в базе есть категория "Прочее" с транзакциями, отсутствующая в импорте
    And по ней выбрано "Удалить транзакции категории"
    When выполняется ReplaceCurrent
    Then категория "Прочее" и её транзакции удалены

  Scenario: Категория без транзакций удаляется без вопроса
    Given в базе есть пустая категория, отсутствующая в импорте
    When выполняется ReplaceCurrent
    Then она удаляется без запроса решения
```

## Gap / context
После SPEC 02 категории всегда ведут себя как Append. Этот SPEC добавляет управление категориями: удаление с
безопасной обработкой сирот (D5) и ручное объединение похожих с переносом записей (D6) — требует новых DAO-операций
(удаление категорий — G9 отсутствует; bulk-reassign — G10 отсутствует).

## Implementation links
- commit: c5446aa1 (prod) + 76382ac4 (test)
- files:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/CategoryDao.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/TransactionDao.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/csv/ImportStrategy.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/BackupRepository.kt
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt
