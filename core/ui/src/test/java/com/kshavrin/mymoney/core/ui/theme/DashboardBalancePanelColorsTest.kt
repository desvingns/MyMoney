package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBalancePanelColorsTest {
    @Test
    fun `theme mode palettes collapse to the same neon color scheme`() {
        assertSame(DarkColors, LightColors)
        assertEquals(Color(0xFF0A0E1C), LightColors.dashboardNeonBackground)
        assertEquals(Color(0xFF0A0E1C), DarkColors.dashboardNeonBackground)
    }

    @Test
    fun `dashboard neon ring and tile tokens expose requested colors`() {
        assertEquals(Color(0xFF5BE3B0), DarkColors.neonRingGradientStart)
        assertEquals(Color(0xFF46B6E6), DarkColors.neonRingGradientEnd)
        assertEquals(Color(0xFF1A2236), DarkColors.neonRingTrack)
        assertEquals(Color(0xFF111A2E), DarkColors.tileSurface)
        assertEquals(Color(0xFF1B2236), DarkColors.tileSurfaceAlt)
        assertEquals(Color(0xFFE8EAF0), DarkColors.textPrimary)
        assertEquals(Color(0xFF7C8290), DarkColors.textSecondary)
        assertEquals(Color(0xFF5BE3B0), DarkColors.incomeAccent)
        assertEquals(Color(0xFFFF8A80), DarkColors.expenseAccent)
    }

    @Test
    fun `dashboard fab tokens reflect the reduced size contract`() {
        assertEquals(94f, Spacing.dashboardFabSize.value, 0f)
        assertEquals(29f, Spacing.dashboardFabIconSize.value, 0f)
        assertEquals(3.6f, Spacing.dashboardFabOutlineWidth.value, 0f)
    }

    @Test
    fun `aurora balance panel keeps the display wash light and balance compact`() {
        assertTrue(DarkColors.dashboardAuroraInnerPanel.alpha <= 0.05f)
        assertEquals(26f, MoneyTypography.dashboardAuroraBalanceValueCompact.fontSize.value, 0f)
        assertEquals(30f, MoneyTypography.dashboardAuroraBalanceValueCompact.lineHeight.value, 0f)
    }

    @Test
    fun `surface variant content keeps normal text contrast readable`() {
        assertTrue(contrastRatio(DarkColors.onSurfaceVariant, DarkColors.surfaceVariant) >= 4.5)
    }

    @Test
    fun `dark color scheme exposes dedicated negative dashboard balance tokens`() {
        assertEquals(
            DarkColors.error.copy(alpha = 0.16f).compositeOver(DarkColors.surface),
            DarkColors.dashboardBalancePanelContainerNegative,
        )
        assertEquals(Color(0xFFEF9A9A), DarkColors.dashboardBalancePanelContentNegative)
    }

    @Test
    fun `shared realtime tokens preserve semantic container and content pairings`() {
        assertEquals(DarkColors.primaryContainer, DarkColors.sharedSyncConnectedContainer)
        assertEquals(DarkColors.onPrimaryContainer, DarkColors.sharedSyncConnectedContent)
        assertEquals(DarkColors.tertiaryContainer, DarkColors.sharedSyncStartingContainer)
        assertEquals(DarkColors.onTertiaryContainer, DarkColors.sharedSyncStartingContent)
        assertEquals(DarkColors.secondaryContainer, DarkColors.sharedSyncSleepingContainer)
        assertEquals(DarkColors.onSecondaryContainer, DarkColors.sharedSyncSleepingContent)
        assertEquals(DarkColors.surfaceVariant, DarkColors.sharedSyncRetryingContainer)
        assertEquals(DarkColors.onSurfaceVariant, DarkColors.sharedSyncRetryingContent)
        assertEquals(DarkColors.errorContainer, DarkColors.sharedSyncErrorContainer)
        assertEquals(DarkColors.onErrorContainer, DarkColors.sharedSyncErrorContent)
        assertEquals(DarkColors.outlineVariant, DarkColors.sharedSyncStatusOutline)
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
