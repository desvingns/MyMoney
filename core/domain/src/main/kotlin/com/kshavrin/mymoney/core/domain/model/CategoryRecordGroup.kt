package com.kshavrin.mymoney.core.domain.model

data class CategoryRecordGroup(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val kind: CategoryKind,
    val total: Money,
    val count: Int,
    val transactions: List<Transaction>,
)
