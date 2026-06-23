package com.kshavrin.mymoney.core.common.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconColorTest {
    @Test
    fun `returns curated dominant hex for representative legacy icon keys`() {
        val expectedColors =
            mapOf(
                "ic_cat_clothing" to "#A9FFE2",
                "ic_cat_food" to "#B7FF7A",
                "ic_cat_entertainment" to "#FF8FE1",
                "ic_cat_taxi" to "#FFE27A",
                "ic_cat_housing" to "#7FE2FF",
                "ic_cat_dentist" to "#EAF6FF",
            )

        expectedColors.forEach { (iconKey, expectedHex) ->
            val actual = categoryIconDominantHex(iconKey)

            assertEquals(expectedHex, actual)
            assertTrue(strictHexRegex.matches(actual))
        }
    }

    @Test
    fun `returns curated dominant hex for representative newer icon keys`() {
        val expectedColors =
            mapOf(
                "ic_cat_education" to "#7FE2FF",
                "ic_cat_delivery" to "#FF8A80",
                "ic_cat_streaming" to "#FF8FE1",
                "ic_cat_hygiene" to "#C5A3FF",
                "ic_cat_fuel" to "#FF8A80",
                "ic_cat_technology" to "#C5A3FF",
            )

        expectedColors.forEach { (iconKey, expectedHex) ->
            val actual = categoryIconDominantHex(iconKey)

            assertEquals(expectedHex, actual)
            assertTrue(strictHexRegex.matches(actual))
        }
    }

    @Test
    fun `unknown icon key uses deterministic strict hex fallback`() {
        val first = categoryIconDominantHex("zzz")
        val second = categoryIconDominantHex("zzz")

        assertEquals("#FF8A80", first)
        assertEquals(first, second)
        assertTrue(strictHexRegex.matches(first))
    }

    @Test
    fun `readable text hex lightens dark colors to the pinned readable output`() {
        val adjusted = readableTextHex("#102018")

        assertEquals("#9BA29E", adjusted)
        assertTrue(strictHexRegex.matches(adjusted))
    }

    @Test
    fun `readable text hex keeps already bright colors unchanged`() {
        assertEquals("#FFD54A", readableTextHex("#FFD54A"))
    }

    @Test
    fun `category text color hex composes dominant and readable text policies`() {
        val iconKey = "ic_cat_taxi"

        assertEquals("#FFE27A", categoryTextColorHex(iconKey))
        assertEquals(
            readableTextHex(categoryIconDominantHex(iconKey)),
            categoryTextColorHex(iconKey),
        )
    }

    private companion object {
        val strictHexRegex = Regex("^#[0-9A-F]{6}$")
    }
}
