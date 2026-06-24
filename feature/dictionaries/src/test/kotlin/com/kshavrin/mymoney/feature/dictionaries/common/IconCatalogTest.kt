package com.kshavrin.mymoney.feature.dictionaries.common

import com.kshavrin.mymoney.core.designsystem.icon.categoryNeonIconResOrNull
import com.kshavrin.mymoney.core.designsystem.picker.CATEGORY_REFERENCE_ICON_KEYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IconCatalogTest {
    private val referenceIconKeys = CATEGORY_REFERENCE_ICON_KEYS

    @Test
    fun `expense icon keys match the documented expense catalog order`() {
        assertEquals(82, referenceIconKeys.size)
        assertEquals(referenceIconKeys, EXPENSE_ICON_KEYS)
    }

    @Test
    fun `every expense icon key resolves to a neon category bitmap asset`() {
        for (iconKey in EXPENSE_ICON_KEYS) {
            assertNotNull(
                "expense key '$iconKey' must resolve to a registered neon category asset",
                categoryNeonIconResOrNull(iconKey),
            )
        }
    }

    @Test
    fun `income icon keys match the documented income catalog order`() {
        assertEquals(referenceIconKeys, INCOME_ICON_KEYS)
    }

    @Test
    fun `every income icon key resolves to a neon category bitmap asset`() {
        for (iconKey in INCOME_ICON_KEYS) {
            assertNotNull(
                "income key '$iconKey' must resolve to a registered neon category asset",
                categoryNeonIconResOrNull(iconKey),
            )
        }
    }

    @Test
    fun `category picker keys resolve to distinct bitmap assets`() {
        val pickerAssets =
            (EXPENSE_ICON_KEYS + INCOME_ICON_KEYS)
                .distinct()
                .map { iconKey -> categoryNeonIconResOrNull(iconKey) }

        assertEquals(referenceIconKeys.size, pickerAssets.toSet().size)
    }
}
