package com.kshavrin.mymoney.core.domain.fake

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.RecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.time.Instant

class FakeCurrencyRepository : CurrencyRepository {
    private val state = MutableStateFlow<List<Currency>>(emptyList())
    fun seed(vararg currencies: Currency) {
        state.value = (state.value + currencies).distinctBy { it.id }.toList()
    }
    override fun observeActive() = state.asStateFlow()
    override fun observeAll() = state.asStateFlow()
    override suspend fun findById(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun findByCode(code: String) = state.value.firstOrNull { it.code.equals(code, ignoreCase = true) }
    override suspend fun upsert(currency: Currency): Long {
        val id = if (currency.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else currency.id
        val withId = currency.copy(id = id)
        state.value = state.value.filterNot { it.id == id } + withId
        return id
    }
    override suspend fun upsertAll(currencies: List<Currency>) {
        currencies.forEach { upsert(it) }
    }
    override suspend fun setActive(id: Long, active: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isActive = active) else it }
    }
}

class FakeCurrencyRateRepository : CurrencyRateRepository {
    private val state = MutableStateFlow<List<CurrencyRate>>(emptyList())
    fun seed(rate: CurrencyRate) {
        val id = if (rate.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else rate.id
        state.value = state.value.filterNot { it.id == id } + rate.copy(id = id)
    }
    override suspend fun findRate(fromCurrencyId: Long, toCurrencyId: Long) =
        state.value.firstOrNull { it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId }
    override fun observeAll() = state.asStateFlow()
    override suspend fun upsert(rate: CurrencyRate): Long {
        seed(rate)
        return rate.id
    }
    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeAccountRepository : AccountRepository {
    private val state = MutableStateFlow<List<Account>>(emptyList())
    fun seed(vararg accounts: Account) {
        state.value = (state.value + accounts).distinctBy { it.id }
    }
    override fun observeActive(): Flow<List<Account>> = state.asStateFlow()
    override suspend fun findById(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun findDefault() = state.value.firstOrNull { it.isDefault }
    override suspend fun computeBalance(accountId: Long): BigDecimal =
        state.value.firstOrNull { it.id == accountId }?.initialBalance ?: BigDecimal.ZERO
    override suspend fun upsert(account: Account): Long {
        val id = if (account.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else account.id
        state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
        return id
    }
    override suspend fun archive(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
    }
    override suspend fun setDefault(id: Long) {
        state.value = state.value.map { it.copy(isDefault = it.id == id) }
    }
    override suspend fun countByCurrency(currencyId: Long): Int =
        state.value.count { it.currencyId == currencyId && !it.isArchived }
}

class FakeBudgetRepository : BudgetRepository {
    private val state = MutableStateFlow<List<Budget>>(emptyList())
    fun seed(vararg budgets: Budget) {
        state.value = (state.value + budgets).distinctBy { it.id }
    }
    override fun observeActive(): Flow<List<Budget>> = state.asStateFlow()
    override suspend fun findForCategory(categoryId: Long) =
        state.value.firstOrNull { it.categoryId == categoryId && it.isActive }
    override suspend fun findTotalBudget() =
        state.value.firstOrNull { it.categoryId == null && it.isActive }
    override suspend fun upsert(budget: Budget): Long {
        val id = if (budget.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else budget.id
        state.value = state.value.filterNot { it.id == id } + budget.copy(id = id)
        return id
    }
    override suspend fun deactivate(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(isActive = false) else it }
    }
}

class FakeCategoryRepository : CategoryRepository {
    private val state = MutableStateFlow<List<Category>>(emptyList())
    fun seed(vararg categories: Category) {
        state.value = (state.value + categories).distinctBy { it.id }
    }
    override fun observeByKind(kind: CategoryKind): Flow<List<Category>> = state.asStateFlow()
    override fun observeAll(): Flow<List<Category>> = state.asStateFlow()
    override suspend fun findById(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(category: Category): Long {
        val id = if (category.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else category.id
        state.value = state.value.filterNot { it.id == id } + category.copy(id = id)
        return id
    }
    override suspend fun upsertAll(categories: List<Category>) {
        categories.forEach { upsert(it) }
    }
    override suspend fun archive(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
    }
}

class FakeTransactionRepository : TransactionRepository {
    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private var expenseSummary: List<CategorySummary> = emptyList()
    private var incomeSummary: List<CategorySummary> = emptyList()
    private var categoryGroups: List<CategoryGroup> = emptyList()
    private var periodTransactions: List<Transaction> = emptyList()

    fun seedExpenseSummary(vararg rows: CategorySummary) {
        expenseSummary = rows.toList()
    }
    fun seedIncomeSummary(vararg rows: CategorySummary) {
        incomeSummary = rows.toList()
    }
    fun seedCategoryGroups(vararg rows: CategoryGroup) {
        categoryGroups = rows.toList()
    }
    fun seedPeriodTransactions(vararg rows: Transaction) {
        periodTransactions = rows.toList()
    }

    fun upserted(): List<Transaction> = transactions.value

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = transactions.asStateFlow()
    override fun observeAll(): Flow<List<Transaction>> = transactions.asStateFlow()
    override fun paged(accountId: Long, categoryId: Long?, from: Instant, to: Instant): Flow<PagingData<Transaction>> =
        flowOf(PagingData.empty())
    override suspend fun findById(id: Long) = transactions.value.firstOrNull { it.id == id }
    override suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction> = periodTransactions
    override suspend fun getCategorySummary(accountId: Long, period: Period, kind: TransactionKind): List<CategorySummary> =
        if (kind == TransactionKind.Expense) expenseSummary else incomeSummary
    override suspend fun getCategoryGroups(accountId: Long, period: Period): List<CategoryGroup> = categoryGroups
    override suspend fun searchByNote(query: String, limit: Int): List<Transaction> = emptyList()
    override suspend fun upsert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) (transactions.value.maxOfOrNull { it.id } ?: 0L) + 1L else transaction.id
        transactions.value = transactions.value.filterNot { it.id == id } + transaction.copy(id = id)
        return id
    }
    override suspend fun softDelete(id: Long, now: Instant) {
        transactions.value = transactions.value.map { if (it.id == id) it.copy(isDeleted = true, updatedAt = now) else it }
    }
    override suspend fun restore(id: Long, now: Instant) {
        transactions.value = transactions.value.map { if (it.id == id) it.copy(isDeleted = false, updatedAt = now) else it }
    }
    override suspend fun pruneDeleted(before: Instant) {
        transactions.value = transactions.value.filterNot { it.isDeleted && it.updatedAt.isBefore(before) }
    }
    override suspend fun countByAccount(id: Long): Int =
        transactions.value.count { !it.isDeleted && (it.accountId == id || it.toAccountId == id) }
    override suspend fun countByCategory(id: Long): Int =
        transactions.value.count { !it.isDeleted && it.categoryId == id }
    override suspend fun countByCurrency(id: Long): Int =
        transactions.value.count { !it.isDeleted && it.currencyId == id }
}

class FakeRecurringTemplateRepository : RecurringTemplateRepository {
    private val templates = MutableStateFlow<List<RecurringTemplate>>(emptyList())

    val updateNextRunCalls = mutableListOf<Pair<Long, Instant>>()
    val deactivateCalls = mutableListOf<Long>()

    fun seed(vararg items: RecurringTemplate) {
        templates.value = (templates.value + items).distinctBy { it.id }
    }

    fun stored(id: Long): RecurringTemplate? = templates.value.firstOrNull { it.id == id }

    override suspend fun findDue(now: Instant): List<RecurringTemplate> =
        templates.value.filter { !it.nextRunAt.isAfter(now) }

    override fun observeAll(): Flow<List<RecurringTemplate>> = templates.asStateFlow()

    override suspend fun upsert(template: RecurringTemplate): Long {
        val id = if (template.id == 0L) (templates.value.maxOfOrNull { it.id } ?: 0L) + 1L else template.id
        templates.value = templates.value.filterNot { it.id == id } + template.copy(id = id)
        return id
    }

    override suspend fun updateNextRun(id: Long, nextRunAt: Instant) {
        updateNextRunCalls += id to nextRunAt
        templates.value = templates.value.map { if (it.id == id) it.copy(nextRunAt = nextRunAt) else it }
    }

    override suspend fun deactivate(id: Long) {
        deactivateCalls += id
        templates.value = templates.value.map { if (it.id == id) it.copy(isActive = false) else it }
    }
}
