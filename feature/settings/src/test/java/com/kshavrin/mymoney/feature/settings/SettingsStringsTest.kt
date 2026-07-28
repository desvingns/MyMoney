package com.kshavrin.mymoney.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SettingsStringsTest {
    @Test
    fun `english and russian settings string keys stay in parity`() {
        assertEquals(parseNames(enFile), parseNames(ruFile))
    }

    @Test
    fun `recents subtitle warns in both languages that screenshots are blocked`() {
        val key = "settings_hide_content_in_recents_subtitle"
        val english = parseStrings(enFile).getValue(key)
        val russian = parseStrings(ruFile).getValue(key)

        assertTrue(english.contains("screenshot", ignoreCase = true))
        assertTrue(russian.contains("сним", ignoreCase = true))
    }

    private companion object {
        val enFile = resolve("values/strings.xml")
        val ruFile = resolve("values-ru/strings.xml")

        fun resolve(relative: String): File =
            listOf(
                File("src/main/res/$relative"),
                File("feature/settings/src/main/res/$relative"),
                File("../feature/settings/src/main/res/$relative"),
            ).firstOrNull(File::isFile) ?: File("src/main/res/$relative")

        fun parseStrings(file: File): Map<String, String> {
            require(file.isFile) { "strings.xml not found: ${file.path}" }
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = document.getElementsByTagName("string")
            return buildMap {
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as Element
                    put(element.getAttribute("name"), element.textContent)
                }
            }
        }

        fun parseNames(file: File): Set<String> = parseStrings(file).keys
    }
}
