package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DonutAnimationKeyTest {

    private fun slice(id: Long, fraction: Float, label: String = "L"): CategorySlice =
        CategorySlice(categoryId = id, color = Color.Red, fraction = fraction, label = label)

    @Test
    fun `same ids and fractions in new list instances yield equal keys`() {
        val a = listOf(slice(1L, 0.5f), slice(2L, 0.5f))
        val b = listOf(slice(1L, 0.5f), slice(2L, 0.5f))

        assertEquals(donutAnimationKey(a), donutAnimationKey(b))
    }

    @Test
    fun `key ignores fields other than id and fraction`() {
        val a = listOf(slice(1L, 0.5f, label = "Food"))
        val b = listOf(
            CategorySlice(
                categoryId = 1L,
                color = Color.Blue,
                fraction = 0.5f,
                label = "Other",
                iconKey = "ic",
                hasBudgetAlert = true,
            ),
        )

        assertEquals(donutAnimationKey(a), donutAnimationKey(b))
    }

    @Test
    fun `different fraction yields different key`() {
        val a = listOf(slice(1L, 0.5f))
        val b = listOf(slice(1L, 0.6f))

        assertNotEquals(donutAnimationKey(a), donutAnimationKey(b))
    }

    @Test
    fun `different category id yields different key`() {
        val a = listOf(slice(1L, 0.5f))
        val b = listOf(slice(2L, 0.5f))

        assertNotEquals(donutAnimationKey(a), donutAnimationKey(b))
    }

    @Test
    fun `slice order is significant`() {
        val a = listOf(slice(1L, 0.4f), slice(2L, 0.6f))
        val b = listOf(slice(2L, 0.6f), slice(1L, 0.4f))

        assertNotEquals(donutAnimationKey(a), donutAnimationKey(b))
    }

    @Test
    fun `empty slices yield equal keys`() {
        assertEquals(donutAnimationKey(emptyList()), donutAnimationKey(emptyList()))
    }

    @Test
    fun `adding a new category to the list produces a different key`() {
        val before = listOf(slice(1L, 0.5f), slice(2L, 0.5f))
        val after = listOf(slice(1L, 0.4f), slice(2L, 0.4f), slice(3L, 0.2f))

        assertNotEquals(donutAnimationKey(before), donutAnimationKey(after))
    }

    @Test
    fun `removing a category from the list produces a different key`() {
        val full = listOf(slice(1L, 0.5f), slice(2L, 0.3f), slice(3L, 0.2f))
        val reduced = listOf(slice(1L, 0.7f), slice(2L, 0.3f))

        assertNotEquals(donutAnimationKey(full), donutAnimationKey(reduced))
    }

    @Test
    fun `balance recompute with same ids and fractions does not change the key`() {
        val firstCompute = listOf(slice(10L, 0.60f), slice(20L, 0.40f))
        val secondCompute = listOf(slice(10L, 0.60f), slice(20L, 0.40f))

        assertEquals(
            donutAnimationKey(firstCompute),
            donutAnimationKey(secondCompute),
        )
    }
}
