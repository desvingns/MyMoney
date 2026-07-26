package com.kshavrin.mymoney.core.designsystem.amountinput

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-level tests for `computeDisplayFontSize`, the auto-shrink helper
 * used inside `AmountInput`.
 *
 * Mapping pinned by SPEC and verified against AmountInput.kt:
 *
 *   length ≤  6 -> 36.sp
 *   length 7..8 -> 32.sp
 *   length 9..10 -> 28.sp
 *   length ≥ 11 -> 24.sp
 *
 * The function is `internal` and lives in this package, so this test
 * can call it directly without any Compose runtime or Robolectric
 * wiring (those are not on the test classpath in :core:designsystem
 * until PHASE_15 — see build.gradle.kts).
 *
 * `TextUnit` comparison is done via `.value` (a `Float` for sp/em) plus
 * a guard on `.type == TextUnitType.Sp` so a future change to em or
 * unspecified would fail the test loudly.
 */
class AmountInputTest {
    private fun assertSp(
        expectedSp: Float,
        actual: TextUnit,
    ) {
        assertTrue(
            "expected a TextUnit of type Sp but was ${actual.type}",
            actual.type == TextUnitType.Sp,
        )
        assertEquals(expectedSp, actual.value, 0.0f)
    }

    // ---- ≤6 chars -> 36.sp ----

    @Test
    fun `length 0 returns 36 sp`() {
        assertSp(36f, computeDisplayFontSize(0))
    }

    @Test
    fun `length 6 returns 36 sp (upper bound of the largest bucket)`() {
        assertSp(36f, computeDisplayFontSize(6))
    }

    // ---- 7..8 chars -> 32.sp ----

    @Test
    fun `length 7 returns 32 sp (lower bound of the 32 sp bucket)`() {
        assertSp(32f, computeDisplayFontSize(7))
    }

    @Test
    fun `length 8 returns 32 sp (upper bound of the 32 sp bucket)`() {
        assertSp(32f, computeDisplayFontSize(8))
    }

    // ---- 9..10 chars -> 28.sp ----

    @Test
    fun `length 9 returns 28 sp (lower bound of the 28 sp bucket)`() {
        assertSp(28f, computeDisplayFontSize(9))
    }

    @Test
    fun `length 10 returns 28 sp (upper bound of the 28 sp bucket)`() {
        assertSp(28f, computeDisplayFontSize(10))
    }

    // ---- ≥11 chars -> 24.sp (cap) ----

    @Test
    fun `length 11 returns 24 sp (lower bound of the smallest bucket)`() {
        assertSp(24f, computeDisplayFontSize(11))
    }

    @Test
    fun `length 100 still returns 24 sp (cap holds for very long strings)`() {
        assertSp(24f, computeDisplayFontSize(100))
    }
}
