package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.Instant

data class Account(
    val id: Long,
    val name: String,
    val currencyId: Long,
    val initialBalance: BigDecimal,
    val type: AccountType,
    val colorHex: String,
    val iconKey: String,
    val isDefault: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isArchived: Boolean,
)
