package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import java.time.YearMonth

data class TransactionsListUiState(
    val accountId: Long? = null,
    val currencyId: Long? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val period: Period = Period.Month(YearMonth.now()),
    val records: List<SummaryRecord> = emptyList(),
    val currencies: Map<Long, Currency> = emptyMap(),
    val categoryDisplays: Map<Long, TransactionCategoryDisplay> = emptyMap(),
    val isLoading: Boolean = true,
) {
    val hasCategoryFilter: Boolean get() = categoryId != null && !categoryName.isNullOrBlank()

    val isEmpty: Boolean get() = !isLoading && records.isEmpty()
}

data class TransactionCategoryDisplay(
    val name: String,
    val iconKey: String,
)
