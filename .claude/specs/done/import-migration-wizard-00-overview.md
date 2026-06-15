# Import migration wizard — epic overview
Epic: import-migration-wizard
Order: 00 of 06
Status: done
Depends-on: —
Date: 2026-06-14
Completed: 2026-06-14 (all 6 SPECs shipped to main; epic closed)

## Goal
После выбора CSV-файла для импорта приложение запускает короткий **визард миграции**, который
спрашивает, как влить данные, и даёт навести порядок в категориях, ВМЕСТО нынешнего «молча добавить всё»
(`importMonefyCsv` всегда append, G4/G11). Поток: выбор файла → разбор + предпросмотр → визард собирает
стратегии → ОДИН коммит по выбору (D1).

Визард задаёт:
1. **Как мигрировать данные** — `ReplaceAll` (чистый лист) / `Append` / `AppendDedup` (добавить, удалив дубли).
2. **Как мигрировать категории** — `ReplaceCurrent` (удалить текущие, оставить импортные) / `Append` /
   `AppendManualMerge` (то же + ручное объединение похожих, экран-резолвер).
3. **Настроить каждую категорию сейчас или потом** — «потом» завершает импорт; «сейчас» → степпер по всем
   категориям (вперёд/назад, в конце «Готово»).

**В охвате:** оба CSV-пути (Monefy + MyMoney). **Вне охвата:** восстановление из .backup (Dropbox/GDrive),
ручное объединение СЧЕТОВ, нечёткие авто-подсказки объединения, изменение схемы Room (D2, см. Out of scope).

## Locked decisions (grilled 2026-06-14)
- **D1 — поток:** предпросмотр → стратегия → один коммит. Парсинг и запись сейчас слиты (G4) — разделить.
- **D2 — охват:** оба CSV; .backup и ручной merge счетов — вне охвата.
- **D3 — дедуп:** ключ дубликата = (account, date, amount, category, kind, note); дедуп И внутри импорта,
  И против существующих; коммит-тайм (уникального индекса нет — G11).
- **D4 — стратегии категорий:** ReplaceCurrent / Append (точные совпадения по имени авто-merge, G5) / AppendManualMerge.
- **D5 — сироты:** на пути ReplaceCurrent для каждой существующей категории с транзакциями — диалог
  «N транзакций» → [Оставить категорию] / [Удалить транзакции категории]. Не удалять молча.
- **D6 — UI объединения:** «список-резолвер по импорту» (каждая несовпавшая импортная категория → выпадающий
  список «Создать новую» (дефолт) | объединить с существующей; имя результата редактируется). НЕ git-merge 3 колонки.
- **D7 — настройка:** гейт «сейчас/потом»; «сейчас» → степпер по ВСЕМ результирующим категориям, вперёд/назад,
  «Далее»→«Готово»; редактируются имя/иконка/цвет (как CategoryEdit, G13/G15).
- **D8 — safety:** деструктив (ReplaceAll, «удалить транзакции категории») — с подтверждением; коммит в одном
  `withTransaction` (G4) → откат при сбое. *(assumption: переиспользовать safety-snapshot из audit9-03, если применимо.)*

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `import-migration-wizard-01-import-plan-domain.md` | — | domain | Типы стратегий, ключ дедупа + фильтр, preview-модель (чистая логика, JVM-unit). |
| 02 | `import-migration-wizard-02-commit-pipeline-data-strategies.md` | 01 | data | Разделить parse/commit; стратегии транзакций ReplaceAll/Append/AppendDedup; tx-DAO. ⚠ правит `BackupRepositoryImpl.kt`. |
| 03 | `import-migration-wizard-03-category-strategies-merge-data.md` | 02 | data | Стратегии категорий: ReplaceCurrent (+сироты), ManualMerge (reassign записей); category/tx-DAO. ⚠ тот же файл, после 02. |
| 04 | `import-migration-wizard-04-wizard-shell-strategy-steps.md` | 02, 03 | presentation | Визард-каркас + nav + шаг 1 (данные) + шаг 2 (категории) + диалог-сирот; запуск из BackupRestore; коммит; прыжок периода. |
| 05 | `import-migration-wizard-05-manual-merge-resolver-screen.md` | 03, 04 | presentation | Шаг 3 (для ManualMerge): экран-резолвер по импорту (D6) → строит mappings для коммита. |
| 06 | `import-migration-wizard-06-per-category-config-stepper.md` | 04 | presentation | Шаг 4: гейт «сейчас/потом» → степпер по всем категориям (D7), пикеры из `:core:designsystem`. |

## Why this ordering
- **Foundation-first:** 01 (чистый домен) ничего не зависит и тестируется на JVM; 02/03 (данные) строят на нём;
  04–06 (UI) — поверх готового commit-API.
- **Same-file clash (G22):** 02 и 03 оба правят `BackupRepositoryImpl.kt` (единственное место логики импорта,
  уже правился audit7-01 и audit9-03) → строго последовательно: 02 ставит split parse/commit + стратегии данных
  (категории = текущий Append), 03 добавляет стратегии категорий. **Параллельно их не трогать.**
- **Wizard VM/state clash:** 04 создаёт ViewModel/State визарда; 05 и 06 их расширяют → 05/06 после 04, не параллельно.
- Каждый SPEC независимо «отгружаем»: после 02 backend умеет ReplaceAll/Append/Dedup; после 04 визард сквозной для
  Append/ReplaceCurrent; 05 и 06 добавляют опциональные шаги.

## Key facts (verified — see `pipeline/grounding.md`)
- **G1/G2/G3 (entry):** единый вход CSV-импорта — `BackupRestoreScreen.kt:93-96` (`OpenDocument`) →
  `BackupRestoreEvent.ImportCsvFilePicked` → `BackupRestoreViewModel.importCsv` (`:56,95-113`) →
  `BackupRepository.importTransactionsCsv(uri): Result<CsvImportFocus?>` (`core/domain/.../BackupRepository.kt:27`).
  Пункт «Импорт» на дашборде ведёт сюда же — отдельного вызова нет (подтверждено grep).
- **G4 (signature):** `importMonefyCsv(records): CsvImportFocus?` — весь импорт в одном `withTransaction`
  (`BackupRepositoryImpl.kt:327-469`); парсинг и коммит СЛИТЫ, только append.
- **G5/G6 (signature):** `resolveCategoryId(name,kind)` (`:407-426`), `resolveAccountId(name,currencyId,code)` (`:376-405`) —
  матч по нормализованному имени, иначе создание (AUTO_PALETTE/AUTO icon, `:662-674`).
- **G9 (ABSENT):** у `CategoryDao` нет delete-all/truncate (только upsert + archive) → новый DAO-метод нужен.
- **G10:** `TransactionEntity.categoryId` nullable, FK `onDelete=SET_NULL` (`TransactionEntity.kt:30-35`) → bulk
  `UPDATE … SET category_id=:new WHERE category_id=:old` для merge ДОПУСТИМ; метода пока нет (есть только
  `countByCategory`, `TransactionDao.kt:197-198`).
- **G11 (ABSENT):** дедупа транзакций нет; уникального индекса нет → дедуп на коммите.
- **G12:** `MoneyDatabase SCHEMA_VERSION=4` (`MoneyDatabase.kt:67`); стратегии — операции над ДАННЫМИ через
  новые DAO-запросы, БЕЗ изменения схемы → **Room-миграция не нужна**.
- **G13/G15 (pattern):** `CategoryEntity` редактируемые поля name/kind/iconKey/colorHex/sortOrder
  (`CategoryEntity.kt:12-22`); форма — `CategoryEditViewModel/Screen` (`feature/dictionaries/.../categories/`).
- **G14 (pattern):** мульти-шаговый flow — `OnboardingScreen` HorizontalPager+PagerState (`OnboardingScreen.kt:49-140`).
- **G16/G17:** маршруты `Destinations.kt`+`MyMoneyNavHost.kt` (`:353`), nav-param через `SavedStateHandle`
  (`GoalEditViewModel.kt:36-54`); UDF — StateFlow<State>+SharedFlow<Action> replay=0.
- **G18/G19/G20 (CI):** Room-оркестрация → instrumented `MonefyCsvImportE2ETest` (`:34-46`), не JVM; ktlint+detekt+kover
  на всех модулях `ignoreFailures=false` → `:<module>:ktlintFormat` перед коммитом; runner даёт ложный fail — верифицировать вручную.
- **G22 (CLASH):** `BackupRepositoryImpl.kt` — горячий файл; правки 02→03 последовательно.

## Assumptions (deferred — см. `pipeline/grill.md`)
- O1: `ReplaceAll` пропускает вопрос о категориях (текущих для сверки нет).
- O2: `ReplaceAll` очищает транзакции + счета + категории; валюты и AppSettings сохраняются; реимпорт пересоздаёт.
- O3: ранний выход из степпера настройки (закрыть, сохранив правки) — разрешить.
- O4: визард-UI в `:feature:settings`; шаг настройки компонует пикеры из `:core:designsystem` (без `:feature→:feature`).

## Implementation links
- commit: —
- files:  —
