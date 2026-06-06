package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBalancePanelColorTokensTest {

    @Test
    fun `light negative balance tokens keep large text contrast above 3 to 1`() {
        val contrast = contrastRatio(
            foreground = LightColors.dashboardBalancePanelContentNegative,
            background = LightColors.dashboardBalancePanelContainerNegative,
        )

        assertTrue("light negative balance contrast must be at least 3.0 but was $contrast", contrast >= 3.0)
    }

    @Test
    fun `dark negative balance tokens keep large text contrast above 3 to 1`() {
        val contrast = contrastRatio(
            foreground = DarkColors.dashboardBalancePanelContentNegative,
            background = DarkColors.dashboardBalancePanelContainerNegative,
        )

        assertTrue("dark negative balance contrast must be at least 3.0 but was $contrast", contrast >= 3.0)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = luminance(foreground.toArgb())
        val backgroundLuminance = luminance(background.toArgb())
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun linear(channel: Int): Double {
            val srgb = channel / 255.0
            return if (srgb <= 0.04045) srgb / 12.92 else Math.pow((srgb + 0.055) / 1.055, 2.4)
        }

        val red = linear((argb shr 16) and 0xFF)
        val green = linear((argb shr 8) and 0xFF)
        val blue = linear(argb and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }
}
