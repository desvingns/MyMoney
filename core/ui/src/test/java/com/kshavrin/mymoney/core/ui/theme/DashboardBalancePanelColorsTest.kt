package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardBalancePanelColorsTest {
    @Test
    fun `light color scheme keeps the existing non negative dashboard balance tokens`() {
        assertEquals(Color(0xFFE9F7EF), LightColors.dashboardBalancePanelContainer)
        assertEquals(Color(0xFF066A35), LightColors.dashboardBalancePanelContent)
    }

    @Test
    fun `light color scheme exposes dedicated negative dashboard balance tokens`() {
        assertEquals(Color(0xFFFCEAEA), LightColors.dashboardBalancePanelContainerNegative)
        assertEquals(Color(0xFFD64545), LightColors.dashboardBalancePanelContentNegative)
    }

    @Test
    fun `dark color scheme exposes dedicated negative dashboard balance tokens`() {
        assertEquals(
            DarkColors.error.copy(alpha = 0.16f).compositeOver(DarkColors.surface),
            DarkColors.dashboardBalancePanelContainerNegative,
        )
        assertEquals(Color(0xFFEF9A9A), DarkColors.dashboardBalancePanelContentNegative)
    }
}
