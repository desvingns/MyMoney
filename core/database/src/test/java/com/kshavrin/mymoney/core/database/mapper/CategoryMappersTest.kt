package com.kshavrin.mymoney.core.database.mapper

import com.kshavrin.mymoney.core.common.category.categoryIconDominantHex
import com.kshavrin.mymoney.core.common.category.categoryTextColorHex
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class CategoryMappersTest {
    private val createdAt = Instant.parse("2026-06-01T12:00:00Z")

    private fun category(
        iconKey: String,
        colorHex: String = "#AABBCC",
        textColor: String = "#111111",
    ) = Category(
        id = 42L,
        name = "Food",
        kind = CategoryKind.Expense,
        iconKey = iconKey,
        colorHex = colorHex,
        textColor = textColor,
        sortOrder = 3,
        isDefault = false,
        isArchived = false,
        createdAt = createdAt,
    )

    @Test
    fun `toEntity derives colorHex from iconKey ignoring the incoming colorHex`() {
        val iconKey = "ic_cat_food"
        val arbitraryIncomingColorHex = "#FF0000"
        val expectedColorHex = categoryIconDominantHex(iconKey)

        val entity = category(iconKey = iconKey, colorHex = arbitraryIncomingColorHex).toEntity()

        assertEquals(
            "colorHex in entity must be derived from iconKey, not from incoming category.colorHex",
            expectedColorHex,
            entity.colorHex,
        )
        assertNotEquals(
            "entity colorHex must not carry the arbitrary incoming value",
            arbitraryIncomingColorHex,
            entity.colorHex,
        )
    }

    @Test
    fun `toEntity derives textColor from iconKey ignoring the incoming textColor`() {
        val iconKey = "ic_cat_food"
        val arbitraryIncomingTextColor = "#111111"
        val expectedTextColor = categoryTextColorHex(iconKey)

        val entity = category(iconKey = iconKey, textColor = arbitraryIncomingTextColor).toEntity()

        assertEquals(
            "textColor in entity must be derived from iconKey, not from incoming category.textColor",
            expectedTextColor,
            entity.textColor,
        )
    }

    @Test
    fun `toEntity never stores an empty textColor`() {
        val entity = category(iconKey = "ic_cat_other").toEntity()

        assertFalse("textColor must not be empty after toEntity()", entity.textColor.isEmpty())
    }

    @Test
    fun `toEntity colorHex and textColor are consistent for a known icon key`() {
        val iconKey = "ic_cat_clothing"
        val entity = category(iconKey = iconKey).toEntity()

        assertEquals(categoryIconDominantHex(iconKey), entity.colorHex)
        assertEquals(categoryTextColorHex(iconKey), entity.textColor)
    }

    @Test
    fun `toEntity colorHex and textColor are consistent for an unknown icon key using fallback`() {
        val iconKey = "ic_nonexistent_icon_xyz"
        val entity = category(iconKey = iconKey).toEntity()

        assertEquals(categoryIconDominantHex(iconKey), entity.colorHex)
        assertEquals(categoryTextColorHex(iconKey), entity.textColor)
        assertFalse("fallback colorHex must not be empty", entity.colorHex.isEmpty())
        assertFalse("fallback textColor must not be empty", entity.textColor.isEmpty())
    }

    @Test
    fun `toEntity preserves all non-color fields from the source category`() {
        val iconKey = "ic_cat_entertainment"
        val source =
            Category(
                id = 99L,
                name = "Entertainment",
                kind = CategoryKind.Expense,
                iconKey = iconKey,
                colorHex = "#AABBCC",
                textColor = "#111111",
                sortOrder = 7,
                isDefault = true,
                isArchived = true,
                createdAt = createdAt,
            )

        val entity = source.toEntity()

        assertEquals(99L, entity.id)
        assertEquals("Entertainment", entity.name)
        assertEquals("expense", entity.kind)
        assertEquals(iconKey, entity.iconKey)
        assertEquals(7, entity.sortOrder)
        assertEquals(true, entity.isDefault)
        assertEquals(true, entity.isArchived)
        assertEquals(createdAt.toEpochMilli(), entity.createdAt)
    }

    @Test
    fun `toEntity two categories with different icon keys produce different colorHex values`() {
        val entity1 = category(iconKey = "ic_cat_food").toEntity()
        val entity2 = category(iconKey = "ic_cat_entertainment").toEntity()

        assertNotEquals(
            "Different icon keys must produce different colorHex values",
            entity1.colorHex,
            entity2.colorHex,
        )
    }

    @Test
    fun `toDomain round trip preserves textColor from entity`() {
        val iconKey = "ic_cat_taxi"
        val entity = category(iconKey = iconKey).toEntity()
        val domain = entity.toDomain()

        assertEquals(
            "toDomain must preserve textColor stored in the entity",
            entity.textColor,
            domain.textColor,
        )
        assertEquals(
            "toDomain must preserve colorHex stored in the entity",
            entity.colorHex,
            domain.colorHex,
        )
    }
}
