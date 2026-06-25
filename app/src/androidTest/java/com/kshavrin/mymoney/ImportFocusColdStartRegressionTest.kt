package com.kshavrin.mymoney

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.repository.AccountRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.BackupRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.BudgetRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.CategoryRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.CurrencyRepositoryImpl
import com.kshavrin.mymoney.core.database.repository.TransactionRepositoryImpl
import com.kshavrin.mymoney.core.datastore.AppSettingsRepositoryImpl
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.BalanceTrendCalculator
import com.kshavrin.mymoney.core.domain.usecase.BudgetEvaluator
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetOperationsSummaryUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetTransferRecordsUseCase
import com.kshavrin.mymoney.core.domain.usecase.IntradayTrendCalculator
import com.kshavrin.mymoney.core.domain.usecase.ObserveBudgetAlertsUseCase
import com.kshavrin.mymoney.feature.dashboard.DashboardSelection
import com.kshavrin.mymoney.feature.dashboard.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

/**
 * Integration regression for the import-focus → cold-started DashboardViewModel → real-data path.
 *
 * Crosses the REAL seam the dashboard fakes paper over: real in-memory Room + a real
 * PreferenceDataStore over a temp file + the real BackupRepositoryImpl, AppSettingsRepositoryImpl
 * and BalanceCalculator. A real Monefy CSV is imported through the real parseImport/commitImport
 * (the focus epoch & currency are COMPUTED, never hand-set), the focus is persisted exactly as the
 * import wizard's commit() does, then a FRESH DashboardViewModel is built to simulate a process
 * restart. It must still surface the imported rows.
 *
 * The defect this pins: importMonefyCsv resolves the import currency through findByCode, which
 * returns a currency regardless of its is_active flag, and never re-activates it. A user who turned
 * the currency off in the currency list (Monefy ships many currencies off by default) imports into
 * an INACTIVE currency. The persisted focus currencyId then points at a currency the rebuilt
 * dashboard never sees, because observeActive() filters it out — so importFocusSelection resolves
 * to null and the dashboard falls back to the empty seed account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ImportFocusColdStartRegressionTest {
    private lateinit var db: MoneyDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
                .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = File(context.cacheDir, "import_focus_regression_${System.nanoTime()}.preferences_pb")
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            )
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun importedRowsSurviveColdStartAndShowOnDashboard() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val io = Dispatchers.IO
            val default = Dispatchers.Default

            val currencyRepository = CurrencyRepositoryImpl(db.currencyDao(), io)
            val accountRepository = AccountRepositoryImpl(db.accountDao(), io)
            val categoryRepository = CategoryRepositoryImpl(db.categoryDao(), io)
            val transactionRepository = TransactionRepositoryImpl(db.transactionDao(), io)
            val budgetRepository = BudgetRepositoryImpl(db.budgetDao(), io)
            val settingsRepository = AppSettingsRepositoryImpl(dataStore)
            val backupRepository = BackupRepositoryImpl(context, db, io)
            val balanceCalculator =
                BalanceCalculator(accountRepository, currencyRepository, transactionRepository, default)
            val observeBudgetAlerts =
                ObserveBudgetAlertsUseCase(
                    transactionRepository,
                    budgetRepository,
                    balanceCalculator,
                    BudgetEvaluator(),
                    default,
                )

            // Fresh-install seed equivalent: USD is the active default-account currency. RUB —
            // the currency the Monefy export uses — has been switched OFF in the currency list,
            // exactly as a user who only wants a couple of currencies visible would do.
            db.currencyDao().upsert(CurrencyEntity(code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 0))
            db.currencyDao().upsert(CurrencyEntity(code = "RUB", symbol = "₽", name = "Russian Ruble", decimalDigits = 2, isActive = false, sortOrder = 2))
            val now = Instant.now()
            val usd = currencyRepository.observeActive().first().first { it.code == "USD" }
            val seededAccountId =
                accountRepository.upsert(
                    Account(
                        id = 0L,
                        name = "Cash",
                        currencyId = usd.id,
                        initialBalance = BigDecimal.ZERO,
                        type = AccountType.Cash,
                        colorHex = "#7AC794",
                        iconKey = "ic_account_cash",
                        isDefault = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                        isArchived = false,
                    ),
                )
            settingsRepository.update {
                it.copy(
                    defaultAccountId = seededAccountId,
                    dashboardSelectionMode = "specific_account",
                    onboardingCompletedAt = now.toEpochMilli(),
                )
            }

            // Real Monefy CSV (dd/MM/yyyy, signed amount, RUB), imported through the real
            // parse → commit chain. The focus epoch & currency are computed by the importer.
            val csvFile = File(context.cacheDir, "import_focus_regression.csv")
            csvFile.writeText(
                buildString {
                    append("date,account,category,amount,currency,converted amount,currency,description\r\n")
                    append("05/01/2020,Наличные,Продукты,-1500,RUB,-1500,RUB,\r\n")
                    append("06/01/2020,Наличные,Зарплата,50000,RUB,50000,RUB,\r\n")
                },
                Charsets.UTF_8,
            )

            val staged = backupRepository.parseImport("file://${csvFile.absolutePath}").getOrThrow()
            val focus =
                backupRepository
                    .commitImport(
                        staged,
                        ImportPlan(
                            dataStrategy = ImportDataStrategy.Append,
                            categoryStrategy = ImportCategoryStrategy.Append,
                        ),
                    ).getOrThrow()
            assertNotNull("Import must compute a focus", focus)

            // Persist the focus exactly as ImportWizardViewModel.commit() does.
            settingsRepository.update {
                it.copy(
                    importFocusEpochMs = focus!!.occurredAtEpochMs,
                    importFocusCurrencyId = focus.currencyId,
                )
            }

            // Simulate a process restart: a brand-new ViewModel reading the persisted
            // DataStore + Room, with no in-memory selection carried over.
            Dispatchers.setMain(default)
            try {
                val viewModel =
                    DashboardViewModel(
                        accountRepository = accountRepository,
                        currencyRepository = currencyRepository,
                        balanceCalculator = balanceCalculator,
                        balanceTrendCalculator = BalanceTrendCalculator(),
                        intradayTrendCalculator = IntradayTrendCalculator(),
                        appSettingsRepository = settingsRepository,
                        transactionRepository = transactionRepository,
                        observeBudgetAlertsUseCase = observeBudgetAlerts,
                        categoryRepository = categoryRepository,
                        getOperationsSummary =
                            GetOperationsSummaryUseCase(
                                getCategoryRecords =
                                    GetCategoryRecordsUseCase(
                                        accountRepository = accountRepository,
                                        currencyRepository = currencyRepository,
                                        transactionRepository = transactionRepository,
                                        defaultDispatcher = default,
                                    ),
                                getTransferRecords =
                                    GetTransferRecordsUseCase(
                                        currencyRepository = currencyRepository,
                                        transactionRepository = transactionRepository,
                                        defaultDispatcher = default,
                                    ),
                            ),
                        resolveRateUseCase =
                            com.kshavrin.mymoney.core.domain.usecase.ResolveRateUseCase(
                                currencyRateRepository = NoRatesCurrencyRateRepository(),
                                currencyRepository = currencyRepository,
                                convertMoney =
                                    com.kshavrin.mymoney.core.domain.usecase
                                        .ConvertMoneyUseCase(),
                                clock = java.time.Clock.systemUTC(),
                            ),
                    )

                val populated =
                    withTimeoutOrNull(5_000) {
                        var resolved: BigDecimal? = null
                        while (resolved == null) {
                            val state = viewModel.state.value
                            val snapshot = state.balanceSnapshot
                            if (!state.isLoading &&
                                snapshot != null &&
                                (snapshot.expense.amount.signum() > 0 || snapshot.income.amount.signum() > 0)
                            ) {
                                resolved = snapshot.expense.amount
                            } else {
                                delay(50)
                            }
                        }
                        viewModel.state.value
                    }

                assertNotNull("Dashboard never surfaced the imported rows after cold start", populated)
                val state = populated!!
                assertTrue(
                    "Cold-started dashboard must resolve the imported currency selection, was ${state.dashboardSelection}",
                    state.dashboardSelection is DashboardSelection.AllAccounts,
                )
                val snapshot = state.balanceSnapshot!!
                assertTrue(
                    "Expected non-empty imported figures, got income=${snapshot.income.amount} expense=${snapshot.expense.amount}",
                    snapshot.income.amount.signum() > 0 && snapshot.expense.amount.signum() > 0,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }
}

// Import focus restores in Separate mode, which never converts, so the rate repository is unused.
private class NoRatesCurrencyRateRepository : com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository {
    override suspend fun findRate(
        fromCurrencyId: Long,
        toCurrencyId: Long,
    ): com.kshavrin.mymoney.core.domain.model.CurrencyRate? = null

    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.kshavrin.mymoney.core.domain.model.CurrencyRate>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun upsert(rate: com.kshavrin.mymoney.core.domain.model.CurrencyRate): Long = rate.id

    override suspend fun deleteById(id: Long) = Unit

    override suspend fun refreshRatesFromNetwork(): Result<Int> = Result.success(0)
}
