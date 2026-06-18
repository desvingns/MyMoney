package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import java.time.YearMonth

data class DashboardState(
    val period: Period = Period.Month(YearMonth.now()),
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val dashboardSelection: DashboardSelection? = null,
    val balanceSnapshot: BalanceSnapshot? = null,
    val periodNet: Money = Money.zero(DASHBOARD_STATE_FALLBACK_CURRENCY),
    val ringFraction: Float = 0f,
    val slices: List<CategorySlice> = emptyList(),
    val expenseTiles: List<CategoryTileItem> = emptyList(),
    val expenseCategoryPlaceholders: List<CategorySlice> = emptyList(),
    val budgetAlertCategoryIds: Set<Long> = emptySet(),
    val overBudgetAmount: Money? = null,
    val isLoading: Boolean = true,
    val leftDrawerOpen: Boolean = false,
    val rightDrawerOpen: Boolean = false,
    val showConfetti: Boolean = false,
) {
    val currentAccount: Account?
        get() = (dashboardSelection as? DashboardSelection.SpecificAccount)?.account

    val currentCurrency: Currency?
        get() =
            when (val selection = dashboardSelection) {
                is DashboardSelection.AllAccounts -> selection.currency
                is DashboardSelection.SpecificAccount -> currencies.firstOrNull { it.id == selection.account.currencyId }
                null -> null
            }
}

private val DASHBOARD_STATE_FALLBACK_CURRENCY =
    Currency(
        id = 0L,
        code = "",
        symbol = "",
        name = "",
        decimalDigits = 2,
        isActive = false,
        sortOrder = 0,
    )

sealed interface DashboardSelection {
    data class SpecificAccount(
        val account: Account,
    ) : DashboardSelection

    data class AllAccounts(
        val currency: Currency,
    ) : DashboardSelection
}

sealed interface DashboardEvent {
    data class PeriodChanged(
        val period: Period,
    ) : DashboardEvent

    data object PreviousPeriod : DashboardEvent

    data object NextPeriod : DashboardEvent

    data class AccountSelected(
        val accountId: Long,
    ) : DashboardEvent

    data object AllAccountsSelected : DashboardEvent

    data object LeftDrawerToggled : DashboardEvent

    data object RightDrawerToggled : DashboardEvent

    data object DrawerDismissed : DashboardEvent

    data object MinusFabClicked : DashboardEvent

    data object PlusFabClicked : DashboardEvent

    data object TransferClicked : DashboardEvent

    data object SearchClicked : DashboardEvent

    data object SettingsClicked : DashboardEvent

    data object CategoriesClicked : DashboardEvent

    data object AccountsClicked : DashboardEvent

    data object FinancialGoalsClicked : DashboardEvent

    data object CurrenciesClicked : DashboardEvent

    data object AboutClicked : DashboardEvent

    data object BalanceCardClicked : DashboardEvent

    data class SliceClicked(
        val categoryId: Long,
    ) : DashboardEvent

    data object ConfettiAcknowledged : DashboardEvent
}
