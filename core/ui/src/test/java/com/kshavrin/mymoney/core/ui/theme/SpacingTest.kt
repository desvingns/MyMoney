package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingTest {
    @Test
    fun `import wizard icon picker tokens keep the enlarged tile contract`() {
        assertEquals(48.dp, Spacing.wizardIconPickerItemSize)
        assertEquals(64.dp, Spacing.wizardIconPickerTileSize)
        assertEquals(40.dp, Spacing.wizardIconPickerIconSize)
        assertTrue(Spacing.wizardIconPickerTileSize > Spacing.wizardIconPickerItemSize)
        assertTrue(Spacing.wizardIconPickerIconSize > 24.dp)
    }

    @Test
    fun `transaction choose category token preserves the compact button height`() {
        assertEquals(68.dp, Spacing.transactionFormChooseCategoryHeight)
    }
}
