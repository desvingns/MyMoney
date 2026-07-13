package com.kshavrin.mymoney.core.designsystem.chart

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing

internal const val TREND_PREVIEW_WIDTH_DP = 360

// Fixed cumulative-balance series + weekday labels shared by the @Preview panes and the Roborazzi
// baseline so the neon wave renders identically every run.
internal val balanceTrendChartSamplePoints: List<Float> =
    listOf(140f, 95f, 175f, 120f, 205f, 165f, 250f)

internal val balanceTrendChartSampleLabels: List<String> =
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
internal fun BalanceTrendChartSample(modifier: Modifier = Modifier) {
    BalanceTrendChart(
        points = balanceTrendChartSamplePoints,
        modifier = modifier,
        labels = balanceTrendChartSampleLabels,
        showGridlines = true,
        showLabels = true,
    )
}

@Preview(name = "Trend · Light", showBackground = true, widthDp = TREND_PREVIEW_WIDTH_DP)
@Preview(name = "Trend · Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES, widthDp = TREND_PREVIEW_WIDTH_DP)
@Composable
private fun BalanceTrendChartPreview() {
    MyMoneyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            BalanceTrendChartSample(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.m),
            )
        }
    }
}
