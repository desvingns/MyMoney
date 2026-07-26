package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.runtime.Immutable
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.YearMonth

@Immutable
data class TransactionsListUiState(
    val accountId: Long? = null,
    val currencyId: Long? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val period: Period = Period.Month(YearMonth.now()),
    val records: ImmutableList<TransactionsListRecord> = persistentListOf(),
    val isLoading: Boolean = true,
) {
    val hasCategoryFilter: Boolean get() = categoryId != null && !categoryName.isNullOrBlank()

    val isEmpty: Boolean get() = !isLoading && records.isEmpty()
}

@Immutable
data class TransactionsListRecord(
    val record: SummaryRecord,
    val currency: Currency?,
    val categoryDisplay: TransactionCategoryDisplay?,
)

data class TransactionCategoryDisplay(
    val name: String,
    val iconKey: String,
)
