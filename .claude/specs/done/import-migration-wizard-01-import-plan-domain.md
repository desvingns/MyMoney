# Импорт: доменные типы стратегий, дедуп и preview-модель
Epic: import-migration-wizard
Order: 01 of 06
Status: done
Depends-on: —
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Чистый доменный слой визарда миграции (без Android/Room зависимостей, тестируется на JVM):
типы стратегий, функция дедупликации и модель предпросмотра импорта. Это фундамент, на котором SPEC 02/03
строят коммит, а 04–06 — UI.
(1) `ImportDataStrategy` = ReplaceAll | Append | AppendDedup.
(2) `ImportCategoryStrategy` = ReplaceCurrent | Append | AppendManualMerge(mappings).
(3) `CategoryMergeMapping` для ручного объединения: по импортной категории — действие
`CreateNew(name)` ИЛИ `MergeInto(targetCategoryName/targetId, resultName)` (D6).
(4) `OrphanDecision` для пути ReplaceCurrent: по существующей категории — `KeepCategory` | `DeleteTransactions` (D5).
(5) `ImportPlan` — агрегат выбранных стратегий + mappings + orphan-решений, который SPEC 02/03 применяют на коммите.
(6) `ImportPreview` — что показать перед коммитом: количество записей, набор категорий (имя+kind) и счетов
(имя+валюта) из файла, диапазон дат.
(7) Чистая функция дедупа: ключ `TransactionDedupKey(accountKey, occurredAt, amount, categoryKey, kind, note)`
(D3) + функция, удаляющая дубли ВНУТРИ списка и помечающая совпадения с уже существующими (через переданный
набор существующих ключей). Логика чистая — БД не трогает.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/csv/ImportStrategy.kt — НОВЫЙ: sealed types
    ImportDataStrategy / ImportCategoryStrategy / CategoryMergeMapping / OrphanDecision / ImportPlan (assumption — новый файл)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/csv/ImportPreview.kt — НОВЫЙ: data class ImportPreview
    (rowCount, categories:Set, accounts:Set, dateRange) (assumption)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/csv/TransactionDedup.kt — НОВЫЙ: TransactionDedupKey
    + dedup-функции; нормализация имён через существующий `MonefyCsvImportParser.normalizeName` (G7, переиспользовать, не дублировать)
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/csv/TransactionDedupTest.kt — НОВЫЙ: JVM-юнит фикстуры (assumption — новый тест)
TEST_TYPES: unit
CONSTRAINTS:
  - Деньги `BigDecimal` в домене (G8); сравнение сумм в ключе дедупа — по нормализованному масштабу
    (`stripTrailingZeros`/`compareTo`), НЕ по `equals` BigDecimal (100 != 100.00 в equals).
  - Имена в ключах (account/category) нормализовать тем же `MonefyCsvImportParser.normalizeName` (trim+NFC+collapse+lowercase, G7) —
    переиспользовать, чтобы дедуп и matching были консистентны; не вводить вторую нормализацию.
  - Дедуп охватывает И дубли внутри импорта, И совпадения с существующими записями (D3); функция принимает набор
    существующих ключей отдельно (БД читает слой data в SPEC 02).
  - Никаких Android/Room импортов в этом модуле (`:core:domain` чистый). Тест-таска `:core:domain` = `test`, не `testDebugUnitTest` (G20).
  - ≥3 фикстуры: (a) два одинаковых ряда в файле → один остаётся; (b) ряд, совпадающий с существующим ключом → помечен дублем;
    (c) ряды, отличающиеся ТОЛЬКО заметкой/категорией → НЕ дубли (полный ключ из 6 полей).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Доменная логика стратегий импорта и дедупа

  Scenario: Дубли внутри файла схлопываются
    Given в импорте две строки с одинаковыми счётом, датой, суммой, категорией, типом и заметкой
    When применяется AppendDedup
    Then остаётся ровно одна из них

  Scenario: Совпадение с существующей записью помечается дублем
    Given в базе есть транзакция с ключом K
    And в импорте есть строка с тем же ключом K
    When вычисляются дубли против существующих
    Then импортная строка помечена как дубликат и не добавляется

  Scenario: Разная заметка — не дубликат
    Given две строки совпадают по счёту, дате, сумме, категории и типу, но различаются заметкой
    When применяется AppendDedup
    Then обе строки сохраняются

  Scenario: Суммы 100 и 100.00 считаются равными в ключе
    Given две строки идентичны, но суммы записаны как "100" и "100.00"
    When применяется AppendDedup
    Then они считаются дубликатом (масштаб не влияет)
```

## Gap / context
Сейчас домен ничего не знает о стратегиях импорта: `importMonefyCsv` всегда append без дедупа (G4/G11).
Этот SPEC вводит чистые типы и логику дедупа, чтобы SPEC 02/03 применяли их на коммите, а UI (04–06) — выбирал.

## Implementation links
- commits: bd526f13 (domain types + dedup), 68a3be85 (test compile fix: drop explicit `<T>` on JUnit assertNotEquals)
- files: core/domain/.../csv/ImportStrategy.kt (ImportDataStrategy/ImportCategoryStrategy/MergeAction/CategoryMergeMapping/OrphanDecision/ImportPlan), core/domain/.../csv/ImportPreview.kt, core/domain/.../csv/TransactionDedup.kt (TransactionDedupKey + pure dedup), + 4 test files (TransactionDedupKeyTest, DedupTransactionsTest, ImportStrategyTest, ImportPreviewTest)
- verified: :core:domain:test 49 new tests green (Dedup 18, DedupKey 9, ImportStrategy 17, ImportPreview 5); ktlintCheck green; pushed to main. Pure :core:domain, no Android/Room imports (reuses MonefyCsvImportParser.normalizeName). NOTE: dedup amount equality by stripTrailingZeros/compareTo (not BigDecimal.equals).
- shape note (for SPEC 02 consumer): `ImportPlan.orphanDecisions: Map<String, OrphanDecision>` keyed by normalized category name; `MergeAction.MergeInto(targetCategoryName, targetId: Long?, resultName)`.
