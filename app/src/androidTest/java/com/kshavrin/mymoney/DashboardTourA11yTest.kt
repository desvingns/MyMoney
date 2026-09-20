package com.kshavrin.mymoney

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.DashboardContent
import com.kshavrin.mymoney.feature.dashboard.DashboardState
import com.kshavrin.mymoney.feature.dashboard.tour.TourPhase
import com.kshavrin.mymoney.feature.dashboard.tour.TourStep
import com.kshavrin.mymoney.feature.dashboard.tour.TourUiState
import com.kshavrin.mymoney.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.test.assertTouchWidthIsAtLeast
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR

@RunWith(AndroidJUnit4::class)
class DashboardTourA11yTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tourControlsPassAccessibilityChecksAndAreAtLeast48dp() {
        composeRule.setContent {
            composeRule.enableAccessibilityChecks()
            MyMoneyTheme {
                DashboardContent(
                    state = baseState().copy(tour = TourUiState(TourStep.Actions, TourPhase.Panel)),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_skip_all))
            .assertIsDisplayed()
            .assertTouchHeightIsAtLeast(48.dp)
            .assertTouchWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_next))
            .assertIsDisplayed()
            .assertTouchHeightIsAtLeast(48.dp)
            .assertTouchWidthIsAtLeast(48.dp)
    }

    @Test
    fun tourCardIsNotClippedAtFontScale200Percent() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 2.0f),
            ) {
                MyMoneyTheme {
                    DashboardContent(
                        state = baseState().copy(tour = TourUiState(TourStep.RightSupport, TourPhase.Panel), rightDrawerOpen = true),
                        onEvent = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_support_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(DashboardR.string.dashboard_tour_done))
            .assertIsDisplayed()
            .assertTouchHeightIsAtLeast(48.dp)
            .assertTouchWidthIsAtLeast(48.dp)
    }

    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun baseState(): DashboardState {
        val currency =
            Currency(
                id = 1L,
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            )
        fun usd(amount: String) = Money(BigDecimal(amount), currency)
        return DashboardState(
            period = Period.Day(LocalDate.of(2024, 6, 15)),
            isLoading = false,
            balanceSnapshot =
                BalanceSnapshot(
                    income = usd("100.00"),
                    expense = usd("40.00"),
                    net = usd("60.00"),
                    byCategory = emptyList(),
                ),
            periodNet = usd("60.00"),
        )
    }
}
