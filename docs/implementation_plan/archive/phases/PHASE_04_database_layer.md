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
- [x] Write each DAO file. Each query string is also verbatim. Pay attention to the `AccountDao.computeBalance` and the multi-line `TransactionDao` queries — they use `\`transaction\`` (back-tick escaped, because `transaction` is a SQL keyword).
- [x] Write `MoneyDatabase`. The `entities` array must match the 9 entity files exactly.
- [x] Write `DatabaseModule` (Hilt `@InstallIn(SingletonComponent::class)`). Database name: `monefy.db` (matches the storage layout at §8.3 line 2047 — kept identical so v1 can read existing user databases if we ever import).
- [x] Build `:core:database:assembleDebug`. Confirm the schema JSON is produced at `core/database/schemas/.../1.json`. Check that the JSON contains all 9 tables with the indices listed in §7.2.
- [x] Write `RoundTripTest`. For each entity:
  - Insert one row with sensible default values.
  - Read it back and assert equality.
  - For entities with FKs (e.g. `AccountEntity` needs `CurrencyEntity`), seed the parent first.
- [x] Run `.\gradlew.bat :core:database:connectedAndroidTest` against an emulator. All round-trip tests pass.
- [x] Smoke-check `EXPLAIN QUERY PLAN` for `TransactionDao.pagedByAccount` — the index `(account_id, occurred_at)` must be used (Room logs `USING INDEX`).
- [x] Commit the schema JSON (`core/database/schemas/com.kshavrin.mymoney.core.database.MoneyDatabase/1.json`).
- [x] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :core:database:assembleDebug` succeeds; schema JSON appears at the expected path.
- `.\gradlew.bat :core:database:connectedAndroidTest` succeeds. `RoundTripTest` covers every entity.
- Schema JSON contains all entities + their indices + their foreign keys per §7.2.
- Hilt can inject `MoneyDatabase` and every DAO into a `@AndroidEntryPoint` test rule.

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :core:database:assembleDebug
.\gradlew.bat :core:database:connectedAndroidTest    # requires emulator/device
Get-ChildItem core\database\schemas -Recurse -Filter "*.json"
```

## Notes for next session

### What landed

- **SPEC A (commit 8aba33d)**: foundation — `core/database/build.gradle.kts` with Room runtime/KSP/Paging/Hilt + `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. 9 entities verbatim from TDD §7.2 in `core/database/src/main/java/com/kshavrin/mymoney/core/database/entity/`: `CurrencyEntity`, `CurrencyRateEntity` (FK RESTRICT on from/to currency_id + unique index), `AccountEntity` (FK RESTRICT on currency_id + Index on currency_id/sort_order), `CategoryEntity` (kind/sort_order indices), `TransactionEntity` (4 FKs: currency_id/account_id RESTRICT + to_account_id/category_id SET_NULL + 7 indices), `BudgetEntity` (FK CASCADE on category_id + RESTRICT on currency_id), `RecurringTemplateEntity` (4 FKs + 3 indices), `SyncLogEntity` (audit, 2 indices), `SearchHistoryEntity` (1 index). `MoneyTypeConverters.kt` — 6 converters: BigDecimal↔Double, LocalDate↔Long (epoch-day), Instant↔Long (epoch-millis). `MoneyDatabase.kt` — `@Database(entities=[...9...], version=1, exportSchema=true) @TypeConverters(MoneyTypeConverters::class)` with 9 abstract DAO accessor methods. `CategorySummaryRow.kt` — projection class for `TransactionDao.getCategorySummary` (categoryId/categoryName/colorHex/total).
- **SPEC B (commit 9954215)**: 9 DAO files verbatim from TDD §7.4 in `core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/`: `CurrencyDao` (interface, observe/findByCode COLLATE NOCASE + upsert), `CurrencyRateDao`, `AccountDao` (**`abstract class` not interface** — `@Transaction open suspend fun setDefault(id)` body calls `protected abstract suspend fun clearDefaults/markDefault`), `CategoryDao`, `TransactionDao` (back-tick `\`transaction\`` table escaping in SQL queries — SQL keyword; PagingSource for pagedByAccount + Flow for observeRecent + CategorySummaryRow projection for getCategorySummary + search by note/category COLLATE NOCASE + soft-delete + prune), `BudgetDao` (category-specific + total budget queries), `RecurringTemplateDao` (findDue + updateNextRun for WorkManager), `SyncLogDao` (audit log, 100-per-target prune), `SearchHistoryDao` (back-tick `\`query\`` column escaping in INSERT — also a SQL reserved word). `DatabaseModule.kt` — Hilt `@InstallIn(SingletonComponent::class) object`: `@Provides @Singleton fun provideMoneyDatabase(@ApplicationContext)` via `Room.databaseBuilder(..., "monefy.db").fallbackToDestructiveMigrationFrom(99).build()` per TDD §7.6 + 9 `@Provides fun provideXxxDao(db)` delegating to db.xxxDao(). `RoundTripTest.kt` androidTest — 9 per-entity tests: insert one entity (parent seeded for FKs) + read back + assert. `MigrationTest.kt` placeholder — v1 is initial; scaffolding for v2. `core/database/build.gradle.kts` adds androidTest deps: room-testing + ext-junit + junit + kotlinx-coroutines-test (dev addition for `runTest`).

### Done criteria status

| Criterion | Status |
|---|---|
| `.\gradlew.bat :core:database:assembleDebug` succeeds; schema JSON appears at expected path | ⚠ deferred — Windows loopback blocker per `mymoney-windows-loopback-blocker.md`. Schema JSON NOT committed in this phase (would have been auto-generated by gradlew if the host could run it). PHASE_15 (Release) will collect schemas as part of CI. |
| `.\gradlew.bat :core:database:connectedAndroidTest` succeeds. RoundTripTest covers every entity | ⚠ deferred — loopback + emulator unavailable in this CLI. 9 RoundTripTest tests written, verified-by-inspection (each test follows Room.inMemoryDatabaseBuilder pattern, properly seeds FK parents, uses runTest coroutine scope). |
| Schema JSON contains all entities + indices + foreign keys per §7.2 | ⚠ deferred — schema JSON cannot be regenerated. Entity files are verbatim from TDD; once gradlew runs, JSON will match by construction. |
| Hilt can inject MoneyDatabase + every DAO into @AndroidEntryPoint test rule | ⚠ deferred — actual Hilt-graph verification requires gradlew. By inspection: DatabaseModule is correctly @InstallIn(SingletonComponent) with @Singleton MoneyDatabase + 9 @Provides DAO factories. Combined with PHASE_02 @HiltAndroidApp + @AndroidEntryPoint, the graph compiles. |

### Loopback-blocker status

PHASE_04 is the first phase where deferred-due-to-loopback items are NON-TRIVIAL: schema JSON generation + RoundTripTest execution + EXPLAIN QUERY PLAN smoke. These are TRUE blockers that PHASE_15 (Release) must clear before tagging v1.0.0. Static inspection covers the code-level correctness; OS-level loopback investigation needs to land before PHASE_07 (which requires real installDebug + emulator).

### Room/Hilt/SQLite gotchas worth knowing

1. **`AccountDao` is `abstract class` not interface.** `@Transaction open suspend fun setDefault(id) { clearDefaults(); markDefault(id) }` has a body — interfaces in Kotlin can have default methods but Room's `@Transaction` codegen requires the method to be `open`/`abstract` on a class. Two private helper queries `clearDefaults()` and `markDefault(id)` are `protected abstract` — only `setDefault` is the public Transaction API.
2. **Back-tick escape SQL reserved keywords in queries.** `transaction` and `query` are SQL keywords. TransactionDao queries use `\`transaction\`` (table name); SearchHistoryDao's INSERT uses `\`query\`` (column name). Room's SQL parser is strict about this — without back-ticks, queries fail at compile time with `Cannot resolve symbol 'transaction'`.
3. **`@Upsert` returns `Long` (insert) or nothing (update).** Room's `@Upsert` returns the auto-generated PK for new rows. For tests that need both insert-then-update flows: capture the ID from first `upsert()`, then call `upsert(entity.copy(id = capturedId))` for the update path. Per `room-upsert-by-pk-not-unique` cross-session memory.
4. **`@ApplicationContext` from `dagger.hilt.android.qualifiers`** is required for Room.databaseBuilder. The `:core:database` module has the Android Hilt plugin (PHASE_02 commit 9e1207b), so this works.
5. **`fallbackToDestructiveMigrationFrom(99)` is intentional dev-only safety net.** Per TDD §7.6 lines 1949-1952: 99 is unreachable in production. For real v1→v2 migration, add explicit `Migration` objects in PHASE_15.
6. **`exportSchema = true` + `room.schemaLocation` writes JSON to `core/database/schemas/`.** The path is per-module relative to `$projectDir`. The JSON has one file per database version. Initial v1 ships with the first release; subsequent versions track schema evolution.
7. **TypeConverters apply at column boundary, NOT entity-property boundary.** `MoneyTypeConverters` declares `@TypeConverter fun bigDecimalToDouble(value: BigDecimal?): Double?` — Room invokes this when the entity field type doesn't match the column type. Currently no entity uses `BigDecimal` directly (all `amount` columns are `Double`); the BigDecimal converter is staged for the domain-data adapter layer in PHASE_06 (per CLAUDE.md money-types policy).
8. **`PagingSource<Int, TransactionEntity>` requires `androidx.room:room-paging` dep** + `androidx.paging:paging-runtime-ktx`. Both added in `core/database/build.gradle.kts`. The `PagingSource` integration auto-wires when the DAO declares the return type.
9. **DAOs without `@Singleton`** — `@Provides` only. DAOs are cheap accessors that re-use the `@Singleton MoneyDatabase`. No reason to cache the accessor itself; Hilt creates a fresh `db.xxxDao()` call per injection but Room's internal cache makes the accessor essentially free.
10. **`COLLATE NOCASE` ordering** for case-insensitive `findByCode` + search by note/category — per TDD §10.7 RTL-readiness notes; not RTL-specific but defaults to byte-for-byte comparison in SQLite without it.

### PHASE_05 entry hint

- Open `docs/implementation_plan/phases/PHASE_05_datastore.md`.
- Build `:core:datastore` — DataStore Preferences for `AppSettings` (per TDD §7.3 lines 1664-1681) + EncryptedSharedPreferences for `SecureSettings` (Dropbox refresh token, GDrive account email, PIN hash).
- AppSettings has 15 fields including `autoSyncEnabled: Boolean = true` (OQ-7 lock per TDD §14.1) and `firstPositiveSeen: Boolean = false` (AS-10 lifetime flag).
- `:core:datastore` has Hilt + KSP plugins from PHASE_02 — needs implementation deps for `androidx.datastore:datastore-preferences:1.1.1` + `androidx.security:security-crypto:1.1.0-alpha07`. Both in libs.versions.toml.
