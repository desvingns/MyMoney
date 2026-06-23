package com.kshavrin.mymoney.core.domain.model

import java.time.Instant

data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val colorHex: String,
    val textColor: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Instant,
)
