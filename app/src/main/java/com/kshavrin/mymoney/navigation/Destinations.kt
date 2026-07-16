package com.kshavrin.mymoney.navigation

import kotlinx.serialization.Serializable

object Destinations {
    @Serializable
    data object Decision

    @Serializable
    data object Splash

    @Serializable
    data object Onboarding

    @Serializable
    data object Dashboard

    @Serializable
    data object AddExpense

    @Serializable
    data object AddIncome

    @Serializable
    data object Transfer

    @Serializable
    data class CurrencyRate(
        val fromId: Long = -1L,
        val toId: Long = -1L,
    )

    @Serializable
    data class TransactionsList(
        val accountId: Long = -1L,
        val currencyId: Long = -1L,
        val categoryId: Long = -1L,
        val from: Long = -1L,
        val to: Long = -1L,
    )

    @Serializable
    data class TransactionDetail(
        val transactionId: Long,
    )

    @Serializable
    data object Search

    @Serializable
    data object Settings

    @Serializable
    data object SettingsTheme

    @Serializable
    data object SettingsLanguage

    @Serializable
    data object SettingsAbout

    @Serializable
    data object SettingsAboutPrivacy

    @Serializable
    data object SettingsAboutHelp

    @Serializable
    data object SettingsBackup

    @Serializable
    data class ImportWizard(
        val uri: String = "",
    )

    @Serializable
    data object CategoriesList

    @Serializable
    data class CategoryEdit(
        val id: Long = -1L,
        val kind: String? = null,
        val fromPicker: Boolean = false,
    )

    @Serializable
    data object AccountsList

    @Serializable
    data class AccountEdit(
        val id: Long = -1L,
    )

    @Serializable
    data object FinancialGoals

    @Serializable
    data class FinancialGoalEdit(
        val id: Long = -1L,
    )

    @Serializable
    data object CurrenciesList

    @Serializable
    data class CurrencyEdit(
        val id: Long = -1L,
    )

    @Serializable
    data object CloudSync

    @Serializable
    data object LockScreen
}

enum class ShortcutDestination {
    AddExpense,
    AddIncome,
    Transfer,
}
