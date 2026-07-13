package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate

// JVM Roborazzi + Robolectric screenshot baseline suite for the dashboard day-view screen state.
//
// Re-record baselines:     ./gradlew :feature:dashboard:recordRoborazziDebug
// Opt-in verify (CI gate): ./gradlew :feature:dashboard:verifyRoborazziDebug
//
// Each @Test writes one PNG to src/test/screenshots/<name>.png (resolved by the Roborazzi plugin
// relative to the module directory). The PNGs are committed to VCS as golden baselines.
//
// Rendering is deterministic: fixed screen metrics (Pixel-5-class, 411dp × 914dp xxhdpi),
// fixed date (2024-06-15), seeded currency/amounts, no animations,
// all overlay sheets/drawers closed, showConfetti = false.
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [DASHBOARD_SCREENSHOT_SDK],
    qualifiers = "w411dp-h914dp-xxhdpi",
    application = android.app.Application::class,
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dashboard day view light`() {
        captureState("dashboard_day_light", darkTheme = false)
    }

    @Test
    fun `dashboard day view dark`() {
        captureState("dashboard_day_dark", darkTheme = true)
    }

    // Renders DashboardContent over the app background in the requested theme and writes the PNG.
    // The pattern mirrors ScreenshotTestHarness.captureThemed in :core:designsystem; it is
    // inlined here because the harness lives in a test source set and is not exposed to
    // downstream modules.
    private fun captureState(name: String, darkTheme: Boolean) {
        val state = dashboardDayState()
        composeTestRule.setContent {
            MyMoneyTheme(darkTheme = darkTheme) {
                Box(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .testTag(DASHBOARD_SCREENSHOT_ROOT_TAG),
                ) {
                    DashboardContent(state = state, onEvent = {})
                }
            }
        }
        composeTestRule
            .onNodeWithTag(DASHBOARD_SCREENSHOT_ROOT_TAG)
            .captureRoboImage("$DASHBOARD_SCREENSHOT_DIR/$name.png")
    }
}

// Robolectric API level for the screenshot render — pinned for reproducibility.
private const val DASHBOARD_SCREENSHOT_SDK = 34

// Baseline PNGs land next to the test sources; Roborazzi resolves the path against the module dir.
private const val DASHBOARD_SCREENSHOT_DIR = "src/test/screenshots"

private const val DASHBOARD_SCREENSHOT_ROOT_TAG = "dashboard_screenshot_root"

// ---- seeded state -------------------------------------------------------------------------------

private val sampleCurrency =
    Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

private fun usd(amount: String): Money = Money(BigDecimal(amount), sampleCurrency)

// Fixed anchor date — never LocalDate.now() so the PNG is identical on every machine and every day.
private val sampleDate: LocalDate = LocalDate.of(2024, 6, 15)

private fun dashboardDayState(): DashboardState =
    DashboardState(
        period = Period.Day(sampleDate),
        isLoading = false,
        balanceSnapshot =
            BalanceSnapshot(
                income = usd("1500.00"),
                expense = usd("840.00"),
                net = usd("660.00"),
                byCategory = emptyList(),
            ),
        periodNet = usd("660.00"),
        trendPoints =
            listOf(
                TrendPoint(0, Period.Day(sampleDate.minusDays(4)), usd("1200.00")),
                TrendPoint(1, Period.Day(sampleDate.minusDays(3)), usd("1320.00")),
                TrendPoint(2, Period.Day(sampleDate.minusDays(2)), usd("1100.00")),
                TrendPoint(3, Period.Day(sampleDate.minusDays(1)), usd("1450.00")),
                TrendPoint(4, Period.Day(sampleDate), usd("1660.00")),
            ),
        trendAxis = ChartTrendAxis.Days,
        chartConfig =
            ChartConfig(
                visible = true,
                showGridlines = true,
                showLabels = true,
            ),
        expenseTiles =
            listOf(
                CategoryTileItem(
                    categoryId = 1L,
                    label = "Food",
                    amount = usd("420.00"),
                    fraction = 0.50f,
                    colorHex = "#E07AAE",
                    iconKey = "ic_cat_food",
                ),
                CategoryTileItem(
                    categoryId = 2L,
                    label = "Transport",
                    amount = usd("240.00"),
                    fraction = 0.29f,
                    colorHex = "#E07A7A",
                    iconKey = "ic_cat_transport",
                ),
                CategoryTileItem(
                    categoryId = 3L,
                    label = "Housing",
                    amount = usd("180.00"),
                    fraction = 0.21f,
                    colorHex = "#4A8FCB",
                    iconKey = "ic_cat_housing",
                ),
            ),
        slices = emptyList(),
        showConfetti = false,
        leftDrawerOpen = false,
        rightDrawerOpen = false,
        chartSettingsSheetOpen = false,
        operationsSummary = null,
    )
