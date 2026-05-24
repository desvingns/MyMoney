package com.kshavrin.mymoney.feature.settings.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageMappingTest {

    @Test
    fun `stored emits the exact wire strings system en ru`() {
        assertEquals("system", AppLanguage.System.stored)
        assertEquals("en", AppLanguage.English.stored)
        assertEquals("ru", AppLanguage.Russian.stored)
    }

    @Test
    fun `localeTag maps english and russian to bcp47 tags`() {
        assertEquals("en", AppLanguage.English.localeTag)
        assertEquals("ru", AppLanguage.Russian.localeTag)
    }

    @Test
    fun `localeTag for System is the cleared override`() {
        assertNull(AppLanguage.System.localeTag)
    }

    @Test
    fun `fromStored decodes the three wire strings`() {
        assertEquals(AppLanguage.System, AppLanguage.fromStored("system"))
        assertEquals(AppLanguage.English, AppLanguage.fromStored("en"))
        assertEquals(AppLanguage.Russian, AppLanguage.fromStored("ru"))
    }

    @Test
    fun `stored then fromStored round-trips for every language`() {
        for (language in AppLanguage.entries) {
            assertEquals(language, AppLanguage.fromStored(language.stored))
        }
    }

    @Test
    fun `unknown string falls back to System`() {
        assertEquals(AppLanguage.System, AppLanguage.fromStored("fr"))
    }

    @Test
    fun `empty string falls back to System`() {
        assertEquals(AppLanguage.System, AppLanguage.fromStored(""))
    }

    @Test
    fun `case-mismatched string falls back to System`() {
        assertEquals(AppLanguage.System, AppLanguage.fromStored("EN"))
    }
}
