package com.kshavrin.mymoney.core.domain.model

enum class TransactionKind {
    Expense,
    Income,
    Transfer,
    ;

    companion object {
        fun fromString(value: String): TransactionKind =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown TransactionKind: $value")
    }
}
