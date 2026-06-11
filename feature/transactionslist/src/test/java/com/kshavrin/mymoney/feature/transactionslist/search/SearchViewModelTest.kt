package com.kshavrin.mymoney.feature.transactionslist.search

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeSearchHistoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * S08 Search records — ViewModel contract (TDD §4.9, lines 760-787).
 *
 * Virtual-time note: the rule installs a [StandardTestDispatcher] (NOT the unconfined default)
 * so the debounce timer is driven only by explicit `advanceTimeBy` / `advanceUntilIdle`. Every
 * `runTest` here is anchored on that same scheduler via `mainDispatcherRule.testDispatcher.scheduler`,
 * so `viewModelScope` work (the init collectors + the debounce(200) + mapLatest pipeline) shares
 * one clock with the test body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val now: Instant = Instant.parse("2026-05-22T10:00:00Z")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var historyRepo: FakeSearchHistoryRepository
    private lateinit var currencyRepo: FakeCurrencyRepository

    private val usd = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun expense(
        id: Long,
        note: String? = "Lunch",
        categoryId: Long? = 10L,
        amount: BigDecimal = BigDecimal("12.50"),
    ) = Transaction(
        id = id,
        kind = TransactionKind.Expense,
        amount = amount,
        currencyId = usd.id,
        accountId = 7L,
        categoryId = categoryId,
        note = note,
        occurredAt = now,
        createdAt = now,
        updatedAt = now,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private fun setUpRepos() {
        transactionRepo = FakeTransactionRepository()
        historyRepo = FakeSearchHistoryRepository()
        currencyRepo = FakeCurrencyRepository()
        currencyRepo.seed(usd)
    }

    private fun buildViewModel(
        transactionRepository: TransactionRepository = transactionRepo,
    ): SearchViewModel = SearchViewModel(
        transactionRepository = transactionRepository,
        searchHistoryRepository = historyRepo,
        currencyRepository = currencyRepo,
    )

    // ---- AC1: 200 ms debounce (TDD §4.9 line 784) --------------------------------------------

    @Test
    fun `rapid typing within the debounce window collapses to a single search for the final query`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val viewModel = buildViewModel()
            advanceUntilIdle() // let init collectors attach; the empty query stays blank (no search)

            // Five keystrokes, each well under the 200 ms threshold apart.
            viewModel.onEvent(SearchEvent.QueryChanged("c"))
            advanceTimeBy(50)
            viewModel.onEvent(SearchEvent.QueryChanged("co"))
            advanceTimeBy(50)
            viewModel.onEvent(SearchEvent.QueryChanged("cof"))
            advanceTimeBy(50)
            viewModel.onEvent(SearchEvent.QueryChanged("coff"))
            advanceTimeBy(50)
            viewModel.onEvent(SearchEvent.QueryChanged("coffee"))

            // Nothing has fired yet: the window never elapsed between keystrokes.
            runCurrent()
            assertTrue(
                "no search should fire while keystrokes stay under 200 ms apart, but was ${transactionRepo.searchCalls}",
                transactionRepo.searchCalls.isEmpty(),
            )

            // Cross the threshold once after the last keystroke.
            advanceTimeBy(200)
            runCurrent()

            assertEquals(
                "rapid typing must collapse to exactly one search",
                1,
                transactionRepo.searchCalls.size,
            )
            assertEquals(
                "the single search must use the final query",
                "coffee",
                transactionRepo.searchCalls.single().first,
            )
        }

    @Test
    fun `two queries separated by more than the debounce window each fire a search`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("food"))
            advanceTimeBy(250)
            runCurrent()
            viewModel.onEvent(SearchEvent.QueryChanged("rent"))
            advanceTimeBy(250)
            runCurrent()

            assertEquals(2, transactionRepo.searchCalls.size)
            assertEquals(listOf("food", "rent"), transactionRepo.searchCalls.map { it.first })
        }

    // ---- AC: Empty state exposes the history chips -------------------------------------------

    @Test
    fun `blank query stays in Empty phase exposing the seeded history`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            historyRepo.seed("coffee", "rent")
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(SearchPhase.Empty, state.phase)
                assertEquals(listOf("coffee", "rent"), state.history)
                assertTrue("blank query searches nothing", transactionRepo.searchCalls.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearing a non-blank query returns to Empty and never searches the blank string`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.seedSearchResults(expense(id = 1L))
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("lunch"))
            advanceTimeBy(250)
            runCurrent()
            viewModel.onEvent(SearchEvent.QueryCleared)
            advanceUntilIdle()

            viewModel.state.test {
                assertEquals(SearchPhase.Empty, expectMostRecentItem().phase)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(
                "the blank query produced by clearing must not reach searchByNote, but was ${transactionRepo.searchCalls}",
                transactionRepo.searchCalls.none { it.first.isBlank() },
            )
        }

    // ---- AC: Results state, rows mapped through (money stays BigDecimal) ---------------------

    @Test
    fun `non-blank query with hits enters Results phase with rows mapped from the transactions`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.seedSearchResults(
                expense(id = 1L, note = "Lunch", categoryId = 10L, amount = BigDecimal("12.50")),
                expense(id = 2L, note = "Dinner", categoryId = 11L, amount = BigDecimal("8.00")),
            )
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("lun"))
            advanceTimeBy(250)
            runCurrent()

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(SearchPhase.Results, state.phase)
                assertEquals(listOf(1L, 2L), state.results.map { it.id })

                val first = state.results.first()
                assertEquals("Lunch", first.note)
                assertEquals(10L, first.categoryId)
                assertEquals(TransactionKind.Expense, first.kind)
                assertEquals(usd.id, first.currencyId)
                // money preserved as BigDecimal end-to-end (compareTo: value, not scale)
                assertEquals(0, BigDecimal("12.50").compareTo(first.amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- AC: EmptyResults state ("No matches") -----------------------------------------------

    @Test
    fun `non-blank query with zero hits enters EmptyResults phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.seedSearchResults() // searchByNote returns empty
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("zzz"))
            advanceTimeBy(250)
            runCurrent()

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(SearchPhase.EmptyResults, state.phase)
                assertTrue("no rows on a miss", state.results.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- AC: history write on commit (add then prune); blank not recorded --------------------

    @Test
    fun `submitting a query records it via add then pruneToLimit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("groceries"))
            advanceUntilIdle()
            viewModel.onEvent(SearchEvent.QuerySubmitted)
            advanceUntilIdle()

            assertEquals(1, historyRepo.addCalls.size)
            assertEquals("groceries", historyRepo.addCalls.single().query)
            assertEquals("commit must prune after writing", 1, historyRepo.pruneCount)
        }

    @Test
    fun `tapping a suggestion chip records the chosen query via add then pruneToLimit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            historyRepo.seed("coffee")
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.SuggestionClicked("coffee"))
            advanceUntilIdle()

            assertEquals("coffee", historyRepo.addCalls.single().query)
            assertEquals(1, historyRepo.pruneCount)
        }

    @Test
    fun `opening a result records the active query then emits OpenDetail`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.seedSearchResults(expense(id = 5L, note = "Lunch"))
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("lunch"))
            advanceTimeBy(250)
            runCurrent()

            viewModel.actions.test {
                viewModel.onEvent(SearchEvent.ResultClicked(transactionId = 5L))

                val action = awaitItem()
                assertTrue(
                    "expected OpenDetail(5) but was $action",
                    action is SearchAction.OpenDetail && action.transactionId == 5L,
                )
                cancelAndIgnoreRemainingEvents()
            }
            advanceUntilIdle()
            assertEquals("lunch", historyRepo.addCalls.single().query)
            assertEquals(1, historyRepo.pruneCount)
        }

    @Test
    fun `submitting a blank query records nothing`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val viewModel = buildViewModel()
            advanceUntilIdle()

            // query is the default empty string
            viewModel.onEvent(SearchEvent.QuerySubmitted)
            advanceUntilIdle()
            // also a whitespace-only commit must be ignored
            viewModel.onEvent(SearchEvent.SuggestionClicked("   "))
            advanceUntilIdle()

            assertTrue("blank/whitespace queries are never persisted", historyRepo.addCalls.isEmpty())
            assertEquals("blank commit must not prune", 0, historyRepo.pruneCount)
        }

    // ---- AC2: voice result sets the query and drives the debounced search --------------------

    @Test
    fun `voice result sets the recognised text as the query`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.seedSearchResults(expense(id = 1L, note = "Taxi ride"))
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.VoiceResult("taxi"))
            advanceUntilIdle()

            viewModel.state.test {
                assertEquals("taxi", expectMostRecentItem().query)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `voice result triggers the debounced search for the recognised text`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.seedSearchResults(expense(id = 1L, note = "Taxi ride"))
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.VoiceResult("taxi"))
            advanceTimeBy(250)
            runCurrent()

            assertEquals("taxi", transactionRepo.searchCalls.single().first)
            viewModel.state.test {
                assertEquals(SearchPhase.Results, expectMostRecentItem().phase)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `superseded search cancellation does not drive the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val blockingRepo = CancellableSearchRepository(
                blockingQuery = "cof",
                resultsByQuery = mapOf(
                    "coffee" to listOf(expense(id = 3L, note = "Coffee")),
                ),
            )
            val viewModel = buildViewModel(transactionRepository = blockingRepo)
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("cof"))
            advanceTimeBy(250)
            runCurrent()
            assertEquals(listOf("cof"), blockingRepo.startedQueries)

            viewModel.onEvent(SearchEvent.QueryChanged("coffee"))
            advanceTimeBy(250)
            runCurrent()
            advanceUntilIdle()

            assertEquals(listOf("cof"), blockingRepo.cancelledQueries)
            assertEquals(SearchPhase.Results, viewModel.state.value.phase)
            assertEquals("coffee", viewModel.state.value.query)
            assertEquals(listOf(3L), viewModel.state.value.results.map { it.id })
        }

    // ---- AC: Error state (searchByNote throws) -----------------------------------------------

    @Test
    fun `a thrown searchByNote drives the Error phase`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            transactionRepo.failSearchWith()
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(SearchEvent.QueryChanged("boom"))
            advanceTimeBy(250)
            runCurrent()

            viewModel.state.test {
                assertEquals(SearchPhase.Error, expectMostRecentItem().phase)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- VM-owned navigation events ----------------------------------------------------------

    @Test
    fun `BackClicked emits NavigateBack`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(SearchEvent.BackClicked)
                assertEquals(SearchAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `VoiceUnavailable emits ShowVoiceUnavailable`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            setUpRepos()
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(SearchEvent.VoiceUnavailable)
                assertEquals(SearchAction.ShowVoiceUnavailable, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class CancellableSearchRepository(
        private val delegate: FakeTransactionRepository = FakeTransactionRepository(),
        private val blockingQuery: String,
        private val resultsByQuery: Map<String, List<Transaction>>,
    ) : TransactionRepository by delegate {
        val startedQueries: MutableList<String> = mutableListOf()
        val cancelledQueries: MutableList<String> = mutableListOf()

        override suspend fun searchByNote(query: String, limit: Int): List<Transaction> {
            startedQueries += query
            if (query == blockingQuery) {
                try {
                    awaitCancellation()
                } finally {
                    cancelledQueries += query
                }
            }
            return resultsByQuery[query].orEmpty()
        }
    }
}
