package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import com.kshavrin.mymoney.core.common.category.categoryIconDominantHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NeonCategoryIconTest {
    @Test
    fun `reference category accents delegate to the canonical dominant hex policy`() {
        listOf(
            "ic_cat_food",
            "ic_cat_entertainment",
            "ic_cat_housing",
            "ic_cat_taxi",
        ).forEach { iconKey ->
            assertEquals(colorFromHex(categoryIconDominantHex(iconKey)), categoryIconAccent(iconKey))
        }
    }

    @Test
    fun `unknown icon accents are stable and match the canonical fallback policy`() {
        val first = categoryIconAccent("ic_cat_future_space")
        val second = categoryIconAccent("ic_cat_future_space")

        assertEquals(first, second)
        assertEquals(colorFromHex(categoryIconDominantHex("ic_cat_future_space")), first)
        assertNotEquals(Color.Transparent, first)
    }

    private fun colorFromHex(hex: String): Color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
}
