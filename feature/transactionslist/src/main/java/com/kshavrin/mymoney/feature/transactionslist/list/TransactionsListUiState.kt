package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.Currency

data class TransactionsListUiState(
    val accountId: Long = -1L,
    val categoryId: Long? = null,
    val categoryFilter: Category? = null,
    val currency: Currency? = null,
)
