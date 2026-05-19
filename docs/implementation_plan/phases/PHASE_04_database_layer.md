# PHASE 04 — Database layer (`:core:database`)

## Goal

Build the entire Room layer per TDD §7.1–§7.8: 9 entities (`CurrencyEntity`, `CurrencyRateEntity`, `AccountEntity`, `CategoryEntity`, `TransactionEntity`, `BudgetEntity`, `RecurringTemplateEntity`, `SyncLogEntity`, `SearchHistoryEntity`), all DAOs with their suspend + Flow + Paging signatures, type converters, `MoneyDatabase` `@Database(version = 1, exportSchema = true)`, Hilt module providing the singleton DB + each DAO, and an in-memory Room test that round-trips one record per entity. Schema JSON exported to `core/database/schemas/`.

## TDD anchors

- §7.1 ER diagram — lines 1485–1500
- §7.2 Entities (Room) — lines 1501–1661 (the biggest block; full table of `@Entity`, `@ForeignKey`, `@Index` declarations)
- §7.4 DAOs — lines 1691–1906
- §7.6 Migrations — lines 1925–1953
- §7.8 Validation rules — lines 1971–1983
- §10.7 RTL/locale-aware queries (`COLLATE NOCASE`) — lines 2403–2408

## Prerequisites

- PHASE_02 — done (Hilt graph in place)
- PHASE_03 — done (theme not strictly required, but next phases assume it)

## Deliverables (in `:core:database`)

- `core/database/build.gradle.kts` — add Room runtime + Room KSP + Room Paging + Paging 3 + Hilt KSP. `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
- `core/database/src/main/java/com/kshavrin/mymoney/core/database/MoneyDatabase.kt` — `@Database(entities = [...], version = 1, exportSchema = true) @TypeConverters(MoneyTypeConverters::class) abstract class MoneyDatabase : RoomDatabase()` exposing all 9 DAOs.
- `core/database/src/main/java/com/kshavrin/mymoney/core/database/entity/*.kt` — one file per entity, schema verbatim from §7.2.
- `core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/*.kt` — one file per DAO. Signatures verbatim from §7.4 (`observeActive`, `observeAll`, `findById`, `findByCode`, `pagedByAccount`, `computeBalance`, `setDefault`, `pruneDeleted`, etc.).
- `core/database/src/main/java/com/kshavrin/mymoney/core/database/converter/MoneyTypeConverters.kt` — `@TypeConverter` for `BigDecimal ↔ Double` (used only by domain wrapper, not stored — Room columns are `Double`), `LocalDate ↔ Long` (epoch-day), `Instant ↔ Long` (epoch-millis). Per §7.2 lines 1503–1504.
- `core/database/src/main/java/com/kshavrin/mymoney/core/database/di/DatabaseModule.kt` — Hilt module: provides singleton `MoneyDatabase` via `Room.databaseBuilder(...).fallbackToDestructiveMigrationFrom(99).build()` (per §7.6 lines 1949–1952). Provides each DAO via `@Provides fun provideXxxDao(db: MoneyDatabase) = db.xxxDao()`.
- `core/database/schemas/com.kshavrin.mymoney.core.database.MoneyDatabase/1.json` — auto-generated on first build; commit to repo per §7.6 line 1951.
- `core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/RoundTripTest.kt` — instrumentation test using `Room.inMemoryDatabaseBuilder` that inserts and reads one row per entity. Verifies indices via `EXPLAIN QUERY PLAN` for at least 3 high-traffic queries.
- `core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MigrationTest.kt` — placeholder. v1 is the initial; this file holds the scaffolding for v2 migrations (TODO comment).

## Task checklist

- [x] Read TDD anchors. Pay close attention to `@ForeignKey` `onDelete = RESTRICT` vs `SET_NULL` vs `CASCADE` — they encode business rules (AS-13 enforces RESTRICT for account deletion).
- [x] Set up `room.schemaLocation` in `core/database/build.gradle.kts` via `ksp` block. Verify the `schemas/` directory is gitignored EXCEPT the actual JSON files.
- [x] Write `MoneyTypeConverters.kt`. Cover `LocalDate`, `Instant`, and (only-if-used-in-domain-layer) `BigDecimal`. Note: storage is `Double`; the BigDecimal converter is for type adapters between domain and storage in PHASE_06.
- [x] Write each entity file — one per entity. Copy `@Entity(...)`, `@PrimaryKey`, `@ColumnInfo`, `@ForeignKey`, `@Index` blocks **verbatim** from §7.2. Keep column ordering identical (helps when diffing schema JSON against the original APK's table definitions later).
- [ ] Write each DAO file. Each query string is also verbatim. Pay attention to the `AccountDao.computeBalance` and the multi-line `TransactionDao` queries — they use `\`transaction\`` (back-tick escaped, because `transaction` is a SQL keyword).
- [x] Write `MoneyDatabase`. The `entities` array must match the 9 entity files exactly.
- [ ] Write `DatabaseModule` (Hilt `@InstallIn(SingletonComponent::class)`). Database name: `monefy.db` (matches the storage layout at §8.3 line 2047 — kept identical so v1 can read existing user databases if we ever import).
- [ ] Build `:core:database:assembleDebug`. Confirm the schema JSON is produced at `core/database/schemas/.../1.json`. Check that the JSON contains all 9 tables with the indices listed in §7.2.
- [ ] Write `RoundTripTest`. For each entity:
  - Insert one row with sensible default values.
  - Read it back and assert equality.
  - For entities with FKs (e.g. `AccountEntity` needs `CurrencyEntity`), seed the parent first.
- [ ] Run `.\gradlew.bat :core:database:connectedAndroidTest` against an emulator. All round-trip tests pass.
- [ ] Smoke-check `EXPLAIN QUERY PLAN` for `TransactionDao.pagedByAccount` — the index `(account_id, occurred_at)` must be used (Room logs `USING INDEX`).
- [ ] Commit the schema JSON (`core/database/schemas/com.kshavrin.mymoney.core.database.MoneyDatabase/1.json`).
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :core:database:assembleDebug` succeeds; schema JSON appears at the expected path.
- `.\gradlew.bat :core:database:connectedAndroidTest` succeeds. `RoundTripTest` covers every entity.
- Schema JSON contains all entities + their indices + their foreign keys per §7.2.
- Hilt can inject `MoneyDatabase` and every DAO into a `@AndroidEntryPoint` test rule.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :core:database:assembleDebug
.\gradlew.bat :core:database:connectedAndroidTest    # requires emulator/device
Get-ChildItem core\database\schemas -Recurse -Filter "*.json"
```

## Notes for next session

(empty — fill at end of session. If `RoomMigrationException` test for v1→v1 trivially passes, note that PHASE_15 will revisit migration tests when v2 lands.)
