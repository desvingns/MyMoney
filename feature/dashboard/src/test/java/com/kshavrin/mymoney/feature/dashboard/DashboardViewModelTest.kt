package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingData
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryBalance
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.BalanceTrendCalculator
import com.kshavrin.mymoney.core.domain.usecase.BudgetEvaluator
import com.kshavrin.mymoney.core.domain.usecase.ConvertMoneyUseCase
import com.kshavrin.mymoney.core.domain.usecase.ObserveBudgetAlertsUseCase
import com.kshavrin.mymoney.core.domain.usecase.ResolveRateUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = DashboardMainDispatcherRule(StandardTestDispatcher())

    private val initialPeriod = Period.Month(YearMonth.now())
    private val april = Period.Month(YearMonth.of(2026, 4))
    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )
    private val eur =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "EUR",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )
    private val cash = account(id = 1L, name = "Cash", isDefault = true)
    private val card = account(id = 2L, name = "Card", isDefault = false)

    private lateinit var accountRepository: FakeDashboardAccountRepository
    private lateinit var currencyRepository: FakeDashboardCurrencyRepository
    private lateinit var transactionRepository: FakeDashboardTransactionRepository
    private lateinit var budgetRepository: FakeDashboardBudgetRepository
    private lateinit var settingsRepository: FakeDashboardAppSettingsRepository
    private lateinit var categoryRepository: FakeDashboardCategoryRepository

    @Before
    fun setUp() {
        accountRepository = FakeDashboardAccountRepository().apply { seed(cash, card) }
        currencyRepository = FakeDashboardCurrencyRepository().apply { seed(usd) }
        transactionRepository = FakeDashboardTransactionRepository()
        budgetRepository = FakeDashboardBudgetRepository()
        settingsRepository =
            FakeDashboardAppSettingsRepository(
                AppSettings(defaultAccountId = cash.id, firstPositiveSeen = true),
            )
        categoryRepository = FakeDashboardCategoryRepository()
    }

    @Test
    fun `alerted categories mark donut slices and the largest overage is retained for the chip`() =
        runTest {
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
                assertTrue(state.expenseTiles.single { it.categoryId == 10L }.hasBudgetAlert)
                assertTrue(state.expenseTiles.single { it.categoryId == 20L }.hasBudgetAlert)
                assertTrue(state.expenseTiles.single { it.categoryId == 30L }.hasBudgetAlert)
                assertNotNull(state.overBudgetAmount)
                assertEquals(usd, state.overBudgetAmount!!.currency)
                assertEquals(0, BigDecimal("35.00").compareTo(state.overBudgetAmount!!.amount))
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `changing display period keeps budget alerts tied to the current month`() =
        runTest {
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "135.00"))
            transactionRepository.seedExpenseSummary(cash.id, april, summary(categoryId = 20L, amount = "85.00"))
            budgetRepository.seed(budget(id = 10L, categoryId = 10L), budget(id = 20L, categoryId = 20L))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertEquals(setOf(10L), viewModel.state.value.budgetAlertCategoryIds)
                assertNotNull(viewModel.state.value.overBudgetAmount)
                assertEquals(
                    0,
                    BigDecimal("35.00").compareTo(
                        viewModel.state.value.overBudgetAmount!!
                            .amount,
                    ),
                )

                viewModel.onEvent(DashboardEvent.PeriodChanged(april))

                runCurrent()
                assertEquals(april, viewModel.state.value.period)
                assertEquals(setOf(10L), viewModel.state.value.budgetAlertCategoryIds)
                assertNotNull(viewModel.state.value.overBudgetAmount)
                assertEquals(
                    0,
                    BigDecimal("35.00").compareTo(
                        viewModel.state.value.overBudgetAmount!!
                            .amount,
                    ),
                )
                assertFalse(
                    viewModel.state.value.slices
                        .any { it.hasBudgetAlert },
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `changing account clears stale budget state and selects alerts for the new account`() =
        runTest {
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "135.00"))
            transactionRepository.seedExpenseSummary(card.id, initialPeriod, summary(categoryId = 20L, amount = "112.00"))
            budgetRepository.seed(budget(id = 10L, categoryId = 10L), budget(id = 20L, categoryId = 20L))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertEquals(setOf(10L), viewModel.state.value.budgetAlertCategoryIds)

                viewModel.onEvent(DashboardEvent.AccountSelected(card.id))

                assertTrue(
                    viewModel.state.value.budgetAlertCategoryIds
                        .isEmpty(),
                )
                assertNull(viewModel.state.value.overBudgetAmount)
                assertFalse(
                    viewModel.state.value.slices
                        .any { it.hasBudgetAlert },
                )

                runCurrent()
                assertEquals(card, viewModel.state.value.currentAccount)
                assertEquals(setOf(20L), viewModel.state.value.budgetAlertCategoryIds)
                assertNotNull(viewModel.state.value.overBudgetAmount)
                assertEquals(
                    0,
                    BigDecimal("12.00").compareTo(
                        viewModel.state.value.overBudgetAmount!!
                            .amount,
                    ),
                )
                assertTrue(
                    viewModel.state.value.slices
                        .single { it.categoryId == 20L }
                        .hasBudgetAlert,
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `changing account updates the current currency and persists the selected account id`() =
        runTest {
            val euroCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, euroCard)
            currencyRepository.seed(usd, eur)

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AccountSelected(euroCard.id))

                runCurrent()
                assertEquals(euroCard, viewModel.state.value.currentAccount)
                assertEquals(eur, viewModel.state.value.currentCurrency)
                assertEquals(euroCard.id, settingsRepository.currentSettings().defaultAccountId)
                assertEquals("specific_account", settingsRepository.currentSettings().dashboardSelectionMode)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts selected emits fork dialog action and does not immediately change selection`() =
        runTest {
            accountRepository.seed(cash, card)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()
                val selectionBefore = viewModel.state.value.dashboardSelection

                viewModel.onEvent(DashboardEvent.AllAccountsSelected)
                runCurrent()

                assertEquals(listOf(DashboardAction.ShowAllAccountsModeDialog), actions)
                // Selection must NOT change until the user completes the fork-dialog flow.
                assertEquals(selectionBefore, viewModel.state.value.dashboardSelection)
                // Left drawer closes on AllAccountsSelected.
                assertFalse(viewModel.state.value.leftDrawerOpen)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts convert chosen emits target currency picker with all active currencies`() =
        runTest {
            currencyRepository.seed(usd, eur)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsConvertChosen)
                runCurrent()

                assertEquals(1, actions.size)
                val picker = actions.single() as DashboardAction.ShowTargetCurrencyPicker
                assertEquals(listOf(usd, eur), picker.currencies)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts target currency chosen emits rate confirm with rows for every source currency`() =
        runTest {
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurCard)
            currencyRepository.seed(usd, eur)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                // Choose target = USD. The EUR account is the only source that differs.
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()

                assertEquals(1, actions.size)
                val rateConfirm = actions.single() as DashboardAction.ShowAllAccountsRateConfirm
                assertEquals(1, rateConfirm.rows.size)
                assertEquals("EUR", rateConfirm.rows.single().fromCode)
                assertEquals("USD", rateConfirm.rows.single().toCode)
                assertEquals(listOf(eur.id), rateConfirm.sourceCurrencyIds)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts target currency chosen skips rate confirm when every account is already in the target`() =
        runTest {
            accountRepository.seed(cash, card)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                // Choose target = USD. All accounts are already in USD → no rows to confirm.
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()

                // ShowAllAccountsRateConfirm must NOT be emitted; selection jumps straight to ConvertTo.
                assertTrue(actions.none { it is DashboardAction.ShowAllAccountsRateConfirm })
                val selection = viewModel.state.value.dashboardSelection
                assertTrue(selection is DashboardSelection.AllAccounts)
                assertEquals(
                    usd,
                    (selection as DashboardSelection.AllAccounts)
                        .foldMode
                        .let { (it as AllAccountsFoldMode.ConvertTo).target },
                )
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts rates confirmed sets ConvertTo selection and aggregates balance per currency group`() =
        runTest {
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurCard)
            currencyRepository.seed(usd, eur)
            // USD group: expense 100
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "100.00"))
            // EUR group: expense 50 EUR — at rate 1.2 USD/EUR → 60 USD
            transactionRepository.seedExpenseSummary(eurCard.id, initialPeriod, summary(categoryId = 20L, amount = "50.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                // Simulate the full flow: TargetCurrencyChosen → then RatesConfirmed with 1 EUR = 1.2 USD
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                // Rate override: eur.id → 1.2
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(mapOf(eur.id to BigDecimal("1.2"))))
                runCurrent()

                val state = viewModel.state.value
                val selection = state.dashboardSelection
                assertTrue(selection is DashboardSelection.AllAccounts)
                assertEquals(AllAccountsFoldMode.ConvertTo(usd), (selection as DashboardSelection.AllAccounts).foldMode)
                assertNotNull(state.balanceSnapshot)
                // USD group expense=100 + EUR group expense=50*1.2=60 → total=160
                assertEquals(0, BigDecimal("160.00").compareTo(state.balanceSnapshot!!.expense.amount))
                assertEquals(usd, state.balanceSnapshot!!.expense.currency)
                assertEquals("all_accounts", settingsRepository.currentSettings().dashboardSelectionMode)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts rates confirmed does not write overrides to CurrencyRateRepository`() =
        runTest {
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurCard)
            currencyRepository.seed(usd, eur)

            val trackingRateRepository = TrackingCurrencyRateRepository()
            val (viewModel, store) = buildViewModelWith(rateRepository = trackingRateRepository)
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(mapOf(eur.id to BigDecimal("1.5"))))
                runCurrent()

                // D5: one-shot rate edits must never be persisted (no upsert calls).
                assertEquals(0, trackingRateRepository.upsertCallCount)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts target currency is not remembered — repeated AllAccountsSelected re-opens the fork dialog`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                // First full flow: select all accounts with USD target.
                viewModel.onEvent(DashboardEvent.AllAccountsSelected)
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsConvertChosen)
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(emptyMap()))
                runCurrent()

                actions.clear()

                // D7: second tap on "All accounts" must open the fork dialog again — target is NOT remembered.
                viewModel.onEvent(DashboardEvent.AllAccountsSelected)
                runCurrent()

                assertEquals(listOf(DashboardAction.ShowAllAccountsModeDialog), actions)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts separate chosen sets Separate selection and persists aggregate mode`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val selection = viewModel.state.value.dashboardSelection
                assertTrue(selection is DashboardSelection.AllAccounts)
                assertEquals(
                    AllAccountsFoldMode.Separate,
                    (selection as DashboardSelection.AllAccounts).foldMode,
                )
                assertEquals("all_accounts", settingsRepository.currentSettings().dashboardSelectionMode)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts conversion dismissed does not change selection`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                val selectionBefore = viewModel.state.value.dashboardSelection

                viewModel.onEvent(DashboardEvent.AllAccountsConversionDismissed)
                runCurrent()

                assertEquals(selectionBefore, viewModel.state.value.dashboardSelection)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `currentCurrency returns target when ConvertTo is active and null when Separate`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd, eur)

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                // After ConvertTo(usd) flow:
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(emptyMap()))
                runCurrent()
                assertEquals(usd, viewModel.state.value.currentCurrency)

                // After SeparateChosen:
                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()
                assertNull(viewModel.state.value.currentCurrency)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts aggregation converts each currency group independently before summing`() =
        runTest {
            // G11: BalanceCalculator.forAccounts requires same-currency accounts.
            // Groups: USD accounts (cash+card) and EUR account (eurCard).
            // USD group: income=200, expense=80. EUR group: income=50, expense=30.
            // Rate: 1 EUR = 2.00 USD.
            // Expected totals in USD: income=200+50*2=300, expense=80+30*2=140.
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, card, eurCard)
            currencyRepository.seed(usd, eur)
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "50.00"))
            transactionRepository.seedExpenseSummary(card.id, initialPeriod, summary(categoryId = 10L, amount = "30.00"))
            transactionRepository.seedIncomeSummary(cash.id, initialPeriod, summary(categoryId = 200L, amount = "200.00"))
            transactionRepository.seedExpenseSummary(eurCard.id, initialPeriod, summary(categoryId = 30L, amount = "30.00"))
            transactionRepository.seedIncomeSummary(eurCard.id, initialPeriod, summary(categoryId = 200L, amount = "50.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(mapOf(eur.id to BigDecimal("2.0"))))
                runCurrent()

                assertNotNull(viewModel.state.value.balanceSnapshot)
                val snapshot = viewModel.state.value.balanceSnapshot!!
                assertEquals(0, BigDecimal("300.00").compareTo(snapshot.income.amount))
                assertEquals(0, BigDecimal("140.00").compareTo(snapshot.expense.amount))
                assertEquals(usd, snapshot.income.currency)
                assertEquals(usd, snapshot.expense.currency)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts balance and slice taps emit aggregate transactions actions without a fake account id after convert flow`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                // Complete the full convert flow to reach ConvertTo(usd) selection.
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(emptyMap()))
                runCurrent()
                actions.clear()

                viewModel.onEvent(DashboardEvent.BalanceCardClicked)
                viewModel.onEvent(DashboardEvent.SliceClicked(categoryId = 77L))

                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(initialPeriod)
                assertEquals(
                    listOf(
                        DashboardAction.NavigateTransactionsByCurrency(
                            currencyId = usd.id,
                            fromMillis = expectedRange.first,
                            toMillis = expectedRange.last,
                        ),
                        DashboardAction.NavigateTransactionsByCategory(
                            accountId = null,
                            currencyId = usd.id,
                            categoryId = 77L,
                            fromMillis = expectedRange.first,
                            toMillis = expectedRange.last,
                        ),
                    ),
                    actions,
                )
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts selection restores on init in Separate mode when no fold target was persisted`() =
        runTest {
            // D7: target currency is never remembered. A cold-started AllAccounts selection always
            // restores as Separate (no target) — the user must run the fork-dialog flow again to
            // choose ConvertTo.
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(
                        defaultAccountId = cash.id,
                        dashboardSelectionMode = "all_accounts",
                        firstPositiveSeen = true,
                    ),
                )
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "10.00"))
            transactionRepository.seedExpenseSummary(card.id, initialPeriod, summary(categoryId = 20L, amount = "15.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                assertNull(viewModel.state.value.currentAccount)
                // Separate mode has no fold target, so currentCurrency is null (D7).
                assertNull(viewModel.state.value.currentCurrency)
                assertTrue(viewModel.state.value.dashboardSelection is DashboardSelection.AllAccounts)
                assertEquals(
                    AllAccountsFoldMode.Separate,
                    (viewModel.state.value.dashboardSelection as DashboardSelection.AllAccounts).foldMode,
                )
                // Separate fallback snapshot uses the first USD group — still shows balance.
                assertEquals(
                    0,
                    BigDecimal("25.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `imported account emission updates all accounts balance`() =
        runTest {
            val importedCash = account(id = 4L, name = "Imported cash", isDefault = false)
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(
                        defaultAccountId = cash.id,
                        dashboardSelectionMode = "all_accounts",
                        firstPositiveSeen = true,
                    ),
                )
            accountRepository.seed(cash)
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "10.00"))
            transactionRepository.seedExpenseSummary(importedCash.id, initialPeriod, summary(categoryId = 20L, amount = "25.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertEquals(
                    0,
                    BigDecimal("10.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )

                accountRepository.seed(cash, importedCash)

                runCurrent()
                assertTrue(
                    viewModel.state.value.accounts
                        .any { it.id == importedCash.id },
                )
                assertEquals(
                    0,
                    BigDecimal("35.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `import focus signal selects imported month and all accounts and persists until navigation`() =
        runTest {
            val focusEpochMs = Instant.parse("2020-03-14T12:00:00Z").toEpochMilli()
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(
                        defaultAccountId = cash.id,
                        firstPositiveSeen = true,
                        importFocusEpochMs = focusEpochMs,
                        importFocusCurrencyId = usd.id,
                    ),
                )
            val focusPeriod = Period.Month(YearMonth.of(2020, 3))
            transactionRepository.seedExpenseSummary(cash.id, focusPeriod, summary(categoryId = 10L, amount = "42.00"))
            transactionRepository.seedExpenseSummary(card.id, focusPeriod, summary(categoryId = 20L, amount = "8.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                assertEquals(focusPeriod, viewModel.state.value.period)
                // D8: import focus lands on the single multi-currency "All accounts" entry. No fold
                // target was chosen (D7), so it defaults to Separate — currentCurrency is null and
                // the imported account is surfaced across every account, not pinned to one currency.
                val selection = viewModel.state.value.dashboardSelection
                assertTrue(selection is DashboardSelection.AllAccounts)
                assertEquals(
                    AllAccountsFoldMode.Separate,
                    (selection as DashboardSelection.AllAccounts).foldMode,
                )
                assertNull(viewModel.state.value.currentCurrency)
                assertTrue(
                    viewModel.state.value.accounts
                        .any { it.id == cash.id },
                )
                assertEquals(
                    0,
                    BigDecimal("50.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
                assertEquals(focusEpochMs, settingsRepository.currentSettings().importFocusEpochMs)
                assertEquals(usd.id, settingsRepository.currentSettings().importFocusCurrencyId)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `import focus survives a restart so a rebuilt view model still jumps to the imported month`() =
        runTest {
            val focusEpochMs = Instant.parse("2020-03-14T12:00:00Z").toEpochMilli()
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(
                        defaultAccountId = cash.id,
                        firstPositiveSeen = true,
                        importFocusEpochMs = focusEpochMs,
                        importFocusCurrencyId = usd.id,
                    ),
                )
            val focusPeriod = Period.Month(YearMonth.of(2020, 3))
            transactionRepository.seedExpenseSummary(cash.id, focusPeriod, summary(categoryId = 10L, amount = "42.00"))
            transactionRepository.seedExpenseSummary(card.id, focusPeriod, summary(categoryId = 20L, amount = "8.00"))

            val (firstViewModel, firstStore) = buildViewModel()
            try {
                runCurrent()
                assertEquals(focusPeriod, firstViewModel.state.value.period)
                assertTrue(firstViewModel.state.value.dashboardSelection is DashboardSelection.AllAccounts)
            } finally {
                firstStore.clear()
                runCurrent()
            }

            val (secondViewModel, secondStore) = buildViewModel()
            try {
                runCurrent()
                assertEquals(focusPeriod, secondViewModel.state.value.period)
                // D8: the rebuilt view model restores the multi-currency "All accounts" entry in
                // Separate mode (no remembered fold target — D7), so currentCurrency is null while
                // the imported month and account stay surfaced.
                val selection = secondViewModel.state.value.dashboardSelection
                assertTrue(selection is DashboardSelection.AllAccounts)
                assertEquals(
                    AllAccountsFoldMode.Separate,
                    (selection as DashboardSelection.AllAccounts).foldMode,
                )
                assertNull(secondViewModel.state.value.currentCurrency)
                assertTrue(
                    secondViewModel.state.value.accounts
                        .any { it.id == cash.id },
                )
                assertEquals(
                    0,
                    BigDecimal("50.00").compareTo(
                        secondViewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
            } finally {
                secondStore.clear()
                runCurrent()
            }
        }

    @Test
    fun `explicit navigation clears import focus but the rebuilt view model restores the last viewed month`() =
        runTest {
            val focusEpochMs = Instant.parse("2020-03-14T12:00:00Z").toEpochMilli()
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(
                        defaultAccountId = cash.id,
                        firstPositiveSeen = true,
                        importFocusEpochMs = focusEpochMs,
                        importFocusCurrencyId = usd.id,
                    ),
                )
            val focusPeriod = Period.Month(YearMonth.of(2020, 3))
            val navigatedPeriod = focusPeriod.next()
            transactionRepository.seedExpenseSummary(cash.id, focusPeriod, summary(categoryId = 10L, amount = "42.00"))
            transactionRepository.seedExpenseSummary(cash.id, navigatedPeriod, summary(categoryId = 20L, amount = "17.00"))

            val (firstViewModel, firstStore) = buildViewModel()
            try {
                runCurrent()
                assertEquals(focusPeriod, firstViewModel.state.value.period)

                firstViewModel.onEvent(DashboardEvent.NextPeriod)
                runCurrent()

                assertEquals(navigatedPeriod, firstViewModel.state.value.period)
                // The transient import focus is gone, but the navigated month is now persisted.
                assertEquals(0L, settingsRepository.currentSettings().importFocusEpochMs)
                assertEquals(-1L, settingsRepository.currentSettings().importFocusCurrencyId)
            } finally {
                firstStore.clear()
                runCurrent()
            }

            val (secondViewModel, secondStore) = buildViewModel()
            try {
                runCurrent()
                // Cold start restores the last viewed month, not the current month.
                assertEquals(navigatedPeriod, secondViewModel.state.value.period)
            } finally {
                secondStore.clear()
                runCurrent()
            }
        }

    @Test
    fun `cold start surfaces past-month imported data after the import focus was cleared by navigation`() =
        runTest {
            // Reproduces the cold-start-only empty-dashboard bug: a Monefy import into a past
            // month (March 2020) renders in-session, but after the import focus is cleared by
            // navigation and the process is restarted, the dashboard must not snap to the current
            // (empty) month — it must restore the past month so the imported data stays visible.
            val focusEpochMs = Instant.parse("2020-03-14T12:00:00Z").toEpochMilli()
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(
                        defaultAccountId = cash.id,
                        firstPositiveSeen = true,
                        importFocusEpochMs = focusEpochMs,
                        importFocusCurrencyId = usd.id,
                    ),
                )
            val importedMonth = Period.Month(YearMonth.of(2020, 3))
            transactionRepository.seedExpenseSummary(cash.id, importedMonth, summary(categoryId = 10L, amount = "42.00"))
            transactionRepository.seedIncomeSummary(cash.id, importedMonth, summary(categoryId = 200L, amount = "100.00"))
            // The current month deliberately has no data — only the restored past month can fill it.

            val (firstViewModel, firstStore) = buildViewModel()
            try {
                runCurrent()
                assertEquals(importedMonth, firstViewModel.state.value.period)
                // Simulate the user navigating away and back, which clears the one-shot import focus.
                firstViewModel.onEvent(DashboardEvent.NextPeriod)
                runCurrent()
                firstViewModel.onEvent(DashboardEvent.PreviousPeriod)
                runCurrent()
                assertEquals(importedMonth, firstViewModel.state.value.period)
                assertEquals(0L, settingsRepository.currentSettings().importFocusEpochMs)
            } finally {
                firstStore.clear()
                runCurrent()
            }

            val (secondViewModel, secondStore) = buildViewModel()
            try {
                runCurrent()
                assertEquals(importedMonth, secondViewModel.state.value.period)
                assertEquals(
                    0,
                    BigDecimal("42.00").compareTo(
                        secondViewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
                assertEquals(
                    0,
                    BigDecimal("100.00").compareTo(
                        secondViewModel.state.value.balanceSnapshot!!
                            .income.amount,
                    ),
                )
            } finally {
                secondStore.clear()
                runCurrent()
            }
        }

    @Test
    fun `manual account selection survives account list refresh`() =
        runTest {
            val importedCash = account(id = 4L, name = "Imported cash", isDefault = false)
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AccountSelected(card.id))
                runCurrent()
                assertEquals(card, viewModel.state.value.currentAccount)

                accountRepository.seed(cash, card, importedCash)

                runCurrent()
                assertEquals(card, viewModel.state.value.currentAccount)
                assertEquals(card.id, settingsRepository.currentSettings().defaultAccountId)
                assertEquals("specific_account", settingsRepository.currentSettings().dashboardSelectionMode)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `custom range period change stores the range and recomputes dashboard balances`() =
        runTest {
            val customRange =
                Period.CustomRange(
                    start = LocalDate.of(2026, 4, 10),
                    end = LocalDate.of(2026, 4, 11),
                )
            transactionRepository.seedExpenseSummary(
                cash.id,
                customRange,
                summary(categoryId = 10L, amount = "40.00"),
            )
            transactionRepository.seedIncomeSummary(
                cash.id,
                customRange,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(customRange))

                runCurrent()
                assertEquals(customRange, viewModel.state.value.period)
                assertNotNull(viewModel.state.value.balanceSnapshot)
                assertEquals(
                    0,
                    BigDecimal("40.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
                assertEquals(
                    listOf(10L),
                    viewModel.state.value.slices
                        .map { it.categoryId },
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `NextPeriod advances the displayed period and recomputes balances for the next period`() =
        runTest {
            val nextMonth = Period.Month(YearMonth.now().plusMonths(1))
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "30.00"))
            transactionRepository.seedExpenseSummary(cash.id, nextMonth, summary(categoryId = 20L, amount = "70.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertEquals(initialPeriod, viewModel.state.value.period)

                viewModel.onEvent(DashboardEvent.NextPeriod)

                runCurrent()
                assertEquals(nextMonth, viewModel.state.value.period)
                assertEquals(
                    listOf(20L),
                    viewModel.state.value.slices
                        .map { it.categoryId },
                )
                assertEquals(
                    0,
                    BigDecimal("70.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `PreviousPeriod steps the displayed period back and recomputes balances for the prior period`() =
        runTest {
            val prevMonth = Period.Month(YearMonth.now().minusMonths(1))
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "30.00"))
            transactionRepository.seedExpenseSummary(cash.id, prevMonth, summary(categoryId = 40L, amount = "55.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertEquals(initialPeriod, viewModel.state.value.period)

                viewModel.onEvent(DashboardEvent.PreviousPeriod)

                runCurrent()
                assertEquals(prevMonth, viewModel.state.value.period)
                assertEquals(
                    listOf(40L),
                    viewModel.state.value.slices
                        .map { it.categoryId },
                )
                assertEquals(
                    0,
                    BigDecimal("55.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `NextPeriod then PreviousPeriod returns to the original displayed period`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertEquals(initialPeriod, viewModel.state.value.period)

                viewModel.onEvent(DashboardEvent.NextPeriod)
                runCurrent()
                viewModel.onEvent(DashboardEvent.PreviousPeriod)
                runCurrent()

                assertEquals(initialPeriod, viewModel.state.value.period)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `donut slices carry the iconKey copied from each category balance`() =
        runTest {
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

    @Test
    fun `donut slices contain only expense categories with fractions over total expense summing to one`() =
        runTest {
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "30.00"),
                summary(categoryId = 20L, amount = "70.00"),
            )
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "500.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val slices = viewModel.state.value.slices
                assertEquals(setOf(10L, 20L), slices.map { it.categoryId }.toSet())
                assertFalse(slices.any { it.categoryId == 200L })

                val totalFraction = slices.sumOf { it.fraction.toDouble() }
                assertEquals(1.0, totalFraction, 0.0001)

                assertEquals(0.30f, slices.single { it.categoryId == 10L }.fraction, 0.0001f)
                assertEquals(0.70f, slices.single { it.categoryId == 20L }.fraction, 0.0001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `donut slices contain only expense category balances — transfers are never present`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val expenseBalance = expenseCategoryBalance(categoryId = 10L, amount = "120.00", iconKey = "food")
                val transferBalance =
                    com.kshavrin.mymoney.core.domain.model.CategoryBalance(
                        categoryId = 77L,
                        categoryName = "transfer-category",
                        colorHex = "#888888",
                        total = Money(java.math.BigDecimal("250.00"), usd),
                        fraction = 0f,
                        iconKey = "ic_transfer",
                        isExpense = false,
                    )
                val snapshot =
                    com.kshavrin.mymoney.core.domain.model.BalanceSnapshot(
                        income = Money(java.math.BigDecimal.ZERO, usd),
                        expense = Money(java.math.BigDecimal("120.00"), usd),
                        net = Money(java.math.BigDecimal("-120.00"), usd),
                        byCategory = listOf(expenseBalance, transferBalance),
                    )

                val slices = viewModel.snapshotToSlices(snapshot, alertCategoryIds = emptySet())

                assertFalse("transfer category must never appear in donut slices", slices.any { it.categoryId == 77L })
                assertEquals(setOf(10L), slices.map { it.categoryId }.toSet())
                assertEquals(1.0f, slices.single { it.categoryId == 10L }.fraction, 0.0001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `donut slices are empty when there are no expenses even if income exists`() =
        runTest {
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "500.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                assertTrue(
                    viewModel.state.value.slices
                        .isEmpty(),
                )
                assertEquals(
                    0,
                    BigDecimal("0.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .expense.amount,
                    ),
                )
                assertEquals(
                    0,
                    BigDecimal("500.00").compareTo(
                        viewModel.state.value.balanceSnapshot!!
                            .income.amount,
                    ),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `state derives ring fraction and period net from income and expense totals`() =
        runTest {
            // income=85000, expense=47350 → surplus → green, fraction = net/income = 37650/85000 ≈ 0.4429
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "45000.00"),
                summary(categoryId = 20L, amount = "1500.00"),
                summary(categoryId = 30L, amount = "850.00"),
            )
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "85000.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertEquals(0.4429f, state.ringFraction, 0.001f)
                assertFalse(state.ringIsExpense)
                assertEquals(0, BigDecimal("37650.00").compareTo(state.periodNet.amount))
                assertEquals(listOf(10L, 20L, 30L), state.expenseTiles.map { it.categoryId })
                assertEquals(0.9504f, state.expenseTiles[0].fraction, 0.0001f)
                assertEquals(0.0179f, state.expenseTiles[2].fraction, 0.0001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `state sets ring to full red when income is zero and expense is present with no previous period data`() =
        runTest {
            // income=0, expense=1200, no previousExpense seeded → fraction=1.0, isExpense=true
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "1200.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertEquals(1.0f, state.ringFraction, 0.001f)
                assertTrue(state.ringIsExpense)
                assertEquals(0, BigDecimal("-1200.00").compareTo(state.periodNet.amount))
                assertEquals(listOf(10L), state.expenseTiles.map { it.categoryId })
                assertEquals(1.0f, state.expenseTiles.single().fraction, 0.0001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ring shows green surplus fraction when income exceeds expense`() =
        runTest {
            // income=200, expense=50 → surplus → green, fraction = 150/200 = 0.75
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "50.00"),
            )
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "200.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertFalse(state.ringIsExpense)
                assertEquals(0.75f, state.ringFraction, 0.001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ring shows red overspend fraction when expense exceeds income`() =
        runTest {
            // income=100, expense=160 → overspend = 60/100 = 0.6, red
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "160.00"),
            )
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertTrue(state.ringIsExpense)
                assertEquals(0.6f, state.ringFraction, 0.001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ring fraction is zero and not expense when no records at all`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertFalse(state.ringIsExpense)
                assertEquals(0.0f, state.ringFraction, 0.001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ring does not query previous period when income is present`() =
        runTest {
            // Surplus period — previousExpense must never be fetched.
            // We seed nothing for the previous month so any query there would return 0,
            // which would corrupt the fraction if it were used. The assertion is that
            // the fraction uses the income-based formula, not the previous-period formula.
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "30.00"),
            )
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                // Surplus: net=70, fraction=70/100=0.7, green — proves previous-period path was NOT taken.
                assertFalse(state.ringIsExpense)
                assertEquals(0.7f, state.ringFraction, 0.001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ring uses previous period expense when income is zero and expense is present`() =
        runTest {
            // current period: income=0, expense=40
            // previous period: expense=80 → fraction = 40/80 = 0.5, red
            val prevMonth = Period.Month(initialPeriod.yearMonth.minusMonths(1))
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "40.00"),
            )
            transactionRepository.seedExpenseSummary(
                cash.id,
                prevMonth,
                summary(categoryId = 10L, amount = "80.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertTrue(state.ringIsExpense)
                assertEquals(0.5f, state.ringFraction, 0.001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToSlices keeps real slices when every expense category is at least two percent`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val slices =
                    viewModel.snapshotToSlices(
                        snapshot =
                            expenseSnapshot(
                                expenseCategoryBalance(
                                    categoryId = 10L,
                                    amount = "30.00",
                                    iconKey = "food",
                                    textColorHex = "#123456",
                                ),
                                expenseCategoryBalance(
                                    categoryId = 20L,
                                    amount = "70.00",
                                    iconKey = "transport",
                                    textColorHex = "#ABCDEF",
                                ),
                            ),
                        alertCategoryIds = setOf(20L),
                    )

                assertEquals(listOf(10L, 20L), slices.map { it.categoryId })
                assertFalse(slices.any { it.categoryId == OTHER_CATEGORY_ID })
                assertEquals(1.0, slices.sumOf { it.fraction.toDouble() }, 0.0001)
                assertEquals(0.30f, slices.single { it.categoryId == 10L }.fraction, 0.0001f)
                assertEquals(0.70f, slices.single { it.categoryId == 20L }.fraction, 0.0001f)
                assertEquals(parseHexColor("#123456"), slices.single { it.categoryId == 10L }.labelColor)
                assertEquals(parseHexColor("#ABCDEF"), slices.single { it.categoryId == 20L }.labelColor)
                assertTrue(slices.single { it.categoryId == 20L }.hasBudgetAlert)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToSlices folds a lone sub two percent expense into one trailing other slice without budget alert`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val slices =
                    viewModel.snapshotToSlices(
                        snapshot =
                            expenseSnapshot(
                                expenseCategoryBalance(categoryId = 10L, amount = "98.50", iconKey = "food"),
                                expenseCategoryBalance(categoryId = 20L, amount = "1.50", iconKey = "coffee"),
                            ),
                        alertCategoryIds = setOf(10L, 20L),
                    )

                assertEquals(listOf(10L, OTHER_CATEGORY_ID), slices.map { it.categoryId })
                assertEquals(1.0, slices.sumOf { it.fraction.toDouble() }, 0.0001)
                assertEquals(0.985f, slices.first().fraction, 0.0001f)
                assertTrue(slices.first().hasBudgetAlert)

                val other = slices.last()
                assertEquals(OTHER_CATEGORY_ID, other.categoryId)
                assertEquals(OTHER_CATEGORY_ICON_KEY, other.iconKey)
                assertEquals(0.015f, other.fraction, 0.0001f)
                assertFalse(other.hasBudgetAlert)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToSlices folds multiple sub two percent expenses into one trailing other slice`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val slices =
                    viewModel.snapshotToSlices(
                        snapshot =
                            expenseSnapshot(
                                expenseCategoryBalance(categoryId = 10L, amount = "97.00", iconKey = "food"),
                                expenseCategoryBalance(categoryId = 20L, amount = "1.00", iconKey = "coffee"),
                                expenseCategoryBalance(categoryId = 30L, amount = "1.00", iconKey = "snack"),
                                expenseCategoryBalance(categoryId = 40L, amount = "1.00", iconKey = "taxi"),
                            ),
                        alertCategoryIds = setOf(20L, 30L, 40L),
                    )

                assertEquals(listOf(10L, OTHER_CATEGORY_ID), slices.map { it.categoryId })
                assertEquals(1.0, slices.sumOf { it.fraction.toDouble() }, 0.0001)
                assertEquals(0.97f, slices.first().fraction, 0.0001f)

                val other = slices.last()
                assertEquals(0.03f, other.fraction, 0.0001f)
                assertEquals(OTHER_CATEGORY_ICON_KEY, other.iconKey)
                assertFalse(other.hasBudgetAlert)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToSlices collapses an all minor expense set into a single hundred percent other slice`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val slices =
                    viewModel.snapshotToSlices(
                        snapshot =
                            expenseSnapshot(
                                *List(60) { index ->
                                    expenseCategoryBalance(
                                        categoryId = (index + 1).toLong(),
                                        amount = "1.00",
                                        iconKey = "minor-${index + 1}",
                                    )
                                }.toTypedArray(),
                            ),
                        alertCategoryIds = (1L..60L).toSet(),
                    )

                assertEquals(1, slices.size)
                val other = slices.single()
                assertEquals(OTHER_CATEGORY_ID, other.categoryId)
                assertEquals(OTHER_CATEGORY_ICON_KEY, other.iconKey)
                assertEquals(1.0f, other.fraction, 0.0001f)
                assertFalse(other.hasBudgetAlert)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToSlices leaves a two to three percent expense slice real because the grouping threshold is independent of labels`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val slices =
                    viewModel.snapshotToSlices(
                        snapshot =
                            expenseSnapshot(
                                expenseCategoryBalance(categoryId = 10L, amount = "97.50", iconKey = "food"),
                                expenseCategoryBalance(categoryId = 20L, amount = "2.50", iconKey = "coffee"),
                            ),
                        alertCategoryIds = emptySet(),
                    )

                assertEquals(listOf(10L, 20L), slices.map { it.categoryId })
                assertFalse(slices.any { it.categoryId == OTHER_CATEGORY_ID })
                assertEquals(1.0, slices.sumOf { it.fraction.toDouble() }, 0.0001)
                assertEquals(0.025f, slices.single { it.categoryId == 20L }.fraction, 0.0001f)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToSlices does not create other when expense is empty or zero`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val slices =
                    viewModel.snapshotToSlices(
                        snapshot =
                            expenseSnapshot(
                                expenseCategoryBalance(categoryId = 10L, amount = "0.00", iconKey = "food"),
                            ),
                        alertCategoryIds = setOf(10L),
                    )

                assertEquals(listOf(10L), slices.map { it.categoryId })
                assertFalse(slices.any { it.categoryId == OTHER_CATEGORY_ID })
                assertEquals(0f, slices.single().fraction, 0f)
                assertTrue(slices.single().hasBudgetAlert)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `snapshotToExpenseTiles keeps all real expense categories sorted with source amount icon color and alert flags`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                val tiles =
                    viewModel.snapshotToExpenseTiles(
                        snapshot =
                            BalanceSnapshot(
                                income = Money(BigDecimal("850.00"), usd),
                                expense = Money(BigDecimal("761.00"), usd),
                                net = Money(BigDecimal("89.00"), usd),
                                byCategory =
                                    listOf(
                                        expenseCategoryBalance(
                                            categoryId = 10L,
                                            amount = "500.00",
                                            iconKey = "food",
                                            textColorHex = "#FFE27A",
                                        ),
                                        expenseCategoryBalance(
                                            categoryId = 20L,
                                            amount = "250.00",
                                            iconKey = "transport",
                                            textColorHex = "#7FE2FF",
                                        ),
                                        expenseCategoryBalance(
                                            categoryId = 30L,
                                            amount = "10.00",
                                            iconKey = "snack",
                                            textColorHex = "#FF8FE1",
                                        ),
                                        expenseCategoryBalance(
                                            categoryId = OTHER_CATEGORY_ID,
                                            amount = "1.00",
                                            iconKey = "other",
                                        ),
                                        CategoryBalance(
                                            categoryId = 200L,
                                            categoryName = "salary",
                                            colorHex = "#22AA22",
                                            total = Money(BigDecimal("850.00"), usd),
                                            fraction = 0f,
                                            iconKey = "salary",
                                            isExpense = false,
                                        ),
                                    ),
                            ),
                        alertCategoryIds = setOf(20L),
                    )

                assertEquals(listOf(10L, 20L, 30L), tiles.map { it.categoryId })
                assertEquals(listOf("#FF8888", "#FF8888", "#FF8888"), tiles.map { it.colorHex })
                assertEquals(listOf("#FFE27A", "#7FE2FF", "#FF8FE1"), tiles.map { it.textColorHex })
                assertEquals(listOf("food", "transport", "snack"), tiles.map { it.iconKey })
                assertEquals(usd, tiles[0].amount.currency)
                assertEquals(0, BigDecimal("500.00").compareTo(tiles[0].amount.amount))
                assertEquals(0, BigDecimal("250.00").compareTo(tiles[1].amount.amount))
                assertEquals(0, BigDecimal("10.00").compareTo(tiles[2].amount.amount))
                assertEquals(0.6570f, tiles[0].fraction, 0.0001f)
                assertEquals(0.0131f, tiles[2].fraction, 0.0001f)
                assertFalse(tiles.any { it.categoryId == OTHER_CATEGORY_ID })
                assertFalse(tiles.any { it.categoryId == 200L })
                assertTrue(tiles.single { it.categoryId == 20L }.hasBudgetAlert)
                assertFalse(tiles.single { it.categoryId == 10L }.hasBudgetAlert)
                assertFalse(tiles.single { it.categoryId == 30L }.hasBudgetAlert)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `expense category placeholders are built from observeByKind Expense sorted by sortOrder`() =
        runTest {
            categoryRepository.seed(
                category(
                    id = 30L,
                    name = "Home",
                    kind = CategoryKind.Expense,
                    sortOrder = 2,
                    iconKey = "home",
                    colorHex = "#112233",
                    textColor = "#AA7733",
                ),
                category(
                    id = 10L,
                    name = "Food",
                    kind = CategoryKind.Expense,
                    sortOrder = 0,
                    iconKey = "food",
                    colorHex = "#FF0000",
                    textColor = "#00AACC",
                ),
                category(
                    id = 20L,
                    name = "Transport",
                    kind = CategoryKind.Expense,
                    sortOrder = 1,
                    iconKey = "transport",
                    colorHex = "#00FF00",
                    textColor = "#CC00AA",
                ),
                category(
                    id = 200L,
                    name = "Salary",
                    kind = CategoryKind.Income,
                    sortOrder = 0,
                    iconKey = "salary",
                    colorHex = "#0000FF",
                ),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val placeholders = viewModel.state.value.expenseCategoryPlaceholders
                assertEquals(listOf(10L, 20L, 30L), placeholders.map { it.categoryId })
                assertEquals(listOf("Food", "Transport", "Home"), placeholders.map { it.label })
                assertEquals(listOf("food", "transport", "home"), placeholders.map { it.iconKey })
                assertEquals(
                    listOf(
                        parseHexColor("#00AACC"),
                        parseHexColor("#CC00AA"),
                        parseHexColor("#AA7733"),
                    ),
                    placeholders.map { it.labelColor },
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `expense category placeholders all carry zero fraction`() =
        runTest {
            categoryRepository.seed(
                category(id = 10L, name = "Food", kind = CategoryKind.Expense, sortOrder = 0, iconKey = "food", colorHex = "#FF0000"),
                category(id = 20L, name = "Transport", kind = CategoryKind.Expense, sortOrder = 1, iconKey = "transport", colorHex = "#00FF00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val placeholders = viewModel.state.value.expenseCategoryPlaceholders
                assertEquals(2, placeholders.size)
                assertTrue(placeholders.all { it.fraction == 0f })
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `expense category placeholders parse the category colour from hex`() =
        runTest {
            categoryRepository.seed(
                category(id = 10L, name = "Food", kind = CategoryKind.Expense, sortOrder = 0, iconKey = "food", colorHex = "#FF8800"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val placeholder =
                    viewModel.state.value.expenseCategoryPlaceholders
                        .single()
                assertEquals(Color(0xFFFF8800), placeholder.color)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `expense category placeholders exclude income categories`() =
        runTest {
            categoryRepository.seed(
                category(id = 200L, name = "Salary", kind = CategoryKind.Income, sortOrder = 0, iconKey = "salary", colorHex = "#0000FF"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                assertTrue(
                    viewModel.state.value.expenseCategoryPlaceholders
                        .isEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `dashboard chrome navigation events emit their actions`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.MinusFabClicked)
                viewModel.onEvent(DashboardEvent.PlusFabClicked)
                viewModel.onEvent(DashboardEvent.TransferClicked)
                viewModel.onEvent(DashboardEvent.SearchClicked)

                runCurrent()

                assertEquals(
                    listOf(
                        DashboardAction.NavigateAddExpense,
                        DashboardAction.NavigateAddIncome,
                        DashboardAction.NavigateTransfer,
                        DashboardAction.NavigateSearch,
                    ),
                    actions,
                )
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `balance card event emits unfiltered transactions action for active account`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.BalanceCardClicked)

                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(initialPeriod)
                assertEquals(
                    listOf(
                        DashboardAction.NavigateTransactionsByAccount(
                            accountId = cash.id,
                            fromMillis = expectedRange.first,
                            toMillis = expectedRange.last,
                        ),
                    ),
                    actions,
                )
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `slice click on other does not emit navigation`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.SliceClicked(OTHER_CATEGORY_ID))

                runCurrent()

                assertTrue(actions.isEmpty())
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `slice click on a real category emits navigation for the current account`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.SliceClicked(categoryId = 77L))

                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(initialPeriod)
                assertEquals(
                    listOf(
                        DashboardAction.NavigateTransactionsByCategory(
                            accountId = cash.id,
                            currencyId = usd.id,
                            categoryId = 77L,
                            fromMillis = expectedRange.first,
                            toMillis = expectedRange.last,
                        ),
                    ),
                    actions,
                )
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts balance and slice taps in Separate mode do not emit navigation because no target currency exists`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                // Reach AllAccounts(Separate) — the fork-dialog path that does NOT confirm a target.
                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()
                actions.clear()

                // In Separate mode there is no fold target, so BalanceCardClicked and SliceClicked
                // must not emit navigation (they silently no-op for AllAccounts when foldMode is Separate).
                viewModel.onEvent(DashboardEvent.BalanceCardClicked)
                viewModel.onEvent(DashboardEvent.SliceClicked(categoryId = 77L))

                runCurrent()

                assertTrue(
                    "BalanceCardClicked and SliceClicked on Separate AllAccounts must not emit navigation; got $actions",
                    actions.isEmpty(),
                )
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `balance card on Year period carries epoch-millis range for that year`() =
        runTest {
            val yearPeriod = Period.Year(2026)
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(yearPeriod))
                runCurrent()

                viewModel.onEvent(DashboardEvent.BalanceCardClicked)
                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(yearPeriod)
                assertEquals(1, actions.size)
                val action = actions.single() as DashboardAction.NavigateTransactionsByAccount
                assertEquals(cash.id, action.accountId)
                assertEquals(expectedRange.first, action.fromMillis)
                assertEquals(expectedRange.last, action.toMillis)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `balance card on CustomRange period carries exact custom epoch-millis range`() =
        runTest {
            val customRange =
                Period.CustomRange(
                    start = LocalDate.of(2026, 3, 1),
                    end = LocalDate.of(2026, 3, 15),
                )
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(customRange))
                runCurrent()

                viewModel.onEvent(DashboardEvent.BalanceCardClicked)
                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(customRange)
                assertEquals(1, actions.size)
                val action = actions.single() as DashboardAction.NavigateTransactionsByAccount
                assertEquals(cash.id, action.accountId)
                assertEquals(expectedRange.first, action.fromMillis)
                assertEquals(expectedRange.last, action.toMillis)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `slice click on Year period carries epoch-millis range for that year`() =
        runTest {
            val yearPeriod = Period.Year(2026)
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(yearPeriod))
                runCurrent()

                viewModel.onEvent(DashboardEvent.SliceClicked(categoryId = 42L))
                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(yearPeriod)
                assertEquals(1, actions.size)
                val action = actions.single() as DashboardAction.NavigateTransactionsByCategory
                assertEquals(cash.id, action.accountId)
                assertEquals(usd.id, action.currencyId)
                assertEquals(42L, action.categoryId)
                assertEquals(expectedRange.first, action.fromMillis)
                assertEquals(expectedRange.last, action.toMillis)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `slice click on CustomRange period carries exact custom epoch-millis range`() =
        runTest {
            val customRange =
                Period.CustomRange(
                    start = LocalDate.of(2026, 5, 10),
                    end = LocalDate.of(2026, 5, 20),
                )
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(customRange))
                runCurrent()

                viewModel.onEvent(DashboardEvent.SliceClicked(categoryId = 55L))
                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(customRange)
                assertEquals(1, actions.size)
                val action = actions.single() as DashboardAction.NavigateTransactionsByCategory
                assertEquals(cash.id, action.accountId)
                assertEquals(usd.id, action.currencyId)
                assertEquals(55L, action.categoryId)
                assertEquals(expectedRange.first, action.fromMillis)
                assertEquals(expectedRange.last, action.toMillis)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `slice click on OTHER_CATEGORY_ID guard does not emit navigation even when period is non-default`() =
        runTest {
            val yearPeriod = Period.Year(2026)
            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(yearPeriod))
                runCurrent()

                viewModel.onEvent(DashboardEvent.SliceClicked(OTHER_CATEGORY_ID))
                runCurrent()

                assertTrue(actions.isEmpty())
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `all accounts balance card on Year period carries epoch-millis range and no account id after convert flow`() =
        runTest {
            val yearPeriod = Period.Year(2026)
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            val actions = mutableListOf<DashboardAction>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.actions.toList(actions)
                }

            try {
                runCurrent()

                // Complete the convert flow to reach ConvertTo(usd), then change period.
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(emptyMap()))
                runCurrent()
                viewModel.onEvent(DashboardEvent.PeriodChanged(yearPeriod))
                runCurrent()
                actions.clear()

                viewModel.onEvent(DashboardEvent.BalanceCardClicked)
                runCurrent()

                val expectedRange = PeriodArithmetic.toEpochMillisRange(yearPeriod)
                assertEquals(1, actions.size)
                val action = actions.single() as DashboardAction.NavigateTransactionsByCurrency
                assertEquals(usd.id, action.currencyId)
                assertEquals(expectedRange.first, action.fromMillis)
                assertEquals(expectedRange.last, action.toMillis)
            } finally {
                collector.cancel()
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `opening the left drawer closes the right drawer`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.RightDrawerToggled)
                assertTrue(viewModel.state.value.rightDrawerOpen)
                assertFalse(viewModel.state.value.leftDrawerOpen)

                viewModel.onEvent(DashboardEvent.LeftDrawerToggled)

                assertTrue(viewModel.state.value.leftDrawerOpen)
                assertFalse(viewModel.state.value.rightDrawerOpen)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `opening the right drawer closes the left drawer`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.LeftDrawerToggled)
                assertTrue(viewModel.state.value.leftDrawerOpen)
                assertFalse(viewModel.state.value.rightDrawerOpen)

                viewModel.onEvent(DashboardEvent.RightDrawerToggled)

                assertTrue(viewModel.state.value.rightDrawerOpen)
                assertFalse(viewModel.state.value.leftDrawerOpen)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    // -------------------------------------------------------------------------
    // Recompute cancellation test — requires a suspending gate on getCategorySummary
    // -------------------------------------------------------------------------

    @Test
    fun `rapid period changes cancel the superseded recompute and only the last period balance is applied`() =
        runTest {
            val may = Period.Month(YearMonth.of(2026, 5))
            val june = Period.Month(YearMonth.of(2026, 6))

            // Gate keyed on Period: when the ViewModel requests getCategorySummary for May,
            // this deferred blocks the call until we release it — simulating a slow DB query
            // for the first period change. The second (june) change arrives while may is
            // suspended, which should cancel may's job via recomputeJob?.cancel().
            val mayGate = CompletableDeferred<Unit>()

            val gatedTransactionRepository =
                object : FakeDashboardTransactionRepository() {
                    override suspend fun getCategorySummary(
                        accountId: Long,
                        period: Period,
                        kind: TransactionKind,
                    ): List<CategorySummary> {
                        if (period == may) {
                            mayGate.await()
                        }
                        return super.getCategorySummary(accountId, period, kind)
                    }
                }
            gatedTransactionRepository.seedExpenseSummary(
                cash.id,
                may,
                summary(categoryId = 11L, amount = "50.00"),
            )
            gatedTransactionRepository.seedExpenseSummary(
                cash.id,
                june,
                summary(categoryId = 22L, amount = "80.00"),
            )

            val dispatcher = mainDispatcherRule.testDispatcher
            val calculator =
                BalanceCalculator(
                    accountRepository = accountRepository,
                    currencyRepository = currencyRepository,
                    transactionRepository = gatedTransactionRepository,
                    defaultDispatcher = dispatcher,
                )
            val alerts =
                ObserveBudgetAlertsUseCase(
                    transactionRepository = gatedTransactionRepository,
                    budgetRepository = budgetRepository,
                    balanceCalculator = calculator,
                    budgetEvaluator = BudgetEvaluator(),
                    defaultDispatcher = dispatcher,
                )
            val store = ViewModelStore()
            val factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        DashboardViewModel(
                            accountRepository = accountRepository,
                            currencyRepository = currencyRepository,
                            balanceCalculator = calculator,
                            balanceTrendCalculator = BalanceTrendCalculator(),
                            appSettingsRepository = settingsRepository,
                            transactionRepository = gatedTransactionRepository,
                            observeBudgetAlertsUseCase = alerts,
                            categoryRepository = categoryRepository,
                            resolveRateUseCase = buildResolveRate(),
                        ) as T
                }
            val viewModel = ViewModelProvider(store, factory)[DashboardViewModel::class.java]

            try {
                // Let the ViewModel finish its startup recompute (uses initialPeriod, not may).
                runCurrent()

                // First user-triggered period change → may. getCategorySummary for may now suspends
                // at mayGate, so the recompute coroutine is blocked.
                viewModel.onEvent(DashboardEvent.PeriodChanged(may))
                runCurrent()

                // Second user-triggered period change → june arrives while may's recompute is
                // still suspended. recomputeJob?.cancel() must kill the may coroutine and launch
                // a new one for june.
                viewModel.onEvent(DashboardEvent.PeriodChanged(june))
                // Release mayGate — the may coroutine resumes momentarily but its Job has been
                // cancelled, so any state update from the May snapshot must be a no-op.
                mayGate.complete(Unit)

                // Let all pending work settle.
                runCurrent()

                // June is the current period and its balance is visible.
                assertEquals(june, viewModel.state.value.period)
                assertEquals(
                    setOf(22L),
                    viewModel.state.value.slices
                        .map { it.categoryId }
                        .toSet(),
                )
                assertEquals(
                    0,
                    java.math.BigDecimal("80.00").compareTo(
                        viewModel.state.value.balanceSnapshot
                            ?.expense
                            ?.amount,
                    ),
                )
                // May's category (11L) must never appear — its recompute was cancelled.
                assertTrue(
                    "superseded period slice must not appear after cancellation",
                    viewModel.state.value.slices
                        .none { it.categoryId == 11L },
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    private fun buildViewModel(): Pair<DashboardViewModel, ViewModelStore> {
        val dispatcher = mainDispatcherRule.testDispatcher
        val calculator =
            BalanceCalculator(
                accountRepository = accountRepository,
                currencyRepository = currencyRepository,
                transactionRepository = transactionRepository,
                defaultDispatcher = dispatcher,
            )
        val alerts =
            ObserveBudgetAlertsUseCase(
                transactionRepository = transactionRepository,
                budgetRepository = budgetRepository,
                balanceCalculator = calculator,
                budgetEvaluator = BudgetEvaluator(),
                defaultDispatcher = dispatcher,
            )
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(
                        accountRepository = accountRepository,
                        currencyRepository = currencyRepository,
                        balanceCalculator = calculator,
                        balanceTrendCalculator = BalanceTrendCalculator(),
                        appSettingsRepository = settingsRepository,
                        transactionRepository = transactionRepository,
                        observeBudgetAlertsUseCase = alerts,
                        categoryRepository = categoryRepository,
                        resolveRateUseCase = buildResolveRate(),
                    ) as T
            }
        return ViewModelProvider(store, factory)[DashboardViewModel::class.java] to store
    }

    private fun buildResolveRate(rateRepository: FakeDashboardCurrencyRateRepository = FakeDashboardCurrencyRateRepository()): ResolveRateUseCase =
        ResolveRateUseCase(
            currencyRateRepository = rateRepository,
            currencyRepository = currencyRepository,
            convertMoney = ConvertMoneyUseCase(),
            clock = Clock.fixed(Instant.parse("2026-04-15T00:00:00Z"), ZoneOffset.UTC),
            zoneId = ZoneOffset.UTC,
        )

    private fun buildViewModelWith(
        rateRepository: FakeDashboardCurrencyRateRepository = FakeDashboardCurrencyRateRepository(),
    ): Pair<DashboardViewModel, ViewModelStore> {
        val dispatcher = mainDispatcherRule.testDispatcher
        val calculator =
            BalanceCalculator(
                accountRepository = accountRepository,
                currencyRepository = currencyRepository,
                transactionRepository = transactionRepository,
                defaultDispatcher = dispatcher,
            )
        val alerts =
            ObserveBudgetAlertsUseCase(
                transactionRepository = transactionRepository,
                budgetRepository = budgetRepository,
                balanceCalculator = calculator,
                budgetEvaluator = BudgetEvaluator(),
                defaultDispatcher = dispatcher,
            )
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(
                        accountRepository = accountRepository,
                        currencyRepository = currencyRepository,
                        balanceCalculator = calculator,
                        balanceTrendCalculator = BalanceTrendCalculator(),
                        appSettingsRepository = settingsRepository,
                        transactionRepository = transactionRepository,
                        observeBudgetAlertsUseCase = alerts,
                        categoryRepository = categoryRepository,
                        resolveRateUseCase = buildResolveRate(rateRepository),
                    ) as T
            }
        return ViewModelProvider(store, factory)[DashboardViewModel::class.java] to store
    }

    private fun summary(
        categoryId: Long,
        amount: String,
        iconKey: String = "",
    ) =
        CategorySummary(
            categoryId = categoryId,
            categoryName = "category-$categoryId",
            colorHex = "#FF8888",
            total = BigDecimal(amount),
            iconKey = iconKey,
        )

    private fun expenseSnapshot(vararg byCategory: CategoryBalance): BalanceSnapshot {
        val totalExpense =
            byCategory.fold(BigDecimal.ZERO) { acc, categoryBalance ->
                acc + categoryBalance.total.amount
            }
        val expense = Money(totalExpense, usd)
        return BalanceSnapshot(
            income = Money(BigDecimal.ZERO, usd),
            expense = expense,
            net = Money(BigDecimal.ZERO.subtract(totalExpense), usd),
            byCategory = byCategory.toList(),
        )
    }

    private fun expenseCategoryBalance(
        categoryId: Long,
        amount: String,
        iconKey: String,
        textColorHex: String = "#FFFFFF",
    ) = CategoryBalance(
        categoryId = categoryId,
        categoryName = "category-$categoryId",
        colorHex = "#FF8888",
        total = Money(BigDecimal(amount), usd),
        fraction = 0f,
        iconKey = iconKey,
        isExpense = true,
        textColorHex = textColorHex,
    )

    private fun budget(
        id: Long,
        categoryId: Long,
    ) =
        Budget(
            id = id,
            categoryId = categoryId,
            periodKind = "month",
            periodStart = Instant.EPOCH,
            amount = BigDecimal("100.00"),
            currencyId = usd.id,
            alertThresholdPct = 80,
            isActive = true,
        )

    private fun category(
        id: Long,
        name: String,
        kind: CategoryKind,
        sortOrder: Int,
        iconKey: String,
        colorHex: String,
        textColor: String = "#FFFFFF",
    ) = Category(
        id = id,
        name = name,
        kind = kind,
        iconKey = iconKey,
        colorHex = colorHex,
        textColor = textColor,
        sortOrder = sortOrder,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.EPOCH,
    )

    private fun account(
        id: Long,
        name: String,
        isDefault: Boolean,
        currencyId: Long = usd.id,
        isArchived: Boolean = false,
    ) =
        Account(
            id = id,
            name = name,
            currencyId = currencyId,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_cash",
            isDefault = isDefault,
            sortOrder = id.toInt(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            isArchived = isArchived,
        )

    // -------------------------------------------------------------------------
    // trendPoints tests (SPEC 05)
    // -------------------------------------------------------------------------

    @Test
    fun `trendPoints contains five entries for single account period after recompute`() =
        runTest {
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "85000.00"),
            )
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "47350.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                assertEquals(5, viewModel.state.value.trendPoints.size)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `trendPoints is empty in AllAccounts Separate mode`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                assertTrue(
                    "trendPoints must be empty in Separate mode",
                    viewModel.state.value.trendPoints
                        .isEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `periodNet equals income minus expense and is not cumulative`() =
        runTest {
            // income=85000, expense=47350 → periodNet=37650
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "85000.00"),
            )
            transactionRepository.seedExpenseSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "47350.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                val state = viewModel.state.value
                assertEquals(0, BigDecimal("37650.00").compareTo(state.periodNet.amount))
                assertEquals(usd, state.periodNet.currency)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    // -------------------------------------------------------------------------
    // Separate mode — currencyCards tests (SPEC 08 / D6)
    // -------------------------------------------------------------------------

    @Test
    fun `separate mode produces one currency card per currency that has active accounts`() =
        runTest {
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, card, eurCard)
            currencyRepository.seed(usd, eur)
            // USD group: income 200, expense 80
            transactionRepository.seedIncomeSummary(cash.id, initialPeriod, summary(categoryId = 200L, amount = "200.00"))
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "80.00"))
            // EUR group: income 100, expense 30
            transactionRepository.seedIncomeSummary(eurCard.id, initialPeriod, summary(categoryId = 200L, amount = "100.00"))
            transactionRepository.seedExpenseSummary(eurCard.id, initialPeriod, summary(categoryId = 20L, amount = "30.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                assertEquals(2, cards.size)
                val currencyCodes = cards.map { it.currency.code }.toSet()
                assertEquals(setOf("USD", "EUR"), currencyCodes)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode computes each currency group balance independently without conversion`() =
        runTest {
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurCard)
            currencyRepository.seed(usd, eur)
            // USD group: income 100, expense 30 → net 70 USD
            transactionRepository.seedIncomeSummary(cash.id, initialPeriod, summary(categoryId = 200L, amount = "100.00"))
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "30.00"))
            // EUR group: income 50, expense 20 → net 30 EUR
            transactionRepository.seedIncomeSummary(eurCard.id, initialPeriod, summary(categoryId = 200L, amount = "50.00"))
            transactionRepository.seedExpenseSummary(eurCard.id, initialPeriod, summary(categoryId = 20L, amount = "20.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                val usdCard = cards.single { it.currency.code == "USD" }
                val eurCard2 = cards.single { it.currency.code == "EUR" }

                assertEquals(usd, usdCard.snapshot.income.currency)
                assertEquals(0, BigDecimal("100.00").compareTo(usdCard.snapshot.income.amount))
                assertEquals(0, BigDecimal("30.00").compareTo(usdCard.snapshot.expense.amount))
                assertEquals(0, BigDecimal("70.00").compareTo(usdCard.snapshot.net.amount))

                assertEquals(eur, eurCard2.snapshot.income.currency)
                assertEquals(0, BigDecimal("50.00").compareTo(eurCard2.snapshot.income.amount))
                assertEquals(0, BigDecimal("20.00").compareTo(eurCard2.snapshot.expense.amount))
                assertEquals(0, BigDecimal("30.00").compareTo(eurCard2.snapshot.net.amount))
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode currency cards are empty when selection is ConvertTo`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "50.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                // Complete the convert flow — NOT Separate mode
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(emptyMap()))
                runCurrent()

                assertTrue(
                    "currencyCards must be empty in ConvertTo mode",
                    viewModel.state.value.currencyCards
                        .isEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode currency cards are empty when a specific account is selected`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                // Default selection is SpecificAccount(cash) — currencyCards must be empty
                assertTrue(
                    "currencyCards must be empty in specific account mode",
                    viewModel.state.value.currencyCards
                        .isEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode excludes archived accounts from currency card computation`() =
        runTest {
            val archivedEurCard = account(id = 3L, name = "Archived EUR", isDefault = false, currencyId = eur.id, isArchived = true)
            accountRepository.seed(cash, archivedEurCard)
            currencyRepository.seed(usd, eur)
            transactionRepository.seedIncomeSummary(cash.id, initialPeriod, summary(categoryId = 200L, amount = "50.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                // Only the active USD account produces a card; the archived EUR account must not
                assertEquals(1, cards.size)
                assertEquals("USD", cards.single().currency.code)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode currency cards sorted by descending absolute net then by currency code`() =
        runTest {
            // RUB net = 0 (no data), EUR net = 30, USD net = 70
            // Sorted descending by |net|: USD first, EUR second, RUB third
            val rub =
                Currency(
                    id = 3L,
                    code = "RUB",
                    symbol = "₽",
                    name = "Russian Ruble",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 2,
                )
            val rubAccount = account(id = 4L, name = "Cash RUB", isDefault = false, currencyId = rub.id)
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurCard, rubAccount)
            currencyRepository.seed(usd, eur, rub)
            // USD net = 70
            transactionRepository.seedIncomeSummary(cash.id, initialPeriod, summary(categoryId = 200L, amount = "100.00"))
            transactionRepository.seedExpenseSummary(cash.id, initialPeriod, summary(categoryId = 10L, amount = "30.00"))
            // EUR net = 30
            transactionRepository.seedIncomeSummary(eurCard.id, initialPeriod, summary(categoryId = 200L, amount = "50.00"))
            transactionRepository.seedExpenseSummary(eurCard.id, initialPeriod, summary(categoryId = 20L, amount = "20.00"))
            // RUB: no transactions → net = 0

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                assertEquals(3, cards.size)
                assertEquals("USD", cards[0].currency.code)
                assertEquals("EUR", cards[1].currency.code)
                assertEquals("RUB", cards[2].currency.code)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode isSeparateMode flag is true only while Separate fold mode is active`() =
        runTest {
            accountRepository.seed(cash)
            currencyRepository.seed(usd)

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                // Default: SpecificAccount — not Separate
                assertFalse(viewModel.state.value.isSeparateMode)

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()
                assertTrue(viewModel.state.value.isSeparateMode)

                // Switch to ConvertTo — Separate mode ends
                viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(usd.id))
                runCurrent()
                viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(emptyMap()))
                runCurrent()
                assertFalse(viewModel.state.value.isSeparateMode)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode period change recomputes currency cards for the new period`() =
        runTest {
            val eurCard = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurCard)
            currencyRepository.seed(usd, eur)

            // April: EUR has data; initialPeriod: EUR has no data
            transactionRepository.seedIncomeSummary(eurCard.id, april, summary(categoryId = 200L, amount = "80.00"))
            transactionRepository.seedExpenseSummary(eurCard.id, april, summary(categoryId = 20L, amount = "10.00"))

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                // In initialPeriod: EUR card has zero income and expense
                val cardsNow = viewModel.state.value.currencyCards
                val eurNow = cardsNow.firstOrNull { it.currency.code == "EUR" }
                assertNotNull(eurNow)
                assertEquals(0, BigDecimal.ZERO.compareTo(eurNow!!.snapshot.income.amount))

                viewModel.onEvent(DashboardEvent.PeriodChanged(april))
                runCurrent()

                val cardsApril = viewModel.state.value.currencyCards
                val eurApril = cardsApril.single { it.currency.code == "EUR" }
                assertEquals(0, BigDecimal("80.00").compareTo(eurApril.snapshot.income.amount))
                assertEquals(0, BigDecimal("10.00").compareTo(eurApril.snapshot.expense.amount))
            } finally {
                store.clear()
                runCurrent()
            }
        }

    // -------------------------------------------------------------------------
    // Separate mode — per-currency mini-chart trendPoints (SPEC G17)
    // -------------------------------------------------------------------------

    @Test
    fun `separate mode currency cards each carry trend points from their own currency group only`() =
        runTest {
            val eurAccount = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurAccount)
            currencyRepository.seed(usd, eur)
            // USD: income in every month of the 5-point window
            for (monthOffset in 0 until 5) {
                val period = Period.Month(YearMonth.now().minusMonths((4 - monthOffset).toLong()))
                transactionRepository.seedIncomeSummary(
                    cash.id,
                    period,
                    summary(categoryId = 200L, amount = "${(monthOffset + 1) * 10}.00"),
                )
            }
            // EUR: expense in every month of the 5-point window
            for (monthOffset in 0 until 5) {
                val period = Period.Month(YearMonth.now().minusMonths((4 - monthOffset).toLong()))
                transactionRepository.seedExpenseSummary(
                    eurAccount.id,
                    period,
                    summary(categoryId = 10L, amount = "${(monthOffset + 1) * 5}.00"),
                )
            }

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                assertEquals(2, cards.size)

                val usdCard = cards.single { it.currency.code == "USD" }
                val eurCard2 = cards.single { it.currency.code == "EUR" }

                // Each card must have its own 5-point series.
                assertEquals(5, usdCard.trendPoints.size)
                assertEquals(5, eurCard2.trendPoints.size)

                // USD trend is denominated in USD; EUR trend is denominated in EUR — no cross-rate.
                usdCard.trendPoints.forEach { point ->
                    assertEquals(usd, point.value.currency)
                }
                eurCard2.trendPoints.forEach { point ->
                    assertEquals(eur, point.value.currency)
                }
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode currency card trend points are empty when chart config is not visible`() =
        runTest {
            val eurAccount = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurAccount)
            currencyRepository.seed(usd, eur)
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(defaultAccountId = cash.id, firstPositiveSeen = true, chartVisible = false),
                )
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )
            transactionRepository.seedExpenseSummary(
                eurAccount.id,
                initialPeriod,
                summary(categoryId = 10L, amount = "50.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                assertEquals(2, cards.size)
                assertTrue(
                    "all currency card trendPoints must be empty when chartConfig.visible=false",
                    cards.all { it.trendPoints.isEmpty() },
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `separate mode currency card for usd does not include transactions from eur accounts in its trend`() =
        runTest {
            // G17: USD card trend must be derived from BalanceCalculator.forAccounts(usdAccounts, usd, period)
            // only. If EUR transactions leaked in, the USD trend values would be inflated.
            val eurAccount = account(id = 3L, name = "Euro card", isDefault = false, currencyId = eur.id)
            accountRepository.seed(cash, eurAccount)
            currencyRepository.seed(usd, eur)

            val trendPeriod = Period.Month(YearMonth.now().minusMonths(1))
            // USD: income 100 in trendPeriod
            transactionRepository.seedIncomeSummary(
                cash.id,
                trendPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )
            // EUR: income 9999 in the same trendPeriod — must NOT appear in USD trend
            transactionRepository.seedIncomeSummary(
                eurAccount.id,
                trendPeriod,
                summary(categoryId = 200L, amount = "9999.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
                runCurrent()

                val cards = viewModel.state.value.currencyCards
                val usdCard = cards.single { it.currency.code == "USD" }

                // Find the trend point for trendPeriod and verify its value is 100, not 10099.
                val targetPoint = usdCard.trendPoints.firstOrNull { it.period == trendPeriod }
                if (targetPoint != null) {
                    assertTrue(
                        "USD card trend value must not include EUR account transactions; got ${targetPoint.value.amount}",
                        targetPoint.value.amount.toDouble() < 200.0,
                    )
                }
            } finally {
                store.clear()
                runCurrent()
            }
        }

    // -------------------------------------------------------------------------
    // Chart config — ChartTapped / ChartSettingsClicked / sheet dismiss (SPEC 06)
    // -------------------------------------------------------------------------

    @Test
    fun `ChartTapped sets chartSettingsSheetOpen to true`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                assertFalse(viewModel.state.value.chartSettingsSheetOpen)

                viewModel.onEvent(DashboardEvent.ChartTapped)

                assertTrue(viewModel.state.value.chartSettingsSheetOpen)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartSettingsClicked sets chartSettingsSheetOpen to true and closes drawers`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                viewModel.onEvent(DashboardEvent.RightDrawerToggled)
                assertTrue(viewModel.state.value.rightDrawerOpen)

                viewModel.onEvent(DashboardEvent.ChartSettingsClicked)

                assertTrue(viewModel.state.value.chartSettingsSheetOpen)
                assertFalse(viewModel.state.value.rightDrawerOpen)
                assertFalse(viewModel.state.value.leftDrawerOpen)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartSettingsDismissed sets chartSettingsSheetOpen to false`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                viewModel.onEvent(DashboardEvent.ChartTapped)
                assertTrue(viewModel.state.value.chartSettingsSheetOpen)

                viewModel.onEvent(DashboardEvent.ChartSettingsDismissed)

                assertFalse(viewModel.state.value.chartSettingsSheetOpen)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    // -------------------------------------------------------------------------
    // Chart config — field writes + DataStore persistence (SPEC 06)
    // -------------------------------------------------------------------------

    @Test
    fun `ChartStyleChanged persists the new style id to AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartStyleChanged(com.kshavrin.mymoney.core.designsystem.chart.ChartStyle.Bars))
                runCurrent()

                assertEquals("bars", settingsRepository.currentSettings().chartStyle)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartGridlinesToggled persists the new value to AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartGridlinesToggled(false))
                runCurrent()

                assertFalse(settingsRepository.currentSettings().chartShowGridlines)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartLabelsToggled persists the new value to AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartLabelsToggled(false))
                runCurrent()

                assertFalse(settingsRepository.currentSettings().chartShowLabels)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartColorRuleChanged persists the new color rule id to AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartColorRuleChanged(com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule.Expense))
                runCurrent()

                assertEquals("expense", settingsRepository.currentSettings().chartColorRule)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartVisibilityChanged false persists chartVisible false to AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartVisibilityChanged(false))
                runCurrent()

                assertFalse(settingsRepository.currentSettings().chartVisible)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartVisibilityChanged true persists chartVisible true to AppSettings`() =
        runTest {
            settingsRepository =
                FakeDashboardAppSettingsRepository(
                    AppSettings(defaultAccountId = cash.id, firstPositiveSeen = true, chartVisible = false),
                )
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartVisibilityChanged(true))
                runCurrent()

                assertTrue(settingsRepository.currentSettings().chartVisible)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    // -------------------------------------------------------------------------
    // Chart config — trend re-derivation on period-type / point-count / metric (SPEC 06)
    // -------------------------------------------------------------------------

    @Test
    fun `ChartPointCountChanged persists new point count and triggers trend recompute`() =
        runTest {
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()
                val beforeCount = viewModel.state.value.trendPoints.size

                viewModel.onEvent(DashboardEvent.ChartPointCountChanged(8))
                runCurrent()

                assertEquals(8, settingsRepository.currentSettings().chartPointCount)
                val afterCount = viewModel.state.value.trendPoints.size
                assertTrue(
                    "point count change must re-derive trendPoints; before=$beforeCount after=$afterCount",
                    afterCount > 0,
                )
                assertEquals(8, afterCount)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartPointCountChanged below minimum is clamped to minimum in AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartPointCountChanged(0))
                runCurrent()

                assertEquals(CHART_POINT_COUNT_RANGE.first, settingsRepository.currentSettings().chartPointCount)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartPointCountChanged above maximum is clamped to maximum in AppSettings`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartPointCountChanged(99))
                runCurrent()

                assertEquals(CHART_POINT_COUNT_RANGE.last, settingsRepository.currentSettings().chartPointCount)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartMetricChanged persists new metric id and triggers trend recompute`() =
        runTest {
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartMetricChanged(com.kshavrin.mymoney.core.domain.model.ChartMetric.PERIOD_NET))
                runCurrent()

                assertEquals("period_net", settingsRepository.currentSettings().chartMetric)
                assertTrue(
                    "metric change must re-derive trendPoints",
                    viewModel.state.value.trendPoints
                        .isNotEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartPeriodTypeChanged persists new period type id and triggers trend recompute`() =
        runTest {
            transactionRepository.seedIncomeSummary(
                cash.id,
                initialPeriod,
                summary(categoryId = 200L, amount = "100.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartPeriodTypeChanged(ChartPeriodType.Month))
                runCurrent()

                assertEquals("month", settingsRepository.currentSettings().chartPeriodType)
                assertTrue(
                    "period type change must re-derive trendPoints",
                    viewModel.state.value.trendPoints
                        .isNotEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `ChartPeriodTypeChanged to Follow mirrors the current dashboard period for the trend window`() =
        runTest {
            transactionRepository.seedIncomeSummary(
                cash.id,
                april,
                summary(categoryId = 200L, amount = "50.00"),
            )

            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                viewModel.onEvent(DashboardEvent.PeriodChanged(april))
                runCurrent()

                viewModel.onEvent(DashboardEvent.ChartPeriodTypeChanged(ChartPeriodType.Follow))
                runCurrent()

                assertEquals("follow", settingsRepository.currentSettings().chartPeriodType)
                assertTrue(
                    "Follow period type must produce trendPoints anchored to the dashboard period",
                    viewModel.state.value.trendPoints
                        .isNotEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    @Test
    fun `settings flow re-emission from another session projects chartConfig into state`() =
        runTest {
            val (viewModel, store) = buildViewModel()
            try {
                runCurrent()

                settingsRepository.update { it.copy(chartStyle = "smooth_area") }
                runCurrent()

                assertEquals(
                    com.kshavrin.mymoney.core.designsystem.chart.ChartStyle.SmoothArea,
                    viewModel.state.value.chartConfig.style,
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

private class FakeDashboardAppSettingsRepository(
    initial: AppSettings,
) : AppSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state.asStateFlow()

    fun currentSettings(): AppSettings = state.value

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

    override suspend fun setActive(
        id: Long,
        active: Boolean,
    ) = Unit
}

private open class FakeDashboardCurrencyRateRepository : CurrencyRateRepository {
    private val state = MutableStateFlow<List<CurrencyRate>>(emptyList())

    override suspend fun findRate(
        fromCurrencyId: Long,
        toCurrencyId: Long,
    ): CurrencyRate? = state.value.firstOrNull { it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId }

    override fun observeAll(): Flow<List<CurrencyRate>> = state.asStateFlow()

    override suspend fun upsert(rate: CurrencyRate): Long = rate.id

    override suspend fun deleteById(id: Long) = Unit

    override suspend fun refreshRatesFromNetwork(): Result<Int> = Result.success(0)
}

// D5: tracks upsert calls to assert one-shot rate overrides are never persisted.
private class TrackingCurrencyRateRepository : FakeDashboardCurrencyRateRepository() {
    var upsertCallCount = 0

    override suspend fun upsert(rate: CurrencyRate): Long {
        upsertCallCount++
        return super.upsert(rate)
    }
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

private class FakeDashboardCategoryRepository : CategoryRepository {
    private val state = MutableStateFlow<List<Category>>(emptyList())

    fun seed(vararg categories: Category) {
        state.value = categories.toList()
    }

    override fun observeByKind(kind: CategoryKind): Flow<List<Category>> =
        state.map { list -> list.filter { it.kind == kind } }

    override fun observeAll(): Flow<List<Category>> = state.asStateFlow()

    override suspend fun findById(id: Long): Category? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(category: Category): Long = category.id

    override suspend fun upsertAll(categories: List<Category>) = Unit

    override suspend fun archive(id: Long) = Unit
}

private open class FakeDashboardTransactionRepository : TransactionRepository {
    private val state = MutableStateFlow<List<Transaction>>(emptyList())
    private val expenseSummaries = mutableMapOf<Pair<Long, Period>, List<CategorySummary>>()
    private val incomeSummaries = mutableMapOf<Pair<Long, Period>, List<CategorySummary>>()

    fun seedExpenseSummary(
        accountId: Long,
        period: Period,
        vararg summaries: CategorySummary,
    ) {
        expenseSummaries[accountId to period] = summaries.toList()
    }

    fun seedIncomeSummary(
        accountId: Long,
        period: Period,
        vararg summaries: CategorySummary,
    ) {
        incomeSummaries[accountId to period] = summaries.toList()
    }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = state.asStateFlow()

    override fun observeAll(): Flow<List<Transaction>> = state.asStateFlow()

    override fun paged(
        accountId: Long,
        categoryId: Long?,
        from: Instant,
        to: Instant,
    ): Flow<PagingData<Transaction>> =
        flowOf(PagingData.empty())

    override suspend fun findById(id: Long): Transaction? = null

    override suspend fun findByPeriod(
        accountId: Long,
        period: Period,
    ): List<Transaction> = emptyList()

    override suspend fun getCategorySummary(
        accountId: Long,
        period: Period,
        kind: TransactionKind,
    ): List<CategorySummary> =
        if (kind == TransactionKind.Expense) {
            expenseSummaries[accountId to period].orEmpty()
        } else {
            incomeSummaries[accountId to period].orEmpty()
        }

    override suspend fun getCategoryGroups(
        accountId: Long,
        period: Period,
    ): List<CategoryGroup> = emptyList()

    override suspend fun searchByNote(
        query: String,
        limit: Int,
    ): List<Transaction> = emptyList()

    override suspend fun upsert(transaction: Transaction): Long = transaction.id

    override suspend fun softDelete(
        id: Long,
        now: Instant,
    ) = Unit

    override suspend fun restore(
        id: Long,
        now: Instant,
    ) = Unit

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
