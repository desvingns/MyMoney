package com.kshavrin.mymoney.core.ui.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint")
enum class PaywallEntryPoint {
    SupportSection,
    SharedSyncGate,
}

object Destinations {
    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Decision")
    data object Decision

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Splash")
    data object Splash

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Onboarding")
    data object Onboarding

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Dashboard")
    data class Dashboard(
        val openChartSettings: Boolean = false,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.AddExpense")
    data object AddExpense

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.AddIncome")
    data object AddIncome

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Transfer")
    data object Transfer

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.CurrencyRate")
    data class CurrencyRate(
        val fromId: Long = -1L,
        val toId: Long = -1L,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.TransactionsList")
    data class TransactionsList(
        val accountId: Long = -1L,
        val currencyId: Long = -1L,
        val categoryId: Long = -1L,
        val from: Long = -1L,
        val to: Long = -1L,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.TransactionDetail")
    data class TransactionDetail(
        val transactionId: Long,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Search")
    data object Search

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Settings")
    data object Settings

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Support")
    data object Support

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.Paywall")
    data class Paywall(
        val entryPoint: PaywallEntryPoint = PaywallEntryPoint.SupportSection,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.SettingsTheme")
    data object SettingsTheme

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.SettingsLanguage")
    data object SettingsLanguage

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.SettingsAbout")
    data object SettingsAbout

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.SettingsAboutPrivacy")
    data object SettingsAboutPrivacy

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.SettingsAboutHelp")
    data object SettingsAboutHelp

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.SettingsBackup")
    data object SettingsBackup

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.ImportWizard")
    data class ImportWizard(
        val uri: String = "",
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.CategoriesList")
    data object CategoriesList

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.CategoryEdit")
    data class CategoryEdit(
        val id: Long = -1L,
        val kind: String? = null,
        val fromPicker: Boolean = false,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.AccountsList")
    data object AccountsList

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.AccountEdit")
    data class AccountEdit(
        val id: Long = -1L,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.FinancialGoals")
    data object FinancialGoals

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.FinancialGoalEdit")
    data class FinancialGoalEdit(
        val id: Long = -1L,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.CurrenciesList")
    data object CurrenciesList

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.CurrencyEdit")
    data class CurrencyEdit(
        val id: Long = -1L,
    )

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.CloudSync")
    data object CloudSync

    @Serializable
    @SerialName("com.kshavrin.mymoney.navigation.Destinations.LockScreen")
    data object LockScreen
}
