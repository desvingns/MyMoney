package com.kshavrin.mymoney.core.domain.model

enum class AccountType {
    Cash, Card, Bank, Savings;

    companion object {
        fun fromString(value: String): AccountType = values().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown AccountType: $value")
    }
}
