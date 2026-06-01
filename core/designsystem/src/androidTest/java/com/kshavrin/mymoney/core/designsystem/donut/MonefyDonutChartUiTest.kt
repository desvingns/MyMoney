package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class MonefyDonutChartUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `announces income expense and multiple slice percentages in merged semantics`() {
        setChart(
            income = BigDecimal("450.00"),
            expense = BigDecimal("124.00"),
            slices = listOf(
                slice(label = "Food", fraction = 0.50f),
                slice(label = "Transport", fraction = 0.25f),
                slice(label = "Home", fraction = 0.25f),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 25, "Home" to 25),
                ),
            )
            .assertExists()
    }

    @Test
    fun `announces totals without slice descriptions when chart is empty`() {
        setChart(
            income = BigDecimal.ZERO,
            expense = BigDecimal.ZERO,
            slices = emptyList(),
        )

        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "0", expense = "0"))
            .assertExists()
    }

    @Test
    fun `truncates a three point four percent slice to three percent in semantics`() {
        setChart(
            income = BigDecimal.ZERO,
            expense = BigDecimal("100.00"),
            slices = listOf(slice(label = "Coffee", fraction = 0.034f)),
        )

        // Instrumentation asserts accessibility semantics; screenshot or device checks cover drawn Canvas labels.
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "0",
                    expense = "100.00",
                    slices = listOf("Coffee" to 3),
                ),
            )
            .assertExists()
    }

    @Test
    fun `center totals expose both income and expense figures when a currency symbol is supplied`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("500.00"),
                    expense = BigDecimal("124.00"),
                    slices = listOf(slice(label = "Food", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    currencySymbol = "$",
                    decimalDigits = 2,
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "500.00",
                    expense = "124.00",
                    slices = listOf("Food" to 100),
                ),
            )
            .assertExists()
    }

    @Test
    fun `empty period with placeholder icons still announces zero totals`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal.ZERO,
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = listOf(
                        slice(label = "Food", fraction = 0f),
                        slice(label = "Transport", fraction = 0f),
                        slice(label = "Home", fraction = 0f),
                    ),
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "0", expense = "0"))
            .assertExists()
    }

    @Test
    fun `empty state icons do not add slice descriptions to semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal.ZERO,
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = listOf(slice(label = "Food", fraction = 0f)),
                    animationSpec = snap(),
                )
            }
        }

        // Placeholder icons are drawn on the Canvas only; they must NOT leak into the
        // slice portion of the accessibility description (that is reserved for real slices).
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(income = "0", expense = "0", slices = listOf("Food" to 0)),
            )
            .assertDoesNotExist()
    }

    @Test
    fun `populated chart ignores empty state icons and keeps slice semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("450.00"),
                    expense = BigDecimal("124.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.50f),
                        slice(label = "Transport", fraction = 0.50f),
                    ),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = listOf(slice(label = "Placeholder", fraction = 0f)),
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 50),
                ),
            )
            .assertExists()
    }

    private fun setChart(
        income: BigDecimal,
        expense: BigDecimal,
        slices: List<CategorySlice>,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = income,
                    expense = expense,
                    slices = slices,
                    modifier = Modifier.size(240.dp),
                    animationSpec = snap(),
                )
            }
        }
    }

    private fun slice(label: String, fraction: Float) = CategorySlice(
        categoryId = label.hashCode().toLong(),
        color = Color.Green,
        fraction = fraction,
        label = label,
    )

    private fun expectedDescription(
        income: String,
        expense: String,
        slices: List<Pair<String, Int>> = emptyList(),
    ): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = context.getString(R.string.donut_chart_cd, income, expense)
        val sliceText = slices.joinToString(separator = " ") { (label, percent) ->
            context.getString(R.string.donut_chart_slice, label, percent)
        }
        return if (sliceText.isEmpty()) header else "$header $sliceText"
    }
}
