package com.kshavrin.mymoney.core.domain.model

data class Currency(
    val id: Long,
    val code: String,
    val symbol: String,
    val name: String,
    val decimalDigits: Int,
    val isActive: Boolean,
    val sortOrder: Int,
)
