package com.kshavrin.mymoney.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DashboardTourStringsTest {

    @Test
    fun `english and russian tour string keys stay in parity`() {
        val english = parseStrings(enFile).filterKeys { it.startsWith("dashboard_tour_") }
        val russian = parseStrings(ruFile).filterKeys { it.startsWith("dashboard_tour_") }

        assertEquals(
            "dashboard_tour_* key parity broken — " +
                "only in EN: ${english.keys - russian.keys}, only in RU: ${russian.keys - english.keys}",
            english.keys,
            russian.keys,
        )
    }

    @Test
    fun `all expected tour keys exist and are non-blank in both locales`() {
        val english = parseStrings(enFile)
        val russian = parseStrings(ruFile)
        val expectedKeys =
            setOf(
                "dashboard_tour_skip_all",
                "dashboard_tour_next",
                "dashboard_tour_done",
                "dashboard_tour_progress",
                "dashboard_tour_overlay_description",
                "dashboard_tour_actions_title",
                "dashboard_tour_actions_body",
                "dashboard_tour_left_title",
                "dashboard_tour_left_body",
                "dashboard_tour_categories_title",
                "dashboard_tour_categories_body",
                "dashboard_tour_support_title",
                "dashboard_tour_support_body",
            )

        assertTrue(
            "Missing EN tour keys: ${expectedKeys - english.keys}",
            expectedKeys.all(english::containsKey),
        )
        assertTrue(
            "Missing RU tour keys: ${expectedKeys - russian.keys}",
            expectedKeys.all(russian::containsKey),
        )
        assertTrue(
            expectedKeys.all { english.getValue(it).isNotBlank() && russian.getValue(it).isNotBlank() },
        )
    }

    private companion object {
        val enFile = resolve("values/strings.xml")
        val ruFile = resolve("values-ru/strings.xml")

        fun resolve(relative: String): File =
            listOf(
                File("src/main/res/$relative"),
                File("feature/dashboard/src/main/res/$relative"),
                File("../feature/dashboard/src/main/res/$relative"),
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
