package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import java.time.YearMonth

data class TransactionsListUiState(
    val accountId: Long? = null,
    val currencyId: Long? = null,
    val categoryId: Long? = null,
    val period: Period = Period.Month(YearMonth.now()),
    val groups: List<CategoryRecordGroup> = emptyList(),
    val expandedCategoryIds: Set<Long> = emptySet(),
    val sort: RecordSort = RecordSort.TotalDesc,
    val net: Money? = null,
    val currency: Currency? = null,
    val isLoading: Boolean = true,
) {
    val sortedGroups: List<CategoryRecordGroup>
        get() = when (sort) {
            RecordSort.TotalDesc -> groups.sortedByDescending { it.total.amount }
            RecordSort.TotalAsc -> groups.sortedBy { it.total.amount }
        }

    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}
