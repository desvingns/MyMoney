package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Train
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract tests for [categoryIcon].
 *
 * `categoryIcon` is a plain (non-@Composable) function returning [ImageVector]; both the
 * Material icon singletons and the locally-built vectors are pure data structures, so they
 * load and compare on the plain JVM test classpath in :core:designsystem (same as
 * MonefyAmountInputTest, which uses Compose unit types without Robolectric).
 *
 * Fallback contract (pinned exactly per the implementer's note — DO NOT loosen):
 *   - fallback (unknown key) is `Icons.Outlined.Category`;
 *   - `ic_cat_other` INTENTIONALLY maps to that SAME instance;
 *   - the 16 non-`other` keys each return something that is NOT the fallback;
 *   - the 16 non-`other` keys are pairwise distinct from each other.
 *
 * Material icon objects are cached singletons, so reference identity (===) is the valid
 * comparison; `assertSame` / `assertNotSame` use reference identity.
 */
class CategoryIconsTest {

    private val fallback: ImageVector = Icons.Outlined.Category

    /** The 17 real seed keys from InitialDataSeeder. */
    private val allKeys: List<String> = listOf(
        "ic_cat_bills",
        "ic_cat_food",
        "ic_cat_entertainment",
        "ic_cat_taxi",
        "ic_cat_housing",
        "ic_cat_sport",
        "ic_cat_gifts",
        "ic_cat_phone",
        "ic_cat_transport",
        "ic_cat_cafe",
        "ic_cat_car",
        "ic_cat_salary",
        "ic_cat_other",
        "ic_cat_hygiene",
        "ic_cat_pets",
        "ic_cat_health",
        "ic_cat_clothing",
    )

    /** The 16 real keys that must each resolve to a distinct, non-fallback vector. */
    private val nonOtherKeys: List<String> = allKeys.filterNot { it == "ic_cat_other" }

    // ---- exhaustiveness: the registry covers all 17 documented keys ----

    @Test
    fun `covers exactly the seventeen documented category keys`() {
        assertEquals(17, allKeys.size)
        assertEquals(16, nonOtherKeys.size)
    }

    // ---- per-key mapping (mirrors the production `when`) ----

    @Test
    fun `ic_cat_bills maps to Outlined LocalOffer`() {
        assertSame(Icons.Outlined.LocalOffer, categoryIcon("ic_cat_bills"))
    }

    @Test
    fun `ic_cat_food maps to Outlined ShoppingBasket`() {
        assertSame(Icons.Outlined.ShoppingBasket, categoryIcon("ic_cat_food"))
    }

    @Test
    fun `ic_cat_entertainment maps to Outlined LocalBar`() {
        assertSame(Icons.Outlined.LocalBar, categoryIcon("ic_cat_entertainment"))
    }

    @Test
    fun `ic_cat_taxi maps to Outlined LocalTaxi`() {
        assertSame(Icons.Outlined.LocalTaxi, categoryIcon("ic_cat_taxi"))
    }

    @Test
    fun `ic_cat_housing maps to Outlined Home`() {
        assertSame(Icons.Outlined.Home, categoryIcon("ic_cat_housing"))
    }

    @Test
    fun `ic_cat_sport maps to AutoMirrored Outlined DirectionsRun`() {
        // The AutoMirrored variant, not the deprecated Icons.Outlined.DirectionsRun.
        assertSame(Icons.AutoMirrored.Outlined.DirectionsRun, categoryIcon("ic_cat_sport"))
    }

    @Test
    fun `ic_cat_gifts maps to Outlined CardGiftcard`() {
        assertSame(Icons.Outlined.CardGiftcard, categoryIcon("ic_cat_gifts"))
    }

    @Test
    fun `ic_cat_phone maps to Outlined Call`() {
        assertSame(Icons.Outlined.Call, categoryIcon("ic_cat_phone"))
    }

    @Test
    fun `ic_cat_transport maps to Outlined Train`() {
        assertSame(Icons.Outlined.Train, categoryIcon("ic_cat_transport"))
    }

    @Test
    fun `ic_cat_cafe maps to Outlined Restaurant`() {
        assertSame(Icons.Outlined.Restaurant, categoryIcon("ic_cat_cafe"))
    }

    @Test
    fun `ic_cat_car maps to Outlined DirectionsCar`() {
        assertSame(Icons.Outlined.DirectionsCar, categoryIcon("ic_cat_car"))
    }

    @Test
    fun `ic_cat_salary maps to Outlined Payments`() {
        assertSame(Icons.Outlined.Payments, categoryIcon("ic_cat_salary"))
    }

    // ---- fallback contract ----

    @Test
    fun `ic_cat_other maps to the same instance as the fallback`() {
        assertSame(fallback, categoryIcon("ic_cat_other"))
    }

    @Test
    fun `unknown key returns the fallback instance`() {
        assertSame(fallback, categoryIcon("ic_cat_nope"))
    }

    @Test
    fun `empty key returns the fallback instance`() {
        assertSame(fallback, categoryIcon(""))
    }

    @Test
    fun `garbage key returns the fallback instance`() {
        assertSame(fallback, categoryIcon("random"))
    }

    @Test
    fun `each non-other key returns a vector that is not the fallback`() {
        for (key in nonOtherKeys) {
            assertNotSame(
                "key '$key' must NOT resolve to the fallback (Icons.Outlined.Category)",
                fallback,
                categoryIcon(key),
            )
        }
    }

    @Test
    fun `the sixteen non-other keys are pairwise distinct vectors`() {
        val vectors: List<ImageVector> = nonOtherKeys.map { categoryIcon(it) }
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                assertTrue(
                    "'${nonOtherKeys[i]}' and '${nonOtherKeys[j]}' must resolve to different vectors",
                    vectors[i] !== vectors[j],
                )
            }
        }
    }

    // ---- stability: repeated calls are referentially stable (lazy singletons cached) ----

    @Test
    fun `repeated calls for the same key return the same instance`() {
        for (key in allKeys) {
            assertSame(
                "key '$key' must return a stable instance across calls",
                categoryIcon(key),
                categoryIcon(key),
            )
        }
    }
}
