package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

class BalanceCalculatorTest {
    private val may2026 = Period.Month(YearMonth.of(2026, 5))
    private val usd = currency(id = 1L, code = "USD", symbol = "$", name = "US Dollar")
    private val eur = currency(id = 2L, code = "EUR", symbol = "EUR", name = "Euro")

    @Test
    fun computes_per_category_aggregates() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)

            val accountRepo = FakeAccountRepository().apply { seed(account) }
            val currencyRepo = FakeCurrencyRepository().apply { seed(usd) }
            val transactionRepo =
                FakeTransactionRepository().apply {
                    seedExpenseSummary(
                        CategorySummary(categoryId = 100L, categoryName = "Food", colorHex = "#E07AAE", total = BigDecimal("30.00")),
                        CategorySummary(categoryId = 101L, categoryName = "Bills", colorHex = "#C9A227", total = BigDecimal("70.00")),
                    )
                    seedIncomeSummary(
                        CategorySummary(categoryId = 200L, categoryName = "Salary", colorHex = "#7AC29A", total = BigDecimal("500.00")),
                    )
                }

            val calculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
            val snapshot = calculator(10L, may2026)

            assertScaledAmount(snapshot.expense.amount, "100.00")
            assertScaledAmount(snapshot.income.amount, "500.00")
            assertScaledAmount(snapshot.net.amount, "400.00")
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 100L }
                    .total
                    .amount,
                "30.00",
            )
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 101L }
                    .total
                    .amount,
                "70.00",
            )
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 200L }
                    .total
                    .amount,
                "500.00",
            )
            assertEquals(3, snapshot.byCategory.size)
        }

    @Test
    fun `carries iconKey from each category summary into the emitted CategoryBalance`() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)

            val accountRepo = FakeAccountRepository().apply { seed(account) }
            val currencyRepo = FakeCurrencyRepository().apply { seed(usd) }
            val transactionRepo =
                FakeTransactionRepository().apply {
                    seedExpenseSummary(
                        CategorySummary(categoryId = 100L, categoryName = "Food", colorHex = "#E07AAE", total = BigDecimal("30.00"), iconKey = "food"),
                        CategorySummary(categoryId = 101L, categoryName = "Bills", colorHex = "#C9A227", total = BigDecimal("70.00"), iconKey = "bills"),
                    )
                    seedIncomeSummary(
                        CategorySummary(categoryId = 200L, categoryName = "Salary", colorHex = "#7AC29A", total = BigDecimal("500.00"), iconKey = "salary"),
                    )
                }

            val calculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
            val snapshot = calculator(10L, may2026)

            assertEquals(
                "food",
                snapshot.byCategory
                    .single { it.categoryId == 100L }
                    .iconKey,
            )
            assertEquals(
                "bills",
                snapshot.byCategory
                    .single { it.categoryId == 101L }
                    .iconKey,
            )
            assertEquals(
                "salary",
                snapshot.byCategory
                    .single { it.categoryId == 200L }
                    .iconKey,
            )
        }

    @Test
    fun `forAccounts aggregates only active accounts in the selected currency`() =
        runTest {
            val cash = account(id = 10L, name = "Cash", currencyId = usd.id)
            val card = account(id = 11L, name = "Card", currencyId = usd.id)
            val archived = account(id = 12L, name = "Archived", currencyId = usd.id, isArchived = true)
            val euro = account(id = 13L, name = "Euro", currencyId = eur.id)
            val accountRepo = FakeAccountRepository().apply { seed(cash, card, archived, euro) }
            val currencyRepo = FakeCurrencyRepository().apply { seed(usd, eur) }
            val transactionRepo =
                FakeTransactionRepository().apply {
                    seedExpenseSummary(cash.id, may2026, CategorySummary(100L, "Food", "#E07AAE", BigDecimal("30.00")))
                    seedIncomeSummary(cash.id, may2026, CategorySummary(200L, "Salary", "#7AC29A", BigDecimal("120.00")))
                    seedExpenseSummary(card.id, may2026, CategorySummary(100L, "Food", "#E07AAE", BigDecimal("10.00")))
                    seedIncomeSummary(card.id, may2026, CategorySummary(300L, "Bonus", "#7AC794", BigDecimal("20.00")))
                    seedExpenseSummary(archived.id, may2026, CategorySummary(100L, "Food", "#E07AAE", BigDecimal("999.00")))
                    seedIncomeSummary(euro.id, may2026, CategorySummary(400L, "FX", "#00AAFF", BigDecimal("999.00")))
                }

            val snapshot =
                BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
                    .forAccounts(listOf(cash, card, archived), usd, may2026)

            assertScaledAmount(snapshot.expense.amount, "40.00")
            assertScaledAmount(snapshot.income.amount, "140.00")
            assertScaledAmount(snapshot.net.amount, "100.00")
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 100L }
                    .total
                    .amount,
                "40.00",
            )
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 200L }
                    .total
                    .amount,
                "120.00",
            )
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 300L }
                    .total
                    .amount,
                "20.00",
            )
            assertEquals(
                setOf(100L, 200L, 300L),
                snapshot.byCategory
                    .map { it.categoryId }
                    .toSet(),
            )
        }

    @Test
    fun `forAccounts rejects mixed-currency selections`() =
        runTest {
            val cash = account(id = 10L, name = "Cash", currencyId = usd.id)
            val euro = account(id = 11L, name = "Euro", currencyId = eur.id)
            val calculator =
                BalanceCalculator(
                    accountRepository = FakeAccountRepository().apply { seed(cash, euro) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd, eur) },
                    transactionRepository = FakeTransactionRepository(),
                    defaultDispatcher = UnconfinedTestDispatcher(),
                )

            try {
                calculator.forAccounts(listOf(cash, euro), usd, may2026)
                fail("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }

    @Test
    fun `normalizes classic 0 point 1 plus 0 point 2 artifacts before emitting snapshot amounts`() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)
            val snapshot =
                BalanceCalculator(
                    accountRepository = FakeAccountRepository().apply { seed(account) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd) },
                    transactionRepository =
                        FakeTransactionRepository().apply {
                            seedIncomeSummary(
                                CategorySummary(
                                    categoryId = 200L,
                                    categoryName = "Salary",
                                    colorHex = "#7AC29A",
                                    total = BigDecimal("0.30000000000000004"),
                                    iconKey = "salary",
                                ),
                            )
                        },
                    defaultDispatcher = UnconfinedTestDispatcher(),
                )(account.id, may2026)

            assertScaledAmount(snapshot.income.amount, "0.30")
            assertScaledAmount(snapshot.expense.amount, "0.00")
            assertScaledAmount(snapshot.net.amount, "0.30")
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 200L }
                    .total
                    .amount,
                "0.30",
            )
        }

    @Test
    fun `uses the account currency decimal digits instead of hardcoded scale two`() =
        runTest {
            val jpy =
                currency(
                    id = 3L,
                    code = "JPY",
                    symbol = "JPY",
                    name = "Japanese Yen",
                    decimalDigits = 0,
                )
            val account = account(id = 10L, name = "Cash", currencyId = jpy.id)
            val snapshot =
                BalanceCalculator(
                    accountRepository = FakeAccountRepository().apply { seed(account) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(jpy) },
                    transactionRepository =
                        FakeTransactionRepository().apply {
                            seedIncomeSummary(
                                CategorySummary(
                                    categoryId = 200L,
                                    categoryName = "Salary",
                                    colorHex = "#7AC29A",
                                    total = BigDecimal("10.5"),
                                    iconKey = "salary",
                                ),
                            )
                        },
                    defaultDispatcher = UnconfinedTestDispatcher(),
                )(account.id, may2026)

            assertScaledAmount(snapshot.income.amount, "11", expectedScale = 0)
            assertScaledAmount(snapshot.expense.amount, "0", expectedScale = 0)
            assertScaledAmount(snapshot.net.amount, "11", expectedScale = 0)
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 200L }
                    .total
                    .amount,
                "11",
                expectedScale = 0,
            )
        }

    @Test
    fun `rounds floating point tails in computed balances to two decimals`() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)
            val snapshot =
                BalanceCalculator(
                    accountRepository = FakeAccountRepository().apply { seed(account) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd) },
                    transactionRepository =
                        FakeTransactionRepository().apply {
                            seedExpenseSummary(
                                CategorySummary(
                                    categoryId = 100L,
                                    categoryName = "Goal",
                                    colorHex = "#E07AAE",
                                    total = BigDecimal("1182337.0799999996"),
                                    iconKey = "goal",
                                ),
                            )
                        },
                    defaultDispatcher = UnconfinedTestDispatcher(),
                )(account.id, may2026)

            assertScaledAmount(snapshot.income.amount, "0.00")
            assertScaledAmount(snapshot.expense.amount, "1182337.08")
            assertScaledAmount(snapshot.net.amount, "-1182337.08")
            assertScaledAmount(
                snapshot.byCategory
                    .single { it.categoryId == 100L }
                    .total
                    .amount,
                "1182337.08",
            )
        }

    @Test
    fun `returns scaled zero amounts when category summaries are empty`() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)
            val snapshot =
                BalanceCalculator(
                    accountRepository = FakeAccountRepository().apply { seed(account) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd) },
                    transactionRepository = FakeTransactionRepository(),
                    defaultDispatcher = UnconfinedTestDispatcher(),
                )(account.id, may2026)

            assertScaledAmount(snapshot.income.amount, "0.00")
            assertScaledAmount(snapshot.expense.amount, "0.00")
            assertScaledAmount(snapshot.net.amount, "0.00")
            assertEquals(emptyList<Long>(), snapshot.byCategory.map { it.categoryId })
        }

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
        name: String,
        decimalDigits: Int = 2,
    ) = Currency(
        id = id,
        code = code,
        symbol = symbol,
        name = name,
        decimalDigits = decimalDigits,
        isActive = true,
        sortOrder = 0,
    )

    private fun account(
        id: Long,
        name: String,
        currencyId: Long,
        isArchived: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_cash",
        isDefault = id == 10L,
        sortOrder = 0,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        isArchived = isArchived,
    )

    private fun assertScaledAmount(
        actual: BigDecimal,
        expectedValue: String,
        expectedScale: Int = 2,
    ) {
        assertEquals(0, BigDecimal(expectedValue).compareTo(actual))
        assertEquals(expectedScale, actual.scale())
    }
}
