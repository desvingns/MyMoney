package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGridColorParsingTest {

    @Test
    fun `parses six digit hex colors with an implicit opaque alpha channel`() {
        val parsed = parseHexColor("#7AC794", fallback = Color.Black)

        assertEquals(Color(0xFF7AC794), parsed)
    }

    @Test
    fun `preserves an explicit alpha channel when one is provided`() {
        val parsed = parseHexColor("807AC794", fallback = Color.Black)

        assertEquals(Color(0x807AC794), parsed)
    }

    @Test
    fun `returns the fallback color when the hex string is invalid`() {
        val fallback = Color(0xFF123456)

        assertEquals(fallback, parseHexColor("#oops", fallback))
    }
}
