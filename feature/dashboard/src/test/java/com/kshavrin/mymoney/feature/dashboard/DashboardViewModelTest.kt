package com.kshavrin.mymoney.feature.dashboard

import androidx.paging.PagingData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.BudgetEvaluator
import com.kshavrin.mymoney.core.domain.usecase.ObserveBudgetAlertsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = DashboardMainDispatcherRule(StandardTestDispatcher())

    private val initialPeriod = Period.Month(YearMonth.now())
    private val april = Period.Month(YearMonth.of(2026, 4))
    private val usd = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )
    private val cash = account(id = 1L, name = "Cash", isDefault = true)
    private val card = account(id = 2L, name = "Card", isDefault = false)

    private lateinit var accountRepository: FakeDashboardAccountRepository
    private lateinit var currencyRepository: FakeDashboardCurrencyRepository
    private lateinit var transactionRepository: FakeDashboardTransactionRepository
    private lateinit var budgetRepository: FakeDashboardBudgetRepository
    private lateinit var settingsRepository: FakeDashboardAppSettingsRepository

    @Before
    fun setUp() {
        accountRepository = FakeDashboardAccountRepository().apply { seed(cash, card) }
        currencyRepository = FakeDashboardCurrencyRepository().apply { seed(usd) }
        transactionRepository = FakeDashboardTransactionRepository()
        budgetRepository = FakeDashboardBudgetRepository()
        settingsRepository = FakeDashboardAppSettingsRepository(
            AppSettings(defaultAccountId = cash.id, firstPositiveSeen = true),
        )
    }

    @Test
    fun `alerted categories mark donut slices and the largest overage is retained for the chip`() = runTest {
        transactionRepository.seedExpenseSummary(
            cash.id,
            initialPeriod,
            summary(categoryId = 10L, amount = "85.00"),
            summary(categoryId = 20L, amount = "112.00"),
            summary(categoryId = 30L, amount = "135.00"),
        )
        budgetRepository.seed(
            budget(id = 10L, categoryId = 10L),
            budget(id = 20L, categoryId = 20L),
            budget(id = 30L, categoryId = 30L),
        )

        val (viewModel, store) = buildViewModel()
        try {
            runCurrent()

            val state = viewModel.state.value
            assertEquals(setOf(10L, 20L, 30L), state.budgetAlertCategoryIds)
            assertTrue(state.slices.single { it.categoryId == 10L }.hasBudgetAlert)
            assertTrue(state.slices.single { it.categoryId == 20L }.hasBudgetAlert)
            assertTrue(state.slices.single { it.categoryId == 30L }.hasBudgetAlert)
            assertNotNull(state.overBudgetAmount)
            assertEquals(usd, state.overBudgetAmount!!.currency)
            assertEquals(0, BigDecimal("35.00").compareTo(state.overBudgetAmount!!.amount))
        } finally {
            store.clear()
            runCurrent()
        }
    }

    @Test
    fun `changing display period keeps budget alerts tied to the current month`() = runTest {
        transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "135.00"))
        transactionRepository.seedExpenseSummary(cash.id, april, summary(categoryId = 20L, amount = "85.00"))
        budgetRepository.seed(budget(id = 10L, categoryId = 10L), budget(id = 20L, categoryId = 20L))

        val (viewModel, store) = buildViewModel()
        try {
            runCurrent()
            assertEquals(setOf(10L), viewModel.state.value.budgetAlertCategoryIds)
            assertNotNull(viewModel.state.value.overBudgetAmount)
            assertEquals(0, BigDecimal("35.00").compareTo(viewModel.state.value.overBudgetAmount!!.amount))

            viewModel.onEvent(DashboardEvent.PeriodChanged(april))

            runCurrent()
            assertEquals(april, viewModel.state.value.period)
            assertEquals(setOf(10L), viewModel.state.value.budgetAlertCategoryIds)
            assertNotNull(viewModel.state.value.overBudgetAmount)
            assertEquals(0, BigDecimal("35.00").compareTo(viewModel.state.value.overBudgetAmount!!.amount))
            assertFalse(viewModel.state.value.slices.any { it.hasBudgetAlert })
        } finally {
            store.clear()
            runCurrent()
        }
    }

    @Test
    fun `changing account clears stale budget state and selects alerts for the new account`() = runTest {
        transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "135.00"))
        transactionRepository.seedExpenseSummary(card.id, initialPeriod, summary(categoryId = 20L, amount = "112.00"))
        budgetRepository.seed(budget(id = 10L, categoryId = 10L), budget(id = 20L, categoryId = 20L))

        val (viewModel, store) = buildViewModel()
        try {
            runCurrent()
            assertEquals(setOf(10L), viewModel.state.value.budgetAlertCategoryIds)

            viewModel.onEvent(DashboardEvent.AccountChanged(card.id))

            assertTrue(viewModel.state.value.budgetAlertCategoryIds.isEmpty())
            assertNull(viewModel.state.value.overBudgetAmount)
            assertFalse(viewModel.state.value.slices.any { it.hasBudgetAlert })

            runCurrent()
            assertEquals(card, viewModel.state.value.currentAccount)
            assertEquals(setOf(20L), viewModel.state.value.budgetAlertCategoryIds)
            assertNotNull(viewModel.state.value.overBudgetAmount)
            assertEquals(0, BigDecimal("12.00").compareTo(viewModel.state.value.overBudgetAmount!!.amount))
            assertTrue(viewModel.state.value.slices.single { it.categoryId == 20L }.hasBudgetAlert)
        } finally {
            store.clear()
            runCurrent()
        }
    }

    @Test
    fun `donut slices carry the iconKey copied from each category balance`() = runTest {
        transactionRepository.seedExpenseSummary(
            cash.id,
            initialPeriod,
            summary(categoryId = 10L, amount = "85.00", iconKey = "food"),
            summary(categoryId = 20L, amount = "112.00", iconKey = "transport"),
        )

        val (viewModel, store) = buildViewModel()
        try {
            runCurrent()

            val slices = viewModel.state.value.slices
            assertEquals("food", slices.single { it.categoryId == 10L }.iconKey)
            assertEquals("transport", slices.single { it.categoryId == 20L }.iconKey)
        } finally {
            store.clear()
            runCurrent()
        }
    }

    private fun buildViewModel(): Pair<DashboardViewModel, ViewModelStore> {
        val dispatcher = mainDispatcherRule.testDispatcher
        val calculator = BalanceCalculator(
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            transactionRepository = transactionRepository,
            defaultDispatcher = dispatcher,
        )
        val alerts = ObserveBudgetAlertsUseCase(
            transactionRepository = transactionRepository,
            budgetRepository = budgetRepository,
            balanceCalculator = calculator,
            budgetEvaluator = BudgetEvaluator(),
            defaultDispatcher = dispatcher,
        )
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(
                accountRepository = accountRepository,
                currencyRepository = currencyRepository,
                balanceCalculator = calculator,
                appSettingsRepository = settingsRepository,
                transactionRepository = transactionRepository,
                observeBudgetAlertsUseCase = alerts,
            ) as T
        }
        return ViewModelProvider(store, factory)[DashboardViewModel::class.java] to store
    }

    private fun summary(categoryId: Long, amount: String, iconKey: String = "") = CategorySummary(
        categoryId = categoryId,
        categoryName = "category-$categoryId",
        colorHex = "#FF8888",
        total = BigDecimal(amount),
        iconKey = iconKey,
    )

    private fun budget(id: Long, categoryId: Long) = Budget(
        id = id,
        categoryId = categoryId,
        periodKind = "month",
        periodStart = Instant.EPOCH,
        amount = BigDecimal("100.00"),
        currencyId = usd.id,
        alertThresholdPct = 80,
        isActive = true,
    )

    private fun account(id: Long, name: String, isDefault: Boolean) = Account(
        id = id,
        name = name,
        currencyId = usd.id,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_cash",
        isDefault = isDefault,
        sortOrder = id.toInt(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        isArchived = false,
    )
}

private class FakeDashboardAppSettingsRepository(initial: AppSettings) : AppSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private class FakeDashboardAccountRepository : AccountRepository {
    private val state = MutableStateFlow<List<Account>>(emptyList())

    fun seed(vararg accounts: Account) {
        state.value = accounts.toList()
    }

    override fun observeActive(): Flow<List<Account>> = state.asStateFlow()
    override suspend fun findById(id: Long): Account? = state.value.firstOrNull { it.id == id }
    override suspend fun findDefault(): Account? = state.value.firstOrNull { it.isDefault }
    override suspend fun computeBalance(accountId: Long): BigDecimal = BigDecimal.ZERO
    override suspend fun upsert(account: Account): Long = account.id
    override suspend fun archive(id: Long) = Unit
    override suspend fun setDefault(id: Long) = Unit
    override suspend fun countByCurrency(currencyId: Long): Int = 0
}

private class FakeDashboardCurrencyRepository : CurrencyRepository {
    private val state = MutableStateFlow<List<Currency>>(emptyList())

    fun seed(vararg currencies: Currency) {
        state.value = currencies.toList()
    }

    override fun observeActive(): Flow<List<Currency>> = state.asStateFlow()
    override fun observeAll(): Flow<List<Currency>> = state.asStateFlow()
    override suspend fun findById(id: Long): Currency? = state.value.firstOrNull { it.id == id }
    override suspend fun findByCode(code: String): Currency? = state.value.firstOrNull { it.code == code }
    override suspend fun upsert(currency: Currency): Long = currency.id
    override suspend fun upsertAll(currencies: List<Currency>) = Unit
    override suspend fun setActive(id: Long, active: Boolean) = Unit
}

private class FakeDashboardBudgetRepository : BudgetRepository {
    private val state = MutableStateFlow<List<Budget>>(emptyList())

    fun seed(vararg budgets: Budget) {
        state.value = budgets.toList()
    }

    override fun observeActive(): Flow<List<Budget>> = state.asStateFlow()
    override suspend fun findForCategory(categoryId: Long): Budget? = state.value.firstOrNull { it.categoryId == categoryId }
    override suspend fun findTotalBudget(): Budget? = state.value.firstOrNull { it.categoryId == null }
    override suspend fun upsert(budget: Budget): Long = budget.id
    override suspend fun deactivate(id: Long) = Unit
}

private class FakeDashboardTransactionRepository : TransactionRepository {
    private val state = MutableStateFlow<List<Transaction>>(emptyList())
    private val expenseSummaries = mutableMapOf<Pair<Long, Period>, List<CategorySummary>>()

    fun seedExpenseSummary(accountId: Long, period: Period, vararg summaries: CategorySummary) {
        expenseSummaries[accountId to period] = summaries.toList()
    }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = state.asStateFlow()
    override fun observeAll(): Flow<List<Transaction>> = state.asStateFlow()
    override fun paged(accountId: Long, categoryId: Long?, from: Instant, to: Instant): Flow<PagingData<Transaction>> =
        flowOf(PagingData.empty())
    override suspend fun findById(id: Long): Transaction? = null
    override suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction> = emptyList()
    override suspend fun getCategorySummary(
        accountId: Long,
        period: Period,
        kind: TransactionKind,
    ): List<CategorySummary> = if (kind == TransactionKind.Expense) {
        expenseSummaries[accountId to period].orEmpty()
    } else {
        emptyList()
    }
    override suspend fun searchByNote(query: String, limit: Int): List<Transaction> = emptyList()
    override suspend fun upsert(transaction: Transaction): Long = transaction.id
    override suspend fun softDelete(id: Long, now: Instant) = Unit
    override suspend fun restore(id: Long, now: Instant) = Unit
    override suspend fun pruneDeleted(before: Instant) = Unit
    override suspend fun countByAccount(id: Long): Int = 0
    override suspend fun countByCategory(id: Long): Int = 0
    override suspend fun countByCurrency(id: Long): Int = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardMainDispatcherRule(
    val testDispatcher: TestDispatcher,
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
