package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal

data class ContributionItem(
    val name: String,
    val amount: BigDecimal,
)

data class ContributionBreakdown(
    val enabled: Boolean = false,
    val incomes: List<ContributionItem> = emptyList(),
    val expenses: List<ContributionItem> = emptyList(),
) {
    companion object {
        val EMPTY = ContributionBreakdown()
    }
}
