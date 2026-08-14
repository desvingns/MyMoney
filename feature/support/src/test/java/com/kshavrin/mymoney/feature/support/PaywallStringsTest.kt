package com.kshavrin.mymoney.feature.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PaywallStringsTest {
    @Test
    fun `english and russian paywall string keys stay in parity`() {
        val english = parseStrings(enFile).filterKeys { it.startsWith("paywall_") }
        val russian = parseStrings(ruFile).filterKeys { it.startsWith("paywall_") }

        assertEquals(english.keys, russian.keys)
    }

    @Test
    fun `free forever and owner pays rules remain explicit in both locales`() {
        val english = parseStrings(enFile)
        val russian = parseStrings(ruFile)

        assertTrue(english.getValue("paywall_free_forever_description").contains("free", ignoreCase = true))
        assertTrue(russian.getValue("paywall_free_forever_description").contains("бесплат", ignoreCase = true))
        assertTrue(english.getValue("paywall_workspace_payer_description").contains("owner", ignoreCase = true))
        assertTrue(russian.getValue("paywall_workspace_payer_description").contains("владел", ignoreCase = true))
    }

    private companion object {
        val enFile = resolve("values/strings.xml")
        val ruFile = resolve("values-ru/strings.xml")

        fun resolve(relative: String): File =
            listOf(
                File("src/main/res/$relative"),
                File("feature/support/src/main/res/$relative"),
                File("../feature/support/src/main/res/$relative"),
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
    }
}
