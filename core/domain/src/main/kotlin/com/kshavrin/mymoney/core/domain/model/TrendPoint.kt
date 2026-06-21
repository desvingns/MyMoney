package com.kshavrin.mymoney.core.domain.model

data class TrendPoint(
    val index: Int,
    val period: Period,
    val value: Money,
    val income: Money? = null,
    val expense: Money? = null,
)
