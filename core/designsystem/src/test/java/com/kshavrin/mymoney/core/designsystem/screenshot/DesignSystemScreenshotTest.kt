package com.kshavrin.mymoney.core.designsystem.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChartSample
import com.kshavrin.mymoney.core.designsystem.donut.MonefyDonutChartSample
import com.kshavrin.mymoney.core.designsystem.form.TransactionFormSample
import com.kshavrin.mymoney.core.designsystem.form.transactionFormAmountStepSampleState
import com.kshavrin.mymoney.core.designsystem.form.transactionFormCategoryStepSampleState
import com.kshavrin.mymoney.core.designsystem.keypad.MonefyKeypadSample
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// JVM Roborazzi + Robolectric screenshot baseline suite for :core:designsystem components.
//
// Re-record baselines:     ./gradlew :core:designsystem:recordRoborazziDebug
// Opt-in verify (CI gate): ./gradlew :core:designsystem:verifyRoborazziDebug
//
// Each @Test writes one PNG to src/test/screenshots/<name>.png (resolved by the Roborazzi plugin
// relative to the module directory). The PNGs are committed to VCS as golden baselines.
//
// All rendering is deterministic: fixed screen metrics (Pixel-5-class, 411dp × 914dp xxhdpi),
// snap() animations in MonefyDonutChartSample (no mid-animation frame), and seeded sample data
// shared with the @Preview composables in the same module.
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBORAZZI_SDK],
    qualifiers = "w411dp-h914dp-xxhdpi",
    application = android.app.Application::class,
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---- MonefyDonutChart -----------------------------------------------------------------------

    @Test
    fun `donut chart light`() {
        composeTestRule.captureThemed("donut_light", darkTheme = false) {
            MonefyDonutChartSample()
        }
    }

    @Test
    fun `donut chart dark`() {
        composeTestRule.captureThemed("donut_dark", darkTheme = true) {
            MonefyDonutChartSample()
        }
    }

    // ---- BalanceTrendChart ----------------------------------------------------------------------

    @Test
    fun `balance trend chart light`() {
        composeTestRule.captureThemed("balance_trend_light", darkTheme = false) {
            BalanceTrendChartSample()
        }
    }

    @Test
    fun `balance trend chart dark`() {
        composeTestRule.captureThemed("balance_trend_dark", darkTheme = true) {
            BalanceTrendChartSample()
        }
    }

    // ---- MonefyKeypad ---------------------------------------------------------------------------

    @Test
    fun `keypad light`() {
        composeTestRule.captureThemed("keypad_light", darkTheme = false) {
            MonefyKeypadSample()
        }
    }

    @Test
    fun `keypad dark`() {
        composeTestRule.captureThemed("keypad_dark", darkTheme = true) {
            MonefyKeypadSample()
        }
    }

    // ---- TransactionForm — amount step ----------------------------------------------------------

    @Test
    fun `transaction form amount step light`() {
        composeTestRule.captureThemed("transaction_form_amount_light", darkTheme = false) {
            TransactionFormSample(state = transactionFormAmountStepSampleState())
        }
    }

    @Test
    fun `transaction form amount step dark`() {
        composeTestRule.captureThemed("transaction_form_amount_dark", darkTheme = true) {
            TransactionFormSample(state = transactionFormAmountStepSampleState())
        }
    }

    // ---- TransactionForm — category step --------------------------------------------------------

    @Test
    fun `transaction form category step light`() {
        composeTestRule.captureThemed("transaction_form_category_light", darkTheme = false) {
            TransactionFormSample(state = transactionFormCategoryStepSampleState())
        }
    }

    @Test
    fun `transaction form category step dark`() {
        composeTestRule.captureThemed("transaction_form_category_dark", darkTheme = true) {
            TransactionFormSample(state = transactionFormCategoryStepSampleState())
        }
    }
}
