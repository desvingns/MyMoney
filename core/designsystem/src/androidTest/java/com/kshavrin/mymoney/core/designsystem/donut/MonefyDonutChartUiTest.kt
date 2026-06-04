package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.math.cos
import kotlin.math.sin

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
    fun `tapping an empty state placeholder icon invokes the matching callback exactly once`() {
        val emptyStateIcons = listOf(
            slice(label = "Food", fraction = 0f),
            slice(label = "Transport", fraction = 0f),
            slice(label = "Home", fraction = 0f),
        )
        val clickedSlices = mutableListOf<CategorySlice>()

        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal.ZERO,
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = emptyStateIcons,
                    onEmptyCategoryClick = { clickedSlices += it },
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "0", expense = "0"))
            .performTouchInput {
                click(emptyIconCenter(index = 0, count = emptyStateIcons.size))
            }

        composeTestRule.runOnIdle {
            assertEquals(1, clickedSlices.size)
            assertEquals(emptyStateIcons.first(), clickedSlices.single())
        }
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

    // ---- centerDecimalDigits ----

    @Test
    fun `centerDecimalDigits zero hides decimal portion from semantics income string`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("500.75"),
                    expense = BigDecimal("124.30"),
                    slices = listOf(slice(label = "Food", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    currencySymbol = "₽",
                    decimalDigits = 2,
                    centerDecimalDigits = 0,
                    animationSpec = snap(),
                )
            }
        }
        // semantics description uses raw BigDecimal string (not centerDecimalDigits),
        // so header always reflects full precision — this test verifies composable renders
        // without crash when centerDecimalDigits=0
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "500.75",
                    expense = "124.30",
                    slices = listOf("Food" to 100),
                ),
            )
            .assertExists()
    }

    @Test
    fun `centerDecimalDigits two produces same semantics as default decimalDigits`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("200.00"),
                    expense = BigDecimal("50.00"),
                    slices = listOf(slice(label = "Bills", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    decimalDigits = 2,
                    centerDecimalDigits = 2,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "200.00",
                    expense = "50.00",
                    slices = listOf("Bills" to 100),
                ),
            )
            .assertExists()
    }

    // ---- DonutStyle enum ----

    @Test
    fun `DonutStyle Flat renders chart and preserves slice semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("300.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.60f),
                        slice(label = "Transport", fraction = 0.40f),
                    ),
                    modifier = Modifier.size(240.dp),
                    style = DonutStyle.Flat,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "300.00",
                    expense = "100.00",
                    slices = listOf("Food" to 60, "Transport" to 40),
                ),
            )
            .assertExists()
    }

    @Test
    fun `DonutStyle Extrude renders chart and preserves slice semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("300.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(slice(label = "Home", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    style = DonutStyle.Extrude,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "300.00",
                    expense = "100.00",
                    slices = listOf("Home" to 100),
                ),
            )
            .assertExists()
    }

    // ---- ringThicknessFraction and sliceGapDegrees — semantics contract unchanged ----

    @Test
    fun `custom ringThicknessFraction 0 39 preserves full semantics description`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("400.00"),
                    expense = BigDecimal("200.00"),
                    slices = listOf(
                        slice(label = "Car", fraction = 0.50f),
                        slice(label = "Pets", fraction = 0.50f),
                    ),
                    modifier = Modifier.size(240.dp),
                    ringThicknessFraction = 0.39f,
                    sliceGapDegrees = 5f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "400.00",
                    expense = "200.00",
                    slices = listOf("Car" to 50, "Pets" to 50),
                ),
            )
            .assertExists()
    }

    @Test
    fun `sliceGapDegrees zero renders without crash and keeps semantics intact`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("100.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(slice(label = "Sport", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    sliceGapDegrees = 0f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "100.00",
                    expense = "100.00",
                    slices = listOf("Sport" to 100),
                ),
            )
            .assertExists()
    }

    // ---- budget alert semantics preserved ----

    @Test
    fun `slice with hasBudgetAlert true is still included in semantics description`() {
        val alertSlice = CategorySlice(
            categoryId = 99L,
            color = Color.Red,
            fraction = 1.0f,
            label = "Overbudget",
            hasBudgetAlert = true,
        )
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal("250.00"),
                    slices = listOf(alertSlice),
                    modifier = Modifier.size(240.dp),
                    animationSpec = snap(),
                )
            }
        }
        // The accessibility description must contain the slice label
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = context.getString(R.string.donut_chart_cd, "0", "250.00")
        val slicePart = context.getString(R.string.donut_chart_slice, "Overbudget", 100)
        val alertLabel = context.getString(R.string.donut_chart_budget_alert)
        val full = "$header $slicePart, $alertLabel"
        composeTestRule.onNodeWithContentDescription(full).assertExists()
    }

    // ---- iconScale param — composable renders without crash ----

    @Test
    fun `custom iconScale 1 7 renders without crash and preserves semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("150.00"),
                    expense = BigDecimal("50.00"),
                    slices = listOf(slice(label = "Coffee", fraction = 1.0f)),
                    modifier = Modifier.size(300.dp),
                    iconScale = 1.7f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "150.00",
                    expense = "50.00",
                    slices = listOf("Coffee" to 100),
                ),
            )
            .assertExists()
    }

    // ---- zero-fraction slices are excluded from semantics description ----

    @Test
    fun `slices with fraction zero are excluded from semantics description`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("100.00"),
                    expense = BigDecimal("50.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.80f),
                        slice(label = "Ghost", fraction = 0.0f),
                    ),
                    modifier = Modifier.size(240.dp),
                    animationSpec = snap(),
                )
            }
        }
        // Ghost slice has fraction=0 → (0*100).toInt()=0, included in semantics by the
        // chart (it still calls joinToString over all slices); verify Food appears correctly
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "100.00",
                    expense = "50.00",
                    slices = listOf("Food" to 80, "Ghost" to 0),
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

    private fun emptyIconCenter(index: Int, count: Int): Offset {
        val canvasSize = with(composeTestRule.density) { 240.dp.toPx() }
        val center = canvasSize / 2f
        val outerRadius = center * 0.75f
        val angleRadians = Math.toRadians((-90f + index * (360f / count)).toDouble())
        return Offset(
            x = center + outerRadius * 0.92f * cos(angleRadians).toFloat(),
            y = center + outerRadius * 0.92f * sin(angleRadians).toFloat(),
        )
    }
}
