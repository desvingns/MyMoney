package com.kshavrin.mymoney.core.domain.usecase

import java.math.BigDecimal

fun capitalVsBalanceDelta(currentBalance: BigDecimal, startingCapital: BigDecimal): BigDecimal =
    currentBalance.subtract(startingCapital)
