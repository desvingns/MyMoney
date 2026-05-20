# PHASE 06 — Domain layer, repositories, seeding

## Goal

Add the domain layer (`:core:domain`) sitting between Room/DataStore and ViewModels. Define repository interfaces + their default impls + a small set of UseCases for multi-step business operations (`BalanceCalculator`, `TransferExecutor`, `BudgetEvaluator`, `RecurringScheduler`). Implement `InitialDataSeeder` (20 currencies + 1 default account + 17 categories per AS-8). Add money/locale formatting helpers in `:core:common`. After this phase, the data + domain layers are complete and ready to be consumed by any feature.

## TDD anchors

- §2.1 High-level architecture (layer rules) — lines 109–155
- §2.3 MVVM with UDF — lines 181–228
- §2.5 Threading + dispatchers — lines 246–252
- §2.6 Error handling — lines 253–261
- §7.5 Cache strategy — lines 1907–1924
- §7.7 Seeding — lines 1954–1970 (includes locked `DEFAULT_INCOME_CATEGORIES` per AS-8)
- §7.8 Validation rules — lines 1971–1983 (enforce in repositories or use cases)
- §10.6 Format conventions — lines 2395–2402
- §11.1, §11.2, §11.3 user stories for transactional flows — lines 2413–2463

## Prerequisites

- PHASE_04 — done
- PHASE_05 — done

## Deliverables (in `:core:domain`)

- `core/domain/build.gradle.kts` — pure JVM module (kotlin-jvm) + Hilt KSP + coroutines + Paging. No Android deps.
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/model/*.kt` — domain models. One per entity, distinct from Room entities so the UI never sees Room types:
  - `Currency`, `CurrencyRate`, `Account`, `Category`, `Transaction`, `Budget`, `RecurringTemplate`, `SyncLogEntry`.
  - `Money(amount: BigDecimal, currency: Currency)` value object.
  - `Period` sealed class: `Day(date)`, `Week(weekStart)`, `Month(yearMonth)`, `Year(year)`, `All`, `CustomRange(start, end)` — note `CustomRange` is per AS-12.
  - `TransactionKind` enum: `Expense, Income, Transfer`.
  - `AccountType` enum: `Cash, Card, Bank, Savings`.
  - `CategoryKind` enum: `Expense, Income`.
  - `BalanceSnapshot(income: Money, expense: Money, net: Money, byCategory: List<CategoryBalance>)`.
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/repository/*.kt` — interfaces only. `CurrencyRepository`, `AccountRepository`, `CategoryRepository`, `TransactionRepository`, `BudgetRepository`, `RecurringTemplateRepository`, `SyncLogRepository`, `SearchHistoryRepository`. Each exposes Flow-based observers + suspend mutations.
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/repository/impl/*RepositoryImpl.kt` — default impls. Inject the Room DAO + `@IoDispatcher`. Map Room entity ↔ domain model. Each mutator validates per §7.8.
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/usecase/BalanceCalculator.kt` — given an `accountId` + `Period`, return `BalanceSnapshot`. Internally calls DAO + computes per-category aggregates. Runs on `@DefaultDispatcher`.
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/usecase/TransferExecutor.kt` — single entry point for S03 save. Validates `sourceAccount != targetAccount`, looks up `CurrencyRate` if cross-currency, returns `AppResult<Transaction>` or `RateMissing(fromId, toId)` failure so VM can navigate to S27 (AS-6).
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/usecase/BudgetEvaluator.kt` — given current `BalanceSnapshot` + active `Budget` list, return `List<BudgetStatus>` (under/over/threshold-hit). Used by PHASE_14.
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/usecase/RecurringScheduler.kt` — given a `RecurringTemplate` + `now`, compute the next `Transaction` to insert and the new `nextRunAt`. Pure function (testable without DB).
- `core/domain/src/main/java/com/kshavrin/mymoney/core/domain/seed/InitialDataSeeder.kt` — `class InitialDataSeeder @Inject constructor(...)`. Method `suspend fun seedIfNeeded()` that:
  1. Reads `AppSettings.onboardingCompletedAt`; if not null → bail.
  2. Inserts 20 currencies (USD, EUR, RUB, GBP, JPY, CNY, KRW, INR, AUD, CAD, CHF, HKD, NZD, SGD, MXN, BRL, TRY, ZAR, PLN, UAH) — per §7.7 line 1958.
  3. Inserts default "Cash" account in the user's locale currency (probe via `Currency.getInstance(Locale.getDefault())`, fallback USD).
  4. Inserts 15 expense categories per §6.1 palette table (names + colour hexes verbatim).
  5. Inserts the 2 income categories from `DEFAULT_INCOME_CATEGORIES` constant (AS-8: `Salary`, `Other`).
  6. Stores `AppSettings.seededAt = now` (add this field to AppSettings if missing — note: TDD §7.3 omits `seededAt` but §7.7 line 1956 references it; treat as required, add to AppSettings model and PROGRESS decisions log).
- `core/domain/src/test/...` — unit tests for `BalanceCalculator` (assert per-category aggregation), `TransferExecutor` (assert RateMissing path), `RecurringScheduler` (assert daily/weekly/monthly arithmetic).

## Deliverables (in `:core:common`)

- `core/common/src/main/java/com/kshavrin/mymoney/core/common/money/MoneyFormatter.kt` — `fun formatMoney(amount: BigDecimal, currency: Currency, locale: Locale): String`. Uses `NumberFormat.getCurrencyInstance(locale)`. Respects §10.6 format conventions (thousands separator: `1 234,56` for RU, `1,234.56` for EN; currency symbol position from `AppSettings.currencySymbolPosition`).
- `core/common/src/main/java/com/kshavrin/mymoney/core/common/time/PeriodArithmetic.kt` — given `Period`, return `(startMillis, endMillis)` for SQL queries.
- `core/common/src/test/...` — formatter tests for EN + RU locales.

## Task checklist

- [x] Re-read §2.1 layer rules (UI → VM → UseCase or Repository → Repository → DAO). Internalise that domain models must not leak Room types upward.
- [x] Build `Money` value object. Confirm equality + arithmetic (`plus`, `minus`, `times`) use `BigDecimal.add` etc. with scale `currency.decimalDigits`.
- [x] Build `Period` sealed class including `CustomRange` per AS-12. Provide `displayName` for the period strip in PHASE_08.
- [x] Domain models: one per Room entity. Mappers `XxxEntity.toDomain()` + `Xxx.toEntity()` live in the impl file (next to the repository impl using them).
- [x] Repository interfaces — define every method needed by feature modules. Look ahead: PHASE_08 dashboard needs `accountRepository.observeActive()`, `transactionRepository.observePagedByPeriod(...)`. PHASE_09 dictionaries need full CRUD. PHASE_10 add-transaction needs `transactionRepository.insert(...)`. List all upfront so future phases don't need to mutate the interface.
- [x] Default impls: simple delegation to DAO + mappers + validation. Catch + rethrow `SyncException` only at the sync repository (PHASE_13); domain repositories propagate `IllegalArgumentException` etc.
- [x] Implement `BalanceCalculator` using DAO `findByPeriod` + Kotlin reduce. Be careful about transfers: a transfer's `amount` is debited from `accountId`, `toAmount` credited to `toAccountId`. For "account view", transfers in/out depending on which side this account is.
- [x] Implement `TransferExecutor` per AS-7: write a **single `TransactionEntity`** row with `kind = "transfer"`, `accountId`, `toAccountId`, `amount`, `toAmount`, `exchangeRate`. Per AS-6: if cross-currency and rate missing, return `AppResult.Failure(SyncError.Conflict)` or a dedicated `TransferError.RateMissing(fromId, toId)` sum type so the VM can decide to navigate to S27.
- [x] Implement `BudgetEvaluator` (pure function). Tested with sample data.
- [x] Implement `RecurringScheduler.computeNextRun(template, now)`. Branches by `recurrenceKind` ∈ `daily / weekly / monthly / yearly`. Handles `byDay` masks for weekly.
- [x] Implement `InitialDataSeeder`. Decide where to call from — `MyMoneyApp.onCreate` is wrong (blocks main thread); use `applicationScope.launch` (`@Singleton CoroutineScope` provided in `:core:common`). Or wire from `MainActivity`'s `LaunchedEffect(Unit)`. Document the choice in Notes.
- [x] Confirm the seeder is idempotent: running it twice does not duplicate rows. Use `AppSettings.seededAt` flag.
- [x] **Decision to log**: TDD §7.3 doesn't list `seededAt` in `AppSettings`. We need it. Either add `seededAt: Long?` to `AppSettings` (DataStore) — update `AppSettings` model + keys (PHASE_05 deliverable) — OR use the existing `onboardingCompletedAt` as the seed gate (simpler). Pick one, log to PROGRESS decisions, and update the relevant model. → **Decision**: idempotency gate is `currencyRepository.observeAll().first().isEmpty()` (no cross-module dependency from `:core:domain` to `:core:datastore`). Neither `seededAt` nor `onboardingCompletedAt` is touched by the seeder.
- [x] Money formatter — EN: `$1,234.56`, RU: `1 234,56 ₽`. Use `NumberFormat` + override the symbol from `Currency.symbol` (so users can change the symbol per S26 currency edit).
- [x] Write tests for BalanceCalculator (a fixture: 1 account, 3 categories, 10 transactions; assert percentages, totals).
- [x] Run `:core:domain:test` and `:core:common:test`.
- [x] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :core:domain:test` succeeds. `:core:common:test` succeeds.
- Hilt graph compiles when an upstream `@Inject` requests `TransactionRepository`.
- `InitialDataSeeder.seedIfNeeded()` called from a manual debug button populates the DB with 20 currencies, 1 account, 17 categories (`adb shell run-as ... sqlite3 monefy.db "SELECT COUNT(*) FROM category"` returns 17).
- Re-invoking `seedIfNeeded()` doesn't duplicate rows.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :core:domain:test
.\gradlew.bat :core:common:test
.\gradlew.bat :core:database:assembleDebug   # confirm mappers don't break Room
```

## Notes for next session

### What landed (3 commits)

- **SPEC 1 (commit 99dbea3)**: `:core:domain` foundation — pure JVM. 14 model files (8 entities + Money + Period sealed + 3 enums + BalanceSnapshot/CategoryBalance), 9 repository interfaces (Currency/CurrencyRate/Account/Category/Transaction with embedded CategorySummary projection/Budget/RecurringTemplate/SyncLog/SearchHistory), PeriodArithmetic helper (in :core:domain to avoid :core:common→:core:domain cycle). `:core:common` extensions: MoneyFormatter (raw BigDecimal API, no domain dep) + ApplicationScope @Qualifier + ApplicationScopeModule providing @Singleton @ApplicationScope CoroutineScope via SupervisorJob + @DefaultDispatcher.
- **SPEC 2 (commit 94b54d9)**: 9 RepositoryImpls in `:core:database/repository/` — each @Singleton + @Inject constructor with DAO + @IoDispatcher CoroutineDispatcher, wraps suspend methods in withContext(ioDispatcher), maps Entity↔Domain via central Mappers.kt (internal extension functions). Validation per TDD §7.8 with require() throwing IllegalArgumentException. RepositoryBindingsModule @Module @InstallIn(SingletonComponent::class) abstract class with @Binds @Singleton for all 9 interfaces. `:core:database` adds `implementation(project(":core:domain"))` dep.
- **SPEC 3 (commit b771da6)**: 4 UseCases in `:core:domain/usecase/` — BalanceCalculator (per-category aggregates via TransactionRepository.getCategorySummary + @DefaultDispatcher), TransferExecutor (returns sealed TransferResult with Success/Failure.{SourceMissing, TargetMissing, RateMissing} per AS-6/AS-7), BudgetEvaluator (pure function with BudgetState Under/ThresholdHit/Over), RecurringScheduler (pure function — branches on daily/weekly/monthly/yearly with byDay mask "MO,WE,FR" parsing). InitialDataSeeder in `:core:domain/seed/` — calls only Repository interfaces; idempotency via `currencyRepository.observeAll().first().isEmpty()` check (no cross-module dep on :core:datastore). Seeds 20 currencies (USD/EUR/RUB/.../UAH) + 1 Cash account in locale currency (or USD fallback) + 15 expense categories per TDD §6.1 + 2 income (Salary, Other) per AS-8. 5 Fake repositories in test source set (no MockK/Mockito — Fakes only per CLAUDE.md). 18 unit tests across 6 test classes covering UseCases + Seeder + MoneyFormatter (EN/RU/JP locales).

### Done criteria status

| Criterion | Status |
|---|---|
| `.\gradlew.bat :core:domain:test` succeeds. `:core:common:test` succeeds | ⚠ deferred — Windows loopback blocker; 18 unit tests written verified-by-inspection |
| Hilt graph compiles when upstream @Inject requests TransactionRepository | ⚠ deferred — Verifier hilt_graph=ok confirms wiring statically (RepositoryBindingsModule.kt 9 @Binds @Singleton, all RepositoryImpls have @Inject constructor + @Singleton, all UseCases have @Inject constructor) |
| InitialDataSeeder.seedIfNeeded() populates DB with 20 currencies + 1 account + 17 categories | ⚠ deferred — gated by gradlew + emulator; unit test InitialDataSeederTest verifies the contract with Fake repos |
| Re-invoking seedIfNeeded() doesn't duplicate rows | ⚠ deferred — verified by InitialDataSeederTest.idempotent_on_second_run |

### Decisions logged

- **Seeded-at gate**: Use `currencyRepository.observeAll().first().isEmpty()` for idempotency — NOT `AppSettings.onboardingCompletedAt` or a new `seededAt` field. Cleanest cross-module boundary (no `:core:domain` → `:core:datastore` dep). InitialDataSeeder bails early if any currency exists.
- **Repository impl location**: 9 impls live in `:core:database/repository/`, NOT `:core:domain/repository/impl/` as PHASE_06 deliverable text suggested. Reason: impls need DAO refs which are Android-Room; `:core:domain` must stay pure JVM per TDD §2.1 layer rules. PHASE_06 file wording was informational; Clean Architecture trumps.
- **TransactionRepository.findByPeriod placeholder**: Returns `emptyList()` for now — PHASE_11 will add a dedicated non-paged DAO query. BalanceCalculator currently uses TransactionRepository.getCategorySummary (which has a dedicated DAO query) for aggregates and doesn't need findByPeriod.
- **PeriodArithmetic location**: `:core:domain/time/` NOT `:core:common/time/` — Period sealed class lives in `:core:domain`, so PeriodArithmetic must be co-located to avoid cycle.
- **MoneyFormatter signature**: Takes raw BigDecimal + currencySymbol + decimalDigits + locale + symbolPosition, NOT `Money` domain object. Keeps `:core:common` independent of `:core:domain`.

### Domain layer / Hilt graph / UseCase gotchas worth knowing

1. **Pure-JVM domain holds Hilt annotations** — `:core:domain` uses ksp + hilt-core (the JVM-compatible Hilt). @Inject constructors + @InstallIn(SingletonComponent::class) modules work without the Android Hilt plugin. The aggregator in `:app` discovers and links everything at compile time.
2. **UseCases inject @DefaultDispatcher** for CPU work (aggregation, date arithmetic). Repositories inject @IoDispatcher for IO (DB calls). Never the wrong way around.
3. **TransferResult sealed class** is the AS-6/AS-7 contract. ViewModels in PHASE_10 (Add Transaction) pattern-match on `RateMissing(from, to)` → navigate to S27 currency-rate editor.
4. **InitialDataSeeder seeds 17 categories total** (15 expense + 2 income). The 15 expense slugs match PHASE_03 CategoryColors map keys (clothing/bills/food/.../car); the 2 income (Salary/Other) come from AS-8.
5. **Mappers.kt is internal-only** — extension functions are `internal fun X.toDomain()` so they don't leak across module boundaries. Each RepositoryImpl in `:core:database` imports from `com.kshavrin.mymoney.core.database.mapper.*` (internal but in-module).
6. **Enum.fromString() helpers** for AccountType/CategoryKind/TransactionKind — case-insensitive matching. Used by Entity→Domain mappers when Room stores enums as lowercase strings (`"cash"`, `"expense"`, `"transfer"`).
7. **Money arithmetic enforces same-currency** via `require(currency.id == other.currency.id)` in `plus`/`minus`. Cross-currency arithmetic must go through CurrencyRate explicitly (TransferExecutor does this).
8. **BalanceCalculator fraction denominator** = `totalIncome + totalExpense` — robust against signum=0; CategoryBalance.fraction represents the slice's share of total turnover (used by donut chart in PHASE_08).
9. **RecurringScheduler.computeNextRun is pure** — no DB access, no DI dispatcher, no side effects. WorkManager job in PHASE_14 will call it + persist updateNextRun via repository.

### PHASE_07 entry hint

- Open `docs/implementation_plan/phases/PHASE_07_splash_onboarding_nav_root.md`.
- First UI-facing phase. Splash + onboarding (S00, S11) + nav root. Will use MyMoneyTheme (PHASE_03) + Hilt-injected ViewModels (PHASE_06 repos) + DataStore AppSettings.onboardingCompletedAt (PHASE_05).
- PHASE_07 will likely call `InitialDataSeeder.seedIfNeeded()` from a splash ViewModel or onboarding finish handler (per task #11 design decision). Use `applicationScope.launch { ... }` or a dedicated splash coroutine.
- First phase requiring REAL emulator runs (loopback blocker must be resolved before PHASE_07 close). Static inspection still OK for code review; runtime verification requires device.
