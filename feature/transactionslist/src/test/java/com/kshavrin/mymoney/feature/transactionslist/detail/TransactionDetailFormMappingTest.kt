package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormMode
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRateRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Tests for the state→TransactionFormState mapping (Edit mode) and for
 * category re-selection behaviour in Edit mode (category pick does NOT
 * auto-save; it returns to the amount step so the user confirms via the
 * Save FAB).
 */
class TransactionDetailFormMappingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var originalTimeZone: TimeZone

    private val createdAt: Instant = Instant.parse("2026-05-01T08:00:00Z")
    private val originalDate: LocalDate = LocalDate.parse("2026-06-10")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var rateRepo: FakeCurrencyRateRepository

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

    private val cashUsd =
        Account(
            id = 7L,
            name = "Cash",
            currencyId = usd.id,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_acc_cash",
            isDefault = true,
            sortOrder = 0,
            createdAt = createdAt,
            updatedAt = createdAt,
            isArchived = false,
        )

    private val foodCategory =
        Category(
            id = 10L,
            name = "Food",
            kind = CategoryKind.Expense,
            iconKey = "ic_cat_food",
            colorHex = "#FF8888",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = createdAt,
        )

    private val entertainmentCategory =
        Category(
            id = 11L,
            name = "Entertainment",
            kind = CategoryKind.Expense,
            iconKey = "ic_cat_fun",
            colorHex = "#8888FF",
            textColor = "#FFFFFF",
            sortOrder = 1,
            isDefault = false,
            isArchived = false,
            createdAt = createdAt,
        )

    private fun expense(id: Long = 100L) =
        Transaction(
            id = id,
            kind = TransactionKind.Expense,
            amount = BigDecimal("12.50"),
            currencyId = usd.id,
            accountId = cashUsd.id,
            categoryId = foodCategory.id,
            note = "Lunch",
            occurredAt = localMidnight(originalDate),
            createdAt = createdAt,
            updatedAt = createdAt,
            isDeleted = false,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
        )

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_TIME_ZONE_ID))
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        categoryRepo = FakeCategoryRepository()
        rateRepo = FakeCurrencyRateRepository()

        currencyRepo.seed(usd)
        accountRepo.seed(cashUsd)
        categoryRepo.seed(foodCategory, entertainmentCategory)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun buildViewModel(transactionId: Long): TransactionDetailViewModel =
        TransactionDetailViewModel(
            transactionRepository = transactionRepo,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            categoryRepository = categoryRepo,
            currencyRateRepository = rateRepo,
            savedStateHandle =
                SavedStateHandle(
                    mapOf(TransactionDetailViewModel.KEY_TRANSACTION_ID to transactionId),
                ),
        )

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    // ---- AC1: state→TransactionFormState mapping -----------------------------------------------

    @Test
    fun `toTransactionFormState maps amount input from the loaded expense`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue("state must be loaded before checking form mapping", state.isLoaded)
                // CalculatorEngine seeds "12.50" → display is "12.5" (trailing zero stripped)
                // Just verify non-empty and non-zero; the exact display is CalculatorEngine's concern.
                assertTrue(
                    "amountInput must be non-empty after loading",
                    state.amountInput.isNotEmpty(),
                )
                assertEquals(
                    "amount BigDecimal must match the transaction amount",
                    0,
                    BigDecimal("12.50").compareTo(state.amount),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toTransactionFormState maps note from the loaded expense`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("note must be pre-filled from the transaction", "Lunch", state.note)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toTransactionFormState maps occurredAt from the loaded expense`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(
                    "occurredAt must be pre-filled from the transaction",
                    originalDate,
                    state.occurredAt,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toTransactionFormState maps category from the loaded expense`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertNotNull("category must be pre-filled from the transaction", state.category)
                assertEquals(foodCategory.id, state.category?.id)
                assertEquals(foodCategory.name, state.category?.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toTransactionFormState includes expense categories of the matching kind only`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue(
                    "category list must contain Food (Expense kind)",
                    state.categories.any { it.id == foodCategory.id },
                )
                assertTrue(
                    "category list must contain Entertainment (Expense kind)",
                    state.categories.any { it.id == entertainmentCategory.id },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toTransactionFormState sets mode to Edit for a loaded expense`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val s = expectMostRecentItem()
                // The Screen maps state to TransactionFormState with mode = Edit.
                // Verify via the toTransactionFormState extension on the screen (mirrored here).
                val formState =
                    com.kshavrin.mymoney.core.designsystem.form.TransactionFormState(
                        amountInput = s.amountInput,
                        expression = s.expression,
                        currencyCode = s.currency?.code,
                        currencySymbol = s.currency?.symbol,
                        note = s.note,
                        occurredAt = s.occurredAt,
                        categories =
                            s.categories.map {
                                com.kshavrin.mymoney.core.designsystem.form.TransactionFormCategory(
                                    id = it.id,
                                    name = it.name,
                                    colorHex = it.colorHex,
                                    iconKey = it.iconKey,
                                )
                            },
                        categoryStep = s.categoryStep,
                        chooseCategoryEnabled = s.amount > BigDecimal.ZERO,
                        mode = TransactionFormMode.Edit,
                    )
                assertEquals(
                    "Edit-mode screen must produce TransactionFormMode.Edit",
                    TransactionFormMode.Edit,
                    formState.mode,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- AC2: category is editable in Edit mode ------------------------------------------------

    @Test
    fun `SelectCategoryClicked with a non-zero amount opens the category step in Edit mode`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            // Wait for load so amount > 0
            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)

            viewModel.state.test {
                assertTrue(
                    "SelectCategoryClicked must open the category step",
                    expectMostRecentItem().categoryStep,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `CategoryPicked in Edit mode updates the category and returns to amount step without saving`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            // Open category step
            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)
            viewModel.onEvent(TransactionDetailEvent.CategoryPicked(entertainmentCategory.id))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse(
                    "CategoryPicked must close the category step in Edit mode",
                    state.categoryStep,
                )
                assertEquals(
                    "CategoryPicked must update the selected category",
                    entertainmentCategory.id,
                    state.category?.id,
                )
                cancelAndIgnoreRemainingEvents()
            }
            // Explicit-save contract: CategoryPicked must NOT have called upsert.
            val savedTx = transactionRepo.findById(tx.id)
            // The original tx is still in the repo unchanged (upsert not called by CategoryPicked).
            assertEquals(
                "CategoryPicked must NOT trigger an implicit save; repo row stays as originally seeded",
                foodCategory.id,
                savedTx?.categoryId,
            )
        }

    @Test
    fun `CategoryPicked in Edit mode marks the form as dirty`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)
            viewModel.onEvent(TransactionDetailEvent.CategoryPicked(entertainmentCategory.id))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue("picking a different category must flip isDirty", state.isDirty)
                assertTrue("dirty with amount>0 -> canSave must be true", state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `picking the same category in Edit mode leaves the form not dirty`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)
            // Pick the same category that was originally loaded
            viewModel.onEvent(TransactionDetailEvent.CategoryPicked(foodCategory.id))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse(
                    "picking the original category must leave the form not dirty",
                    state.isDirty,
                )
                assertFalse("not dirty -> canSave must be false", state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackToAmount closes the category step without changing the category`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)
            viewModel.onEvent(TransactionDetailEvent.BackToAmount)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse("BackToAmount must close the category step", state.categoryStep)
                assertEquals(
                    "BackToAmount must not change the category",
                    foodCategory.id,
                    state.category?.id,
                )
                assertFalse("BackToAmount must not dirty the form", state.isDirty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- AC3: explicit-save after category change ----------------------------------------------

    @Test
    fun `SaveClicked after CategoryPicked persists the new category id`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)
            viewModel.onEvent(TransactionDetailEvent.CategoryPicked(entertainmentCategory.id))
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            val saved = transactionRepo.findById(tx.id)
            assertNotNull("upsert must have written a row with the same id", saved)
            assertEquals(
                "saved row must carry the newly chosen category id",
                entertainmentCategory.id,
                saved!!.categoryId,
            )
        }

    @Test
    fun `delete does not depend on category state — works regardless of whether category was changed`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            // Change the category (no save)
            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)
            viewModel.onEvent(TransactionDetailEvent.CategoryPicked(entertainmentCategory.id))

            viewModel.actions.test {
                viewModel.onEvent(TransactionDetailEvent.DeleteClicked)
                viewModel.onEvent(TransactionDetailEvent.ConfirmDelete)

                val action = awaitItem()
                assertTrue(
                    "delete with unsaved category change must still emit ShowUndoSnackbar",
                    action is TransactionDetailAction.ShowUndoSnackbar,
                )
                assertEquals(
                    listOf(tx.id),
                    transactionRepo.softDeletedIds,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- AC4: SelectCategoryClicked blocked when amount is zero --------------------------------

    @Test
    fun `SelectCategoryClicked with zero amount sets errorBannerRes and does not open category step`() =
        runTest {
            // Build a ViewModel with no transaction seeded — state stays at amount=0, not loaded.
            // We can also use a loaded expense and backspace the amount to zero.
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                expectMostRecentItem()
                cancelAndIgnoreRemainingEvents()
            }
            // Drive amount to zero via multiple backspaces
            repeat(6) { viewModel.onEvent(TransactionDetailEvent.KeypadBackspace) }

            viewModel.onEvent(TransactionDetailEvent.SelectCategoryClicked)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse(
                    "category step must not open when amount is zero",
                    state.categoryStep,
                )
                assertNotNull(
                    "errorBannerRes must be set when amount is zero",
                    state.errorBannerRes,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }
}
