package com.kshavrin.mymoney.core.designsystem.donut

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kshavrin.mymoney.core.ui.theme.CategoryColors
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import java.math.BigDecimal

internal const val DONUT_PREVIEW_SIZE_DP = 320

// Seeded slice set shared by the @Preview panes and the Roborazzi screenshot baseline so the
// design-system donut always renders the same fixed data off-device.
internal val monefyDonutChartSampleSlices: List<CategorySlice> =
    listOf(
        CategorySlice(
            categoryId = 1L,
            color = CategoryColors.getValue("food"),
            fraction = 0.42f,
            label = "Food",
            iconKey = "ic_cat_food",
        ),
        CategorySlice(
            categoryId = 2L,
            color = CategoryColors.getValue("housing"),
            fraction = 0.27f,
            label = "Housing",
            iconKey = "ic_cat_housing",
        ),
        CategorySlice(
            categoryId = 3L,
            color = CategoryColors.getValue("transport"),
            fraction = 0.19f,
            label = "Transport",
            iconKey = "ic_cat_transport",
        ),
        CategorySlice(
            categoryId = 4L,
            color = CategoryColors.getValue("entertainment"),
            fraction = 0.12f,
            label = "Fun",
            iconKey = "ic_cat_entertainment",
        ),
    )

@Composable
internal fun MonefyDonutChartSample(modifier: Modifier = Modifier) {
    MonefyDonutChart(
        income = BigDecimal("2500"),
        expense = BigDecimal("1840"),
        slices = monefyDonutChartSampleSlices,
        modifier = modifier,
        currencySymbol = "$",
        centerDecimalDigits = 0,
        // Screenshot determinism: skip the entry animation so the ring is captured fully grown.
        animationSpec = snap(),
    )
}

@Preview(name = "Donut · Light", showBackground = true, widthDp = DONUT_PREVIEW_SIZE_DP, heightDp = DONUT_PREVIEW_SIZE_DP)
@Preview(
    name = "Donut · Dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = DONUT_PREVIEW_SIZE_DP,
    heightDp = DONUT_PREVIEW_SIZE_DP,
)
@Composable
private fun MonefyDonutChartPreview() {
    MyMoneyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            MonefyDonutChartSample(modifier = Modifier.fillMaxSize())
        }
    }
}
