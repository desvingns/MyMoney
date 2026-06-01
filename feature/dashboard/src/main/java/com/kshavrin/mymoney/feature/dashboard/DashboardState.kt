package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import java.time.YearMonth

data class DashboardState(
    val period: Period = Period.Month(YearMonth.now()),
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val currentAccount: Account? = null,
    val currentCurrency: Currency? = null,
    val balanceSnapshot: BalanceSnapshot? = null,
    val slices: List<CategorySlice> = emptyList(),
    val expenseCategoryPlaceholders: List<CategorySlice> = emptyList(),
    val budgetAlertCategoryIds: Set<Long> = emptySet(),
    val overBudgetAmount: Money? = null,
    val isLoading: Boolean = true,
    val leftDrawerOpen: Boolean = false,
    val rightDrawerOpen: Boolean = false,
    val showConfetti: Boolean = false,
)

sealed interface DashboardEvent {
    data class PeriodChanged(val period: Period) : DashboardEvent
    data class AccountChanged(val accountId: Long) : DashboardEvent
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
    data object CurrenciesClicked : DashboardEvent
    data object AboutClicked : DashboardEvent
    data object BalanceCardClicked : DashboardEvent
    data class SliceClicked(val categoryId: Long) : DashboardEvent
    data object ConfettiAcknowledged : DashboardEvent
}
