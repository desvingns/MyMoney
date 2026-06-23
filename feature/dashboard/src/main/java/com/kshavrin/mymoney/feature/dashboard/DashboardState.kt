package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import java.time.YearMonth

data class DashboardState(
    val period: Period = Period.Month(YearMonth.now()),
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val dashboardSelection: DashboardSelection? = null,
    val balanceSnapshot: BalanceSnapshot? = null,
    // "All accounts → show separately" (D6): one balance card per currency that has accounts. Each
    // card's snapshot is self-contained in that currency — no conversion, no rates (G11). Empty in
    // every other selection/mode.
    val currencyCards: List<CurrencyBalanceCard> = emptyList(),
    val periodNet: Money = Money.zero(DASHBOARD_STATE_FALLBACK_CURRENCY),
    val ringFraction: Float = 0f,
    val ringIsExpense: Boolean = false,
    val trendPoints: List<TrendPoint> = emptyList(),
    val slices: List<CategorySlice> = emptyList(),
    val expenseTiles: List<CategoryTileItem> = emptyList(),
    val expenseCategoryPlaceholders: List<CategorySlice> = emptyList(),
    val budgetAlertCategoryIds: Set<Long> = emptySet(),
    val overBudgetAmount: Money? = null,
    val isLoading: Boolean = true,
    // Inline category drill-down (G5): tapping a real category tile expands its records in place
    // instead of navigating away. [expandedCategoryId] is the currently expanded category (null =
    // none), [expandedRecords] are that category's transactions for [period], and
    // [expandedRecordsLoading] is true while they are being fetched.
    val expandedCategoryId: Long? = null,
    val expandedRecords: List<Transaction> = emptyList(),
    val expandedRecordsLoading: Boolean = false,
    val leftDrawerOpen: Boolean = false,
    val rightDrawerOpen: Boolean = false,
    val showConfetti: Boolean = false,
    val chartConfig: ChartConfig = ChartConfig(),
    val chartSettingsSheetOpen: Boolean = false,
) {
    val currentAccount: Account?
        get() = (dashboardSelection as? DashboardSelection.SpecificAccount)?.account

    val currentCurrency: Currency?
        get() =
            when (val selection = dashboardSelection) {
                is DashboardSelection.AllAccounts ->
                    when (val mode = selection.foldMode) {
                        is AllAccountsFoldMode.ConvertTo -> mode.target
                        AllAccountsFoldMode.Separate -> null
                    }
                is DashboardSelection.SpecificAccount -> currencies.firstOrNull { it.id == selection.account.currencyId }
                null -> null
            }

    // True only in the "All accounts → show separately" multi-currency view. In this mode the
    // dashboard renders the stacked per-currency cards and hides the donut (D6).
    val isSeparateMode: Boolean
        get() =
            (dashboardSelection as? DashboardSelection.AllAccounts)?.foldMode == AllAccountsFoldMode.Separate
}

// One per-currency balance card for the "All accounts → show separately" view (G12). [snapshot] is
// computed by BalanceCalculator.forAccounts over the accounts in this one currency, so every
// figure is already in [currency] — no conversion is involved. [trendPoints] is the cumulative
// balance series for that same currency group (G17); empty when the chart is hidden in settings.
data class CurrencyBalanceCard(
    val currency: Currency,
    val snapshot: BalanceSnapshot,
    val trendPoints: List<TrendPoint> = emptyList(),
)

// Persisted chart configuration (AppSettings, SPEC 02) projected into the dashboard state. Drives
// BOTH the rendered chart (style/color/toggles) and the trend recompute (period type / point
// count / metric). [periodType] == Follow means the chart window mirrors the dashboard period;
// otherwise the window is built from an independent anchor of that period type.
data class ChartConfig(
    val visible: Boolean = true,
    val style: ChartStyle = ChartStyle.Default,
    val periodType: ChartPeriodType = ChartPeriodType.Follow,
    val pointCount: Int = DEFAULT_CHART_POINT_COUNT,
    val metric: ChartMetric = ChartMetric.CUMULATIVE,
    val showGridlines: Boolean = true,
    val showLabels: Boolean = true,
    val colorRule: ChartColorRule = ChartColorRule.Default,
)

// How the trend window is anchored relative to the dashboard. Follow tracks the dashboard period;
// the calendar variants build an independent window of that granularity.
enum class ChartPeriodType {
    Follow,
    Day,
    Week,
    Month,
    Year,
}

const val DEFAULT_CHART_POINT_COUNT = 5
val CHART_POINT_COUNT_RANGE = 3..12

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

    // "All accounts" no longer pins one currency (D8): it spans every account regardless of
    // currency. How the figures are presented is decided by [foldMode] — either folded into one
    // target currency (the convert-to-one path resolved here in SPEC 07) or shown separately per
    // currency (the stacked-cards path delivered by SPEC 08).
    data class AllAccounts(
        val foldMode: AllAccountsFoldMode,
    ) : DashboardSelection
}

sealed interface AllAccountsFoldMode {
    // Convert every account's balance into [target] and sum them into a single snapshot.
    data class ConvertTo(
        val target: Currency,
    ) : AllAccountsFoldMode

    // Show each currency group on its own; rendering belongs to SPEC 08.
    data object Separate : AllAccountsFoldMode
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

    // User tapped the single "All accounts" row → open the convert/separate fork dialog.
    data object AllAccountsSelected : DashboardEvent

    // Fork dialog: user chose "fold everything into one currency" → ask for the target currency.
    data object AllAccountsConvertChosen : DashboardEvent

    // Fork dialog: user chose "show each currency separately" (rendered by SPEC 08).
    data object AllAccountsSeparateChosen : DashboardEvent

    // Target-currency picker: user picked the currency every conversion folds into (D7 — asked
    // every time, never remembered). The ViewModel then resolves the rates and opens the
    // rate-confirmation list.
    data class AllAccountsTargetCurrencyChosen(
        val currencyId: Long,
    ) : DashboardEvent

    // Rate-confirmation list confirmed. [rateOverrides] maps a source currency id to the one-shot
    // cross-rate the user accepted/edited; edits are NOT persisted (D5).
    data class AllAccountsRatesConfirmed(
        val rateOverrides: Map<Long, java.math.BigDecimal>,
    ) : DashboardEvent

    // Any all-accounts conversion dialog was dismissed without finishing.
    data object AllAccountsConversionDismissed : DashboardEvent

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

    // Tap on the chart area opens the settings sheet (G1/G9).
    data object ChartTapped : DashboardEvent

    // «Настройки графика» entry in the ⋮ menu — the way to reach the sheet while the chart is
    // hidden (D13).
    data object ChartSettingsClicked : DashboardEvent

    data object ChartSettingsDismissed : DashboardEvent

    data class ChartStyleChanged(
        val style: ChartStyle,
    ) : DashboardEvent

    data class ChartPeriodTypeChanged(
        val periodType: ChartPeriodType,
    ) : DashboardEvent

    data class ChartPointCountChanged(
        val pointCount: Int,
    ) : DashboardEvent

    data class ChartMetricChanged(
        val metric: ChartMetric,
    ) : DashboardEvent

    data class ChartGridlinesToggled(
        val enabled: Boolean,
    ) : DashboardEvent

    data class ChartLabelsToggled(
        val enabled: Boolean,
    ) : DashboardEvent

    data class ChartColorRuleChanged(
        val colorRule: ChartColorRule,
    ) : DashboardEvent

    data class ChartVisibilityChanged(
        val visible: Boolean,
    ) : DashboardEvent
}
