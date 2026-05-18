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

- [ ] Re-read §2.1 layer rules (UI → VM → UseCase or Repository → Repository → DAO). Internalise that domain models must not leak Room types upward.
- [ ] Build `Money` value object. Confirm equality + arithmetic (`plus`, `minus`, `times`) use `BigDecimal.add` etc. with scale `currency.decimalDigits`.
- [ ] Build `Period` sealed class including `CustomRange` per AS-12. Provide `displayName` for the period strip in PHASE_08.
- [ ] Domain models: one per Room entity. Mappers `XxxEntity.toDomain()` + `Xxx.toEntity()` live in the impl file (next to the repository impl using them).
- [ ] Repository interfaces — define every method needed by feature modules. Look ahead: PHASE_08 dashboard needs `accountRepository.observeActive()`, `transactionRepository.observePagedByPeriod(...)`. PHASE_09 dictionaries need full CRUD. PHASE_10 add-transaction needs `transactionRepository.insert(...)`. List all upfront so future phases don't need to mutate the interface.
- [ ] Default impls: simple delegation to DAO + mappers + validation. Catch + rethrow `SyncException` only at the sync repository (PHASE_13); domain repositories propagate `IllegalArgumentException` etc.
- [ ] Implement `BalanceCalculator` using DAO `findByPeriod` + Kotlin reduce. Be careful about transfers: a transfer's `amount` is debited from `accountId`, `toAmount` credited to `toAccountId`. For "account view", transfers in/out depending on which side this account is.
- [ ] Implement `TransferExecutor` per AS-7: write a **single `TransactionEntity`** row with `kind = "transfer"`, `accountId`, `toAccountId`, `amount`, `toAmount`, `exchangeRate`. Per AS-6: if cross-currency and rate missing, return `AppResult.Failure(SyncError.Conflict)` or a dedicated `TransferError.RateMissing(fromId, toId)` sum type so the VM can decide to navigate to S27.
- [ ] Implement `BudgetEvaluator` (pure function). Tested with sample data.
- [ ] Implement `RecurringScheduler.computeNextRun(template, now)`. Branches by `recurrenceKind` ∈ `daily / weekly / monthly / yearly`. Handles `byDay` masks for weekly.
- [ ] Implement `InitialDataSeeder`. Decide where to call from — `MyMoneyApp.onCreate` is wrong (blocks main thread); use `applicationScope.launch` (`@Singleton CoroutineScope` provided in `:core:common`). Or wire from `MainActivity`'s `LaunchedEffect(Unit)`. Document the choice in Notes.
- [ ] Confirm the seeder is idempotent: running it twice does not duplicate rows. Use `AppSettings.seededAt` flag.
- [ ] **Decision to log**: TDD §7.3 doesn't list `seededAt` in `AppSettings`. We need it. Either add `seededAt: Long?` to `AppSettings` (DataStore) — update `AppSettings` model + keys (PHASE_05 deliverable) — OR use the existing `onboardingCompletedAt` as the seed gate (simpler). Pick one, log to PROGRESS decisions, and update the relevant model.
- [ ] Money formatter — EN: `$1,234.56`, RU: `1 234,56 ₽`. Use `NumberFormat` + override the symbol from `Currency.symbol` (so users can change the symbol per S26 currency edit).
- [ ] Write tests for BalanceCalculator (a fixture: 1 account, 3 categories, 10 transactions; assert percentages, totals).
- [ ] Run `:core:domain:test` and `:core:common:test`.
- [ ] Update PROGRESS.md.

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

(empty — fill at end of session. Record the decision on `seededAt` vs `onboardingCompletedAt`.)
