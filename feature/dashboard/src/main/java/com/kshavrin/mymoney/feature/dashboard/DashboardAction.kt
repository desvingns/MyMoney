package com.kshavrin.mymoney.feature.dashboard

sealed interface DashboardAction {
    data object NavigateAddExpense : DashboardAction
    data object NavigateAddIncome : DashboardAction
    data object NavigateTransfer : DashboardAction
    data object NavigateSearch : DashboardAction
    data object NavigateSettings : DashboardAction
    data object NavigateCategories : DashboardAction
    data object NavigateAccounts : DashboardAction
    data object NavigateFinancialGoals : DashboardAction
    data object NavigateCurrencies : DashboardAction
    data class NavigateTransactionsByAccount(val accountId: Long) : DashboardAction
    data class NavigateTransactionsByCurrency(val currencyId: Long) : DashboardAction
    data class NavigateTransactionsByCategory(
        val accountId: Long?,
        val currencyId: Long,
        val categoryId: Long,
    ) : DashboardAction
    data object NavigateAbout : DashboardAction
}
