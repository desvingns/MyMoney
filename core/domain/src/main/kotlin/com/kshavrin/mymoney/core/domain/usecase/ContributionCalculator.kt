package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import java.math.BigDecimal
import javax.inject.Inject

class ContributionCalculator
    @Inject
    constructor() {
        operator fun invoke(breakdown: ContributionBreakdown): BigDecimal =
            breakdown.incomes.total().subtract(breakdown.expenses.total())

        private fun List<ContributionItem>.total(): BigDecimal =
            fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
    }
