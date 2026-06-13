package com.kshavrin.mymoney.feature.dictionaries.currencies

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CurrenciesListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var viewModel: CurrenciesListViewModel

    @Before
    fun setUp() {
        currencyRepo = FakeCurrencyRepository()
        viewModel = CurrenciesListViewModel(currencyRepo)
    }

    private fun currency(
        id: Long,
        code: String = "C$id",
        isActive: Boolean = true,
        sortOrder: Int = id.toInt(),
    ): Currency =
        Currency(
            id = id,
            code = code,
            symbol = code.take(1),
            name = "Currency $id",
            decimalDigits = 2,
            isActive = isActive,
            sortOrder = sortOrder,
        )

    // --- initial state ---

    @Test
    fun `initial state has empty currency list`() =
        runTest {
            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.currencies.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- list population ---

    @Test
    fun `seeded currencies appear in state`() =
        runTest {
            currencyRepo.seed(currency(1L, "USD"), currency(2L, "EUR"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(2, state.currencies.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- sorting by sortOrder ---

    @Test
    fun `currencies are sorted by sortOrder ascending`() =
        runTest {
            currencyRepo.seed(
                currency(id = 10L, code = "GBP", sortOrder = 2),
                currency(id = 20L, code = "USD", sortOrder = 0),
                currency(id = 30L, code = "EUR", sortOrder = 1),
            )

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(20L, 30L, 10L), state.currencies.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- reactive: state updates when repository emits ---

    @Test
    fun `state updates reactively when repository emits a new list`() =
        runTest {
            viewModel.state.test {
                awaitItem()

                currencyRepo.seed(currency(id = 1L, code = "USD"))
                val after = awaitItem()
                assertEquals(1, after.currencies.size)
                assertEquals("USD", after.currencies.first().code)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- ActiveToggled: calls repository setActive ---

    @Test
    fun `ActiveToggled to false deactivates the currency in repository`() =
        runTest {
            currencyRepo.seed(currency(id = 5L, code = "USD", isActive = true))

            viewModel.onEvent(CurrenciesListEvent.ActiveToggled(id = 5L, active = false))
            advanceUntilIdle()

            val updated = currencyRepo.observeAll().first().first { it.id == 5L }
            assertFalse(updated.isActive)
        }

    @Test
    fun `ActiveToggled to true activates the currency in repository`() =
        runTest {
            currencyRepo.seed(currency(id = 6L, code = "EUR", isActive = false))

            viewModel.onEvent(CurrenciesListEvent.ActiveToggled(id = 6L, active = true))
            advanceUntilIdle()

            val updated = currencyRepo.observeAll().first().first { it.id == 6L }
            assertTrue(updated.isActive)
        }

    @Test
    fun `ActiveToggled does not change code of the currency`() =
        runTest {
            currencyRepo.seed(currency(id = 7L, code = "JPY", isActive = true))

            viewModel.onEvent(CurrenciesListEvent.ActiveToggled(id = 7L, active = false))
            advanceUntilIdle()

            val updated = currencyRepo.observeAll().first().first { it.id == 7L }
            assertEquals("JPY", updated.code)
        }

    @Test
    fun `ActiveToggled on one currency does not affect other currencies`() =
        runTest {
            currencyRepo.seed(
                currency(id = 1L, code = "USD", isActive = true),
                currency(id = 2L, code = "EUR", isActive = true),
            )

            viewModel.onEvent(CurrenciesListEvent.ActiveToggled(id = 1L, active = false))
            advanceUntilIdle()

            val eur = currencyRepo.observeAll().first().first { it.id == 2L }
            assertTrue(eur.isActive)
        }

    // --- navigation actions ---

    @Test
    fun `AddClicked emits NavigateAdd action`() =
        runTest {
            viewModel.actions.test {
                viewModel.onEvent(CurrenciesListEvent.AddClicked)

                assertEquals(CurrenciesListAction.NavigateAdd, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ItemClicked emits NavigateEdit with correct id`() =
        runTest {
            currencyRepo.seed(currency(id = 42L, code = "CHF"))

            viewModel.actions.test {
                viewModel.onEvent(CurrenciesListEvent.ItemClicked(42L))

                val action = awaitItem()
                assertTrue("expected NavigateEdit but was $action", action is CurrenciesListAction.NavigateEdit)
                assertEquals(42L, (action as CurrenciesListAction.NavigateEdit).id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked emits NavigateBack action`() =
        runTest {
            viewModel.actions.test {
                viewModel.onEvent(CurrenciesListEvent.BackClicked)

                assertEquals(CurrenciesListAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- code-lock invariant at list level ---
    // The list ViewModel does not know about code-lock (that is CurrencyEditViewModel's concern).
    // At the list level the contract is: ActiveToggled must never mutate the code field.
    // The tests above already cover this. The following verifies it survives a toggle-on/off cycle.

    @Test
    fun `code is unchanged after toggle active then inactive cycle`() =
        runTest {
            currencyRepo.seed(currency(id = 8L, code = "NOK", isActive = false))

            viewModel.onEvent(CurrenciesListEvent.ActiveToggled(id = 8L, active = true))
            advanceUntilIdle()
            viewModel.onEvent(CurrenciesListEvent.ActiveToggled(id = 8L, active = false))
            advanceUntilIdle()

            val currency = currencyRepo.observeAll().first().first { it.id == 8L }
            assertEquals("NOK", currency.code)
            assertFalse(currency.isActive)
        }
}
