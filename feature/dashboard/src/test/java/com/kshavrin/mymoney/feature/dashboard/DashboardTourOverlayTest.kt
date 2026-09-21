package com.kshavrin.mymoney.feature.dashboard

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.tour.TourPhase
import com.kshavrin.mymoney.feature.dashboard.tour.TourStep
import com.kshavrin.mymoney.feature.dashboard.tour.TourUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardTourOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun render(state: DashboardState) {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(state = state, onEvent = {})
            }
        }
    }

    @Test
    fun `overlay shows title progress and both buttons on the first step`() {
        render(baseState.copy(tour = TourUiState(TourStep.Actions, TourPhase.Panel)))

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_actions_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_progress, 1, 4)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_skip_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_next)).assertIsDisplayed()
    }

    @Test
    fun `last step shows the done label`() {
        render(
            baseState.copy(
                tour = TourUiState(TourStep.RightSupport, TourPhase.Panel),
                rightDrawerOpen = true,
            ),
        )

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_support_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_done)).assertIsDisplayed()
    }

    @Test
    fun `no overlay when there is no tour`() {
        render(baseState.copy(tour = null))

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_skip_all)).assertDoesNotExist()
    }

    @Test
    fun `no overlay while the tour is paused`() {
        render(baseState.copy(tour = TourUiState(TourStep.Actions, TourPhase.Panel, paused = true)))

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_skip_all)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+ru")
    fun `russian locale renders russian tour strings`() {
        render(baseState.copy(tour = TourUiState(TourStep.Actions, TourPhase.Panel)))

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_actions_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Расходы, переводы, доходы").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_progress, 1, 4)).assertIsDisplayed()
        composeTestRule.onNodeWithText("1 из 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Далее").assertIsDisplayed()
        composeTestRule.onNodeWithText("Пропустить всё").assertIsDisplayed()
    }

    @Test
    fun `tour card and controls are shown at font scale 1_5`() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.5f),
            ) {
                MyMoneyTheme {
                    DashboardContent(
                        state = baseState.copy(tour = TourUiState(TourStep.Actions, TourPhase.Panel)),
                        onEvent = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_actions_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_progress, 1, 4)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_skip_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_tour_next)).assertIsDisplayed()
    }

    private companion object {
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

        fun usd(amount: String): Money = Money(BigDecimal(amount), currency)

        val baseState =
            DashboardState(
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
