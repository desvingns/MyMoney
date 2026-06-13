package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract tests for [goalIcon].
 *
 * `goalIcon` is a plain (non-@Composable) function returning [ImageVector]; all resolved vectors
 * are Material Outlined singletons — pure data structures that load on the plain JVM test
 * classpath without Robolectric.
 *
 * :core:designsystem MUST NOT depend on :feature:dictionaries, so the authoritative
 * GOAL_ICON_KEYS list is not imported here. Instead, [allKeys] is declared locally and kept in
 * sync with GoalIcons.kt — 13 entries matching the production when-expression exactly.
 *
 * Fallback contract (pinned exactly — DO NOT loosen):
 *   - fallback (unknown / null / empty key) is `Icons.Outlined.Flag`;
 *   - `ic_goal_other` INTENTIONALLY maps to that SAME instance — it IS the generic goal glyph
 *     and doubles as the fallback (analogous to `ic_account_wallet` in AccountIconsTest and
 *     `ic_cat_other` in CategoryIconsTest);
 *   - the 12 themed keys each return something that is NOT the fallback;
 *   - the 12 themed keys are pairwise distinct from each other.
 *
 * Material icon objects are cached singletons, so reference identity (===) is the valid
 * comparison; `assertSame` / `assertNotSame` use reference identity.
 */
class GoalIconsTest {
    private val fallback: ImageVector = Icons.Outlined.Flag

    /** All 13 keys from GoalIcons.kt (local copy — :core:designsystem may not import :feature). */
    private val allKeys: List<String> =
        listOf(
            "ic_goal_home",
            "ic_goal_car",
            "ic_goal_travel",
            "ic_goal_education",
            "ic_goal_emergency",
            "ic_goal_wedding",
            "ic_goal_gadget",
            "ic_goal_gift",
            "ic_goal_health",
            "ic_goal_retirement",
            "ic_goal_renovation",
            "ic_goal_family",
            "ic_goal_other",
        )

    /** The 12 keys that must each resolve to a distinct, non-fallback vector. */
    private val themedKeys: List<String> = allKeys.filterNot { it == "ic_goal_other" }

    // ---- exhaustiveness ----

    @Test
    fun `covers exactly the thirteen documented goal keys`() {
        assertEquals(13, allKeys.size)
        assertEquals(12, themedKeys.size)
    }

    // ---- per-key mapping (mirrors the production when-expression) ----

    @Test
    fun `ic_goal_home maps to Outlined Home`() {
        assertSame(Icons.Outlined.Home, goalIcon("ic_goal_home"))
    }

    @Test
    fun `ic_goal_car maps to Outlined DirectionsCar`() {
        assertSame(Icons.Outlined.DirectionsCar, goalIcon("ic_goal_car"))
    }

    @Test
    fun `ic_goal_travel maps to Outlined Flight`() {
        assertSame(Icons.Outlined.Flight, goalIcon("ic_goal_travel"))
    }

    @Test
    fun `ic_goal_education maps to Outlined School`() {
        assertSame(Icons.Outlined.School, goalIcon("ic_goal_education"))
    }

    @Test
    fun `ic_goal_emergency maps to Outlined HealthAndSafety`() {
        assertSame(Icons.Outlined.HealthAndSafety, goalIcon("ic_goal_emergency"))
    }

    @Test
    fun `ic_goal_wedding maps to Outlined Diamond`() {
        assertSame(Icons.Outlined.Diamond, goalIcon("ic_goal_wedding"))
    }

    @Test
    fun `ic_goal_gadget maps to Outlined Devices`() {
        assertSame(Icons.Outlined.Devices, goalIcon("ic_goal_gadget"))
    }

    @Test
    fun `ic_goal_gift maps to Outlined Redeem`() {
        assertSame(Icons.Outlined.Redeem, goalIcon("ic_goal_gift"))
    }

    @Test
    fun `ic_goal_health maps to Outlined MedicalServices`() {
        assertSame(Icons.Outlined.MedicalServices, goalIcon("ic_goal_health"))
    }

    @Test
    fun `ic_goal_retirement maps to Outlined BeachAccess`() {
        assertSame(Icons.Outlined.BeachAccess, goalIcon("ic_goal_retirement"))
    }

    @Test
    fun `ic_goal_renovation maps to Outlined Construction`() {
        assertSame(Icons.Outlined.Construction, goalIcon("ic_goal_renovation"))
    }

    @Test
    fun `ic_goal_family maps to Outlined FamilyRestroom`() {
        assertSame(Icons.Outlined.FamilyRestroom, goalIcon("ic_goal_family"))
    }

    // ---- fallback contract ----

    @Test
    fun `ic_goal_other maps to the same instance as the fallback`() {
        assertSame(fallback, goalIcon("ic_goal_other"))
    }

    @Test
    fun `unknown key returns the fallback instance`() {
        assertSame(fallback, goalIcon("ic_goal_nope"))
    }

    @Test
    fun `empty key returns the fallback instance`() {
        assertSame(fallback, goalIcon(""))
    }

    @Test
    fun `garbage key returns the fallback instance`() {
        assertSame(fallback, goalIcon("random"))
    }

    @Test
    fun `null key returns the fallback instance`() {
        assertSame(fallback, goalIcon(null))
    }

    @Test
    fun `each themed key returns a vector that is not the fallback`() {
        for (key in themedKeys) {
            assertNotSame(
                "key '$key' must NOT resolve to the fallback (Icons.Outlined.Flag)",
                fallback,
                goalIcon(key),
            )
        }
    }

    @Test
    fun `the twelve themed keys are pairwise distinct vectors`() {
        val vectors: List<ImageVector> = themedKeys.map { goalIcon(it) }
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                assertTrue(
                    "'${themedKeys[i]}' and '${themedKeys[j]}' must resolve to different vectors",
                    vectors[i] !== vectors[j],
                )
            }
        }
    }

    // ---- stability: repeated calls are referentially stable (Material singletons cached) ----

    @Test
    fun `repeated calls for the same key return the same instance`() {
        for (key in allKeys) {
            assertSame(
                "key '$key' must return a stable instance across calls",
                goalIcon(key),
                goalIcon(key),
            )
        }
    }
}
