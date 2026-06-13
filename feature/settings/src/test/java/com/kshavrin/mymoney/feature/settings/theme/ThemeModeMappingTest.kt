package com.kshavrin.mymoney.feature.settings.theme

import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.core.ui.theme.stored
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the [ThemeMode.fromStored] / [stored] contract that backs persistence to
 * AppSettings.themeMode. The mapping lives in :core:ui Theme.kt; this test runs from
 * :feature:settings because :core:ui has no test dependencies on its classpath, and
 * :feature:settings already depends on :core:ui and carries JUnit. fromStored/stored are pure
 * Kotlin (enum + extension property) with no Android dependency, so they execute on the plain JVM.
 */
class ThemeModeMappingTest {
    @Test
    fun `stored emits the exact wire strings system light dark`() {
        assertEquals("system", ThemeMode.System.stored)
        assertEquals("light", ThemeMode.Light.stored)
        assertEquals("dark", ThemeMode.Dark.stored)
    }

    @Test
    fun `fromStored decodes the three wire strings`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored("system"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStored("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStored("dark"))
    }

    @Test
    fun `stored then fromStored round-trips for every mode`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromStored(mode.stored))
        }
    }

    @Test
    fun `unknown string falls back to System`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored("midnight"))
    }

    @Test
    fun `empty string falls back to System`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored(""))
    }

    @Test
    fun `case-mismatched string falls back to System`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored("Dark"))
    }
}
