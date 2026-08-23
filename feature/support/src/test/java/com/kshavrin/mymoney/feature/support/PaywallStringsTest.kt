package com.kshavrin.mymoney.feature.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `english and russian support string keys stay in parity`() {
        val english = parseStrings(enFile).filterKeys { it.startsWith("support_") }
        val russian = parseStrings(ruFile).filterKeys { it.startsWith("support_") }

        assertEquals(
            "support_* key parity broken — " +
                "only in EN: ${english.keys - russian.keys}, only in RU: ${russian.keys - english.keys}",
            english.keys,
            russian.keys,
        )
    }

    @Test
    fun `new support screen strings exist in both locales`() {
        val english = parseStrings(enFile)
        val russian = parseStrings(ruFile)
        val expectedKeys =
            setOf(
                "support_back_label",
                "support_headline_lead",
                "support_headline_accent",
                "support_coffee_small_name",
                "support_coffee_large_name",
                "support_coffee_generic_name",
                "support_purchase_action",
                "support_gratitude_title_supporter",
                "support_gratitude_body_supporter",
                "support_gratitude_title_prospect",
                "support_gratitude_body_prospect",
                "support_counter_ads_label",
                "support_counter_coffee_small_label",
                "support_counter_coffee_large_label",
                "support_image_hero_description",
                "support_image_coffee_small_description",
                "support_image_coffee_large_description",
                "support_image_ads_description",
                "support_image_plus_description",
                "support_image_avatar_description",
            )

        assertTrue(
            "Missing new EN support keys: ${expectedKeys - english.keys}",
            expectedKeys.all(english::containsKey),
        )
        assertTrue(
            "Missing new RU support keys: ${expectedKeys - russian.keys}",
            expectedKeys.all(russian::containsKey),
        )
        assertTrue(
            expectedKeys.all { english.getValue(it).isNotBlank() && russian.getValue(it).isNotBlank() },
        )
    }

    @Test
    fun `english and russian rewarded ad string keys stay in parity`() {
        val english = parseStrings(enFile).filterKeys { it.startsWith("support_ads_") }
        val russian = parseStrings(ruFile).filterKeys { it.startsWith("support_ads_") }

        assertEquals(
            "support_ads_* key parity broken — " +
                "only in EN: ${english.keys - russian.keys}, only in RU: ${russian.keys - english.keys}",
            english.keys,
            russian.keys,
        )
    }

    @Test
    fun `awaiting confirmation string never mentions credited or charged in either locale`() {
        val english = parseStrings(enFile)
        val russian = parseStrings(ruFile)

        assertFalse(
            "EN awaiting_confirmation must not say 'credited'",
            english.getValue("support_ads_awaiting_confirmation").contains("credited", ignoreCase = true),
        )
        assertFalse(
            "RU awaiting_confirmation must not say 'начислено'",
            russian.getValue("support_ads_awaiting_confirmation").contains("начислено", ignoreCase = true),
        )
    }

    @Test
    fun `no fill and region unavailable strings are phrased as absence not as error in both locales`() {
        val english = parseStrings(enFile)
        val russian = parseStrings(ruFile)

        assertFalse(
            english.getValue("support_ads_no_fill").contains("error", ignoreCase = true),
        )
        assertFalse(
            english.getValue("support_ads_region_unavailable").contains("error", ignoreCase = true),
        )
        assertFalse(
            russian.getValue("support_ads_no_fill").contains("ошибка", ignoreCase = true),
        )
        assertFalse(
            russian.getValue("support_ads_region_unavailable").contains("ошибка", ignoreCase = true),
        )
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
