package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.designsystem.dialog.RateRow

sealed interface DashboardAction {
    // One-shot dialog triggers for the "All accounts" conversion flow (replay = 0). The host
    // composable shows the matching dialog and feeds the user's choice back as a DashboardEvent.

    // Fork: fold everything into one currency, or show each currency separately.
    data object ShowAllAccountsModeDialog : DashboardAction

    // Ask which currency every account folds into. Asked every time (D7), so the picker is
    // re-shown from scratch on each "All accounts → convert" selection.
    data class ShowTargetCurrencyPicker(
        val currencies: List<com.kshavrin.mymoney.core.domain.model.Currency>,
    ) : DashboardAction

    // Confirm/edit the cross-rates used for the fold. [sourceCurrencyIds] runs parallel to [rows]
    // so the confirm callback can map each edited row back to its source currency id.
    data class ShowAllAccountsRateConfirm(
        val rows: List<RateRow>,
        val sourceCurrencyIds: List<Long>,
    ) : DashboardAction

    data object NavigateAddExpense : DashboardAction

    data object NavigateAddIncome : DashboardAction

    data object NavigateTransfer : DashboardAction

    data object NavigateSearch : DashboardAction

    data object NavigateSettings : DashboardAction

    data object NavigateCategories : DashboardAction

    data object NavigateAccounts : DashboardAction

    data object NavigateFinancialGoals : DashboardAction

    data object NavigateCurrencies : DashboardAction

    data class NavigateToTransactionDetail(
        val transactionId: Long,
    ) : DashboardAction

    data class NavigateToTransactionsList(
        val accountId: Long?,
        val currencyId: Long?,
        val categoryId: Long?,
        val fromMillis: Long,
        val toMillis: Long,
    ) : DashboardAction

    data object NavigateAbout : DashboardAction
}
