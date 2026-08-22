package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupportThemeTokensTest {
    @Test
    fun `support colors preserve semantic role aliases in both theme palettes`() {
        assertSupportColors(LightColors)
        assertSupportColors(DarkColors)
    }

    @Test
    fun `support color aliases do not introduce private hex literals`() {
        val source = colorSource.readText().replace("\r\n", "\n")
        val supportColorBlock =
            source
                .substringAfter("val ColorScheme.supportPanelContainer")
                .substringBefore("// Rewarded-ad block")

        assertTrue(supportColorBlock.isNotBlank())
        assertFalse(supportColorBlock.contains("Color(0x"))
    }

    @Test
    fun `support shapes preserve the requested corner radii`() {
        assertEquals(20f, cornerRadius(MoneyShapes.supportPanel), 0f)
        assertEquals(12f, cornerRadius(MoneyShapes.supportPanelIllustration), 0f)
        assertEquals(18f, cornerRadius(MoneyShapes.supportHeroIllustration), 0f)
        assertEquals(24f, cornerRadius(MoneyShapes.supportPrimaryAction), 0f)
        assertEquals(13f, cornerRadius(MoneyShapes.supporterChip), 0f)
    }

    @Test
    fun `support spacing preserves the layout dimensions and touch target floor`() {
        assertEquals(56.dp, Spacing.supportTopBarHeight)
        assertEquals(48.dp, Spacing.supportBackTouchTarget)
        assertEquals(196.dp, Spacing.supportHeroSize)
        assertEquals(14.dp, Spacing.supportPanelGap)
        assertEquals(16.dp, Spacing.supportPanelPadding)
        assertEquals(10.dp, Spacing.supportPanelColumnGap)
        assertEquals(66.dp, Spacing.supportCoffeeIllustrationWidthSmall)
        assertEquals(82.dp, Spacing.supportCoffeeIllustrationHeight)
        assertEquals(52.dp, Spacing.supportPanelIconSize)
        assertEquals(84.dp, Spacing.supportAvatarSize)
        assertEquals(296.dp, Spacing.supportSubtitleMaxWidth)
        assertEquals(26.dp, Spacing.supporterChipHeight)
        assertEquals(3.dp, Spacing.supportHeadlineUnderlineThickness)
        assertTrue(Spacing.supportActionMinHeight >= 48.dp)
    }

    @Test
    fun `support typography preserves the requested hierarchy`() {
        assertStyle(
            MoneyTypography.supportHeadline,
            fontSize = 33.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 36.sp,
            letterSpacing = (-0.8).sp,
        )
        assertStyle(
            MoneyTypography.supportSubtitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 22.sp,
        )
        assertStyle(
            MoneyTypography.supportPanelTitle,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp,
        )
        assertStyle(
            MoneyTypography.supportPanelSubtitle,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp,
        )
        assertStyle(MoneyTypography.supportProductName, 15.sp, FontWeight.SemiBold)
        assertStyle(
            MoneyTypography.supportProductPrice,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        assertStyle(MoneyTypography.supportActionLabel, 15.sp, FontWeight.Bold)
        assertStyle(MoneyTypography.supportBackLabel, 15.sp, FontWeight.SemiBold)
        assertStyle(
            MoneyTypography.supporterChipLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        assertStyle(MoneyTypography.supportCounterValue, 17.sp, FontWeight.Bold)
        assertStyle(MoneyTypography.supportCounterLabel, 12.sp, FontWeight.Normal)
    }

    private fun assertSupportColors(colors: ColorScheme) {
        assertEquals(colors.surface, colors.supportPanelContainer)
        assertEquals(colors.primary.copy(alpha = 0.28f), colors.supportPanelOutline)
        assertEquals(colors.primary.copy(alpha = 0.22f), colors.supportPanelDivider)
        assertEquals(colors.primary, colors.supportHeadlineAccent)
        assertEquals(colors.primary, colors.supportPriceValue)
        assertEquals(colors.onSurfaceVariant, colors.supportBackLabel)
        assertEquals(colors.surfaceVariant, colors.supporterChipContainer)
        assertEquals(colors.primary.copy(alpha = 0.45f), colors.supporterChipOutline)
        assertEquals(colors.primary, colors.supporterChipContent)
        assertEquals(colors.onSurface, colors.supportCounterValue)
        assertEquals(colors.onSurfaceVariant, colors.supportCounterLabel)
    }

    private fun assertStyle(
        style: TextStyle,
        fontSize: TextUnit,
        fontWeight: FontWeight,
        lineHeight: TextUnit = TextUnit.Unspecified,
        letterSpacing: TextUnit = TextUnit.Unspecified,
    ) {
        assertEquals(FontFamily.Default, style.fontFamily)
        assertEquals(fontSize, style.fontSize)
        assertEquals(fontWeight, style.fontWeight)
        if (lineHeight != TextUnit.Unspecified) {
            assertEquals(lineHeight, style.lineHeight)
        }
        if (letterSpacing != TextUnit.Unspecified) {
            assertEquals(letterSpacing, style.letterSpacing)
        }
    }

    private fun cornerRadius(shape: Shape): Float {
        val outline = shape.createOutline(Size(100f, 100f), LayoutDirection.Ltr, Density(1f))
        return (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
    }

    private companion object {
        val colorSource =
            listOf(
                File("src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt"),
                File("core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt"),
                File("../core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt"),
            ).firstOrNull(File::isFile)
                ?: error("Color.kt not found")
    }
}
