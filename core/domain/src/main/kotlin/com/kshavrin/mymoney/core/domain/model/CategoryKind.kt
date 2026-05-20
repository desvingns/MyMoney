package com.kshavrin.mymoney.core.domain.model

enum class CategoryKind {
    Expense, Income;

    companion object {
        fun fromString(value: String): CategoryKind = values().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown CategoryKind: $value")
    }
}
