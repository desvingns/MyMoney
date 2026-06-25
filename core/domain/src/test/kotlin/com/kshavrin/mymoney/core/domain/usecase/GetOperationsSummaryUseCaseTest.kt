package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.TransferRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class GetOperationsSummaryUseCaseTest {
    private val period = Period.Day(LocalDate.parse("2026-05-20"))
    private val primaryAccountId = 1L
    private val secondaryAccountId = 2L
    private val thirdAccountId = 3L
    private val usdId = 9L
    private val categoryFoodId = 100L
    private val categorySalaryId = 200L

    private val accountRepo = FakeAccountRepository()
    private val currencyRepo = FakeCurrencyRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val getCategoryRecords =
        GetCategoryRecordsUseCase(
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    private val getTransferRecords =
        GetTransferRecordsUseCase(
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    private val useCase =
        GetOperationsSummaryUseCase(
            getCategoryRecords = getCategoryRecords,
            getTransferRecords = getTransferRecords,
        )

    private val usd =
        Currency(
            id = usdId,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    @Test
    fun `unfiltered account summary includes expense income and transfer records`() =
        runTest {
            seedCurrencies()
            accountRepo.seed(account(primaryAccountId))
            transactionRepo.seedCategoryGroups(
                primaryAccountId,
                period,
                categoryGroup(categoryFoodId, CategoryKind.Expense),
                categoryGroup(categorySalaryId, CategoryKind.Income),
            )
            transactionRepo.seedPeriodTransactions(
                primaryAccountId,
                period,
                transaction(
                    id = 11L,
                    occurredAt = Instant.parse("2026-05-20T08:00:00Z"),
                    kind = TransactionKind.Expense,
                    categoryId = categoryFoodId,
                ),
                transaction(
                    id = 12L,
                    occurredAt = Instant.parse("2026-05-20T09:00:00Z"),
                    kind = TransactionKind.Income,
                    categoryId = categorySalaryId,
                ),
            )
            transactionRepo.seedTransfers(
                primaryAccountId,
                period,
                transferRow(
                    id = 21L,
                    occurredAt = Instant.parse("2026-05-20T10:00:00Z"),
                ),
            )

            val result = useCase(primaryAccountId, period)

            assertEquals(listOf(21L, 12L, 11L), result.map { it.id })
            assertEquals(
                listOf(
                    SummaryRecord.Transfer::class,
                    SummaryRecord.Operation::class,
                    SummaryRecord.Operation::class,
                ),
                result.map { it::class },
            )
        }

    @Test
    fun `category filter includes only matching operations and does not request transfers`() =
        runTest {
            seedCurrencies()
            accountRepo.seed(account(primaryAccountId))
            transactionRepo.seedCategoryGroups(
                primaryAccountId,
                period,
                categoryGroup(categoryFoodId, CategoryKind.Expense, count = 2),
                categoryGroup(999L, CategoryKind.Expense),
            )
            transactionRepo.seedPeriodTransactions(
                primaryAccountId,
                period,
                transaction(
                    id = 31L,
                    occurredAt = Instant.parse("2026-05-20T08:00:00Z"),
                    kind = TransactionKind.Expense,
                    categoryId = categoryFoodId,
                ),
                transaction(
                    id = 32L,
                    occurredAt = Instant.parse("2026-05-20T09:00:00Z"),
                    kind = TransactionKind.Expense,
                    categoryId = 999L,
                ),
                transaction(
                    id = 33L,
                    occurredAt = Instant.parse("2026-05-20T10:00:00Z"),
                    kind = TransactionKind.Expense,
                    categoryId = categoryFoodId,
                ),
            )
            transactionRepo.seedTransfers(
                primaryAccountId,
                period,
                transferRow(
                    id = 41L,
                    occurredAt = Instant.parse("2026-05-20T11:00:00Z"),
                ),
            )

            val result = useCase(primaryAccountId, period, categoryId = categoryFoodId)

            assertEquals(listOf(33L, 31L), result.map { it.id })
            assertTrue(result.all { it is SummaryRecord.Operation })
            assertEquals(
                listOf(categoryFoodId, categoryFoodId),
                result.map { (it as SummaryRecord.Operation).categoryId },
            )
            assertTrue(transactionRepo.transferRequests.isEmpty())
        }

    @Test
    fun `empty period returns empty summary`() =
        runTest {
            seedCurrencies()
            accountRepo.seed(account(primaryAccountId))
            transactionRepo.seedPeriodTransactions(primaryAccountId, period)
            transactionRepo.seedTransfers(primaryAccountId, period)

            val result = useCase(primaryAccountId, period)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `sorts by timestamp descending with deterministic secondary keys`() =
        runTest {
            seedCurrencies()
            accountRepo.seed(account(primaryAccountId))
            val tiedTimestamp = Instant.parse("2026-05-20T10:00:00Z")
            transactionRepo.seedCategoryGroups(
                primaryAccountId,
                period,
                categoryGroup(categoryFoodId, CategoryKind.Expense),
                categoryGroup(categorySalaryId, CategoryKind.Income),
            )
            transactionRepo.seedPeriodTransactions(
                primaryAccountId,
                period,
                transaction(
                    id = 51L,
                    occurredAt = tiedTimestamp,
                    kind = TransactionKind.Expense,
                    categoryId = categoryFoodId,
                ),
                transaction(
                    id = 53L,
                    occurredAt = tiedTimestamp,
                    kind = TransactionKind.Income,
                    categoryId = categorySalaryId,
                ),
            )
            transactionRepo.seedTransfers(
                primaryAccountId,
                period,
                transferRow(
                    id = 52L,
                    occurredAt = tiedTimestamp,
                ),
            )

            val result = useCase(primaryAccountId, period)

            assertEquals(listOf(53L, 52L, 51L), result.map { it.id })
        }

    @Test
    fun `forAccounts includes active accounts and deduplicates internal transfers`() =
        runTest {
            seedCurrencies()
            val primary = account(primaryAccountId, name = "Cash")
            val secondary = account(secondaryAccountId, name = "Card")
            val archived = account(thirdAccountId, name = "Archived", isArchived = true)
            accountRepo.seed(primary, secondary, archived)
            transactionRepo.seedCategoryGroups(
                primaryAccountId,
                period,
                categoryGroup(categoryFoodId, CategoryKind.Expense),
            )
            transactionRepo.seedCategoryGroups(
                secondaryAccountId,
                period,
                categoryGroup(categorySalaryId, CategoryKind.Income),
            )
            transactionRepo.seedCategoryGroups(
                thirdAccountId,
                period,
                categoryGroup(categoryFoodId, CategoryKind.Expense),
            )
            transactionRepo.seedPeriodTransactions(
                primaryAccountId,
                period,
                transaction(
                    id = 61L,
                    occurredAt = Instant.parse("2026-05-20T08:00:00Z"),
                    kind = TransactionKind.Expense,
                    categoryId = categoryFoodId,
                    accountId = primaryAccountId,
                ),
            )
            transactionRepo.seedPeriodTransactions(
                secondaryAccountId,
                period,
                transaction(
                    id = 62L,
                    occurredAt = Instant.parse("2026-05-20T09:00:00Z"),
                    kind = TransactionKind.Income,
                    categoryId = categorySalaryId,
                    accountId = secondaryAccountId,
                ),
            )
            transactionRepo.seedPeriodTransactions(
                thirdAccountId,
                period,
                transaction(
                    id = 63L,
                    occurredAt = Instant.parse("2026-05-20T10:00:00Z"),
                    kind = TransactionKind.Expense,
                    categoryId = categoryFoodId,
                    accountId = thirdAccountId,
                ),
            )
            transactionRepo.seedTransfers(
                primaryAccountId,
                period,
                transferRow(
                    id = 71L,
                    occurredAt = Instant.parse("2026-05-20T12:00:00Z"),
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                ),
            )
            transactionRepo.seedTransfers(
                secondaryAccountId,
                period,
                transferRow(
                    id = 71L,
                    occurredAt = Instant.parse("2026-05-20T12:00:00Z"),
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                ),
            )
            transactionRepo.seedTransfers(
                thirdAccountId,
                period,
                transferRow(
                    id = 72L,
                    occurredAt = Instant.parse("2026-05-20T13:00:00Z"),
                    fromAccountName = "Archived",
                    toAccountName = "Cash",
                ),
            )

            val result = useCase.forAccounts(listOf(primary, secondary, archived), usd, period)

            assertEquals(listOf(71L, 62L, 61L), result.map { it.id })
            assertEquals(1, result.filterIsInstance<SummaryRecord.Transfer>().size)
            assertEquals(listOf(62L, 61L), result.filterIsInstance<SummaryRecord.Operation>().map { it.id })
            assertEquals(listOf(primaryAccountId to period, secondaryAccountId to period), transactionRepo.transferRequests)
        }

    private fun seedCurrencies() {
        currencyRepo.seed(usd)
    }

    private fun account(
        id: Long,
        name: String = "Account $id",
        isArchived: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        currencyId = usdId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#FFAA00",
        iconKey = "wallet",
        isDefault = id == primaryAccountId,
        sortOrder = id.toInt(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        isArchived = isArchived,
    )

    private fun categoryGroup(
        categoryId: Long,
        kind: CategoryKind,
        total: String = "1.00",
        count: Int = 1,
    ) = CategoryGroup(
        categoryId = categoryId,
        name = "Category $categoryId",
        iconKey = "ic_$categoryId",
        colorHex = "#FFAA00",
        kind = kind,
        total = BigDecimal(total),
        count = count,
        textColorHex = "#111111",
    )

    private fun transaction(
        id: Long,
        occurredAt: Instant,
        kind: TransactionKind,
        categoryId: Long?,
        accountId: Long = primaryAccountId,
        amount: String = "1.00",
    ) = Transaction(
        id = id,
        kind = kind,
        amount = BigDecimal(amount),
        currencyId = usdId,
        accountId = accountId,
        categoryId = categoryId,
        note = null,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private fun transferRow(
        id: Long,
        occurredAt: Instant,
        amount: String = "5.00",
        fromAccountName: String = "Cash",
        toAccountName: String = "Card",
    ) = TransferRow(
        id = id,
        fromAccountName = fromAccountName,
        toAccountName = toAccountName,
        amount = BigDecimal(amount),
        toAmount = null,
        currencyId = usdId,
        occurredAt = occurredAt,
        note = null,
    )
}
