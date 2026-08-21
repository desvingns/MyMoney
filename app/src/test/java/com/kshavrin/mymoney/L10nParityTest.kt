package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JVM (no Robolectric) cross-module EN↔RU string-key parity sweep.
 *
 * Reads res/values/strings.xml and res/values-ru/strings.xml for every Android module in the
 * repo and asserts:
 *   1. Every EN key has a matching RU key  — mirrors lint.xml MissingTranslation gate.
 *   2. No RU key exists without an EN source — mirrors lint.xml ExtraTranslation gate.
 *   3. Every module with a plurals file declares the same plural names in EN and RU.
 *   4. Every RU <plurals> block carries the full CLDR set required for Russian:
 *      {one, few, many, other}.
 *
 * This catches drift in `./gradlew testDebugUnitTest` without waiting for a full AGP lint pass.
 * The lint gate in lint.xml is the build stopper; this test is the fast-feedback loop.
 *
 * Strings marked translatable="false" in EN are excluded from the parity check — they have no
 * expected RU counterpart.
 *
 * Path resolution: each helper tries the repo root first ("module/src/main/res/…") then
 * falls back to "../module/src/main/res/…" which resolves correctly when Gradle sets
 * the working directory to the :app module folder (the AGP default for unit tests).
 */
class L10nParityTest {
    @Test
    fun `every translatable EN string key has a matching RU key in every module`() {
        MODULES_WITH_STRINGS.forEach { module ->
            val enKeys = parseTranslatableStringKeys(resolveResFile(module, "values/strings.xml"))
            val ruKeys = parseTranslatableStringKeys(resolveResFile(module, "values-ru/strings.xml"))

            val missingInRu = enKeys - ruKeys
            assertTrue(
                "[$module] MissingTranslation — keys in EN but absent in RU: $missingInRu",
                missingInRu.isEmpty(),
            )
        }
    }

    @Test
    fun `no RU string key exists without a matching EN source key in every module`() {
        MODULES_WITH_STRINGS.forEach { module ->
            val enKeys = parseTranslatableStringKeys(resolveResFile(module, "values/strings.xml"))
            val ruKeys = parseTranslatableStringKeys(resolveResFile(module, "values-ru/strings.xml"))

            val extraInRu = ruKeys - enKeys
            assertTrue(
                "[$module] ExtraTranslation — keys in RU without EN source: $extraInRu",
                extraInRu.isEmpty(),
            )
        }
    }

    @Test
    fun `chart settings rework strings keep the exact EN and RU contract`() {
        val enValues = parseStringValues(resolveResFile("feature/dashboard", "values/strings.xml"))
        val ruValues = parseStringValues(resolveResFile("feature/dashboard", "values-ru/strings.xml"))
        val expected =
            mapOf(
                "chart_settings_color_solid" to ("Solid" to "Однотонный"),
                "chart_settings_color_always_green" to ("Always green" to "Всегда зелёный"),
                "chart_settings_color_always_red" to ("Always red" to "Всегда красный"),
                "chart_settings_color_by_direction" to ("By direction" to "По направлению"),
                "chart_settings_projection" to ("Projections" to "Проекции"),
                "chart_settings_projection_cd" to ("Toggle projections" to "Переключить проекции"),
            )

        expected.forEach { (key, values) ->
            assertEquals("[$key] English value", values.first, enValues[key])
            assertEquals("[$key] Russian value", values.second, ruValues[key])
        }
    }

    @Test
    fun `every module with plurals has the same plural names in EN and RU`() {
        MODULES_WITH_PLURALS.forEach { module ->
            val enNames = parsePluralNames(resolveResFile(module, "values/plurals.xml"))
            val ruNames = parsePluralNames(resolveResFile(module, "values-ru/plurals.xml"))

            assertEquals(
                "[$module] Plural-name parity broken — " +
                    "only in EN: ${enNames - ruNames}, only in RU: ${ruNames - enNames}",
                enNames,
                ruNames,
            )
        }
    }

    @Test
    fun `every RU plurals block carries all four CLDR Russian quantity categories`() {
        MODULES_WITH_PLURALS.forEach { module ->
            parsePluralQuantities(resolveResFile(module, "values-ru/plurals.xml"))
                .forEach { (name, quantities) ->
                    assertTrue(
                        "[$module] RU plural '$name' is missing categories " +
                            "${RUSSIAN_QUANTITIES - quantities}; " +
                            "Russian requires $RUSSIAN_QUANTITIES but had $quantities",
                        quantities.containsAll(RUSSIAN_QUANTITIES),
                    )
                }
        }
    }

    private companion object {
        val RUSSIAN_QUANTITIES = setOf("one", "few", "many", "other")

        val MODULES_WITH_STRINGS =
            listOf(
                "app",
                "feature/dashboard",
                "feature/transaction",
                "feature/transactionslist",
                "feature/settings",
                "feature/lockscreen",
                "feature/onboarding",
                "feature/dictionaries",
                "feature/cloudsync",
                "core/designsystem",
            )

        val MODULES_WITH_PLURALS =
            listOf(
                "app",
                "feature/dictionaries",
            )

        /**
         * Tries two candidate paths:
         *   1. "$module/src/main/res/$relativeWithinRes" — resolves when cwd is the repo root.
         *   2. "../$module/src/main/res/$relativeWithinRes" — resolves when cwd is the :app
         *      module directory (the working directory AGP configures for :app unit tests).
         */
        fun resolveResFile(
            module: String,
            relativeWithinRes: String,
        ): File {
            val candidates =
                listOf(
                    File("$module/src/main/res/$relativeWithinRes"),
                    File("../$module/src/main/res/$relativeWithinRes"),
                )
            return candidates.firstOrNull(File::isFile) ?: candidates.first().absoluteFile
        }

        fun newDocumentBuilder() =
            DocumentBuilderFactory
                .newInstance()
                .apply {
                    isNamespaceAware = false
                    isExpandEntityReferences = false
                }.newDocumentBuilder()

        /**
         * Returns the set of <string name="…"> keys that should be translated.
         * Keys with translatable="false" are excluded — they intentionally have no RU counterpart.
         */
        fun parseTranslatableStringKeys(file: File): Set<String> {
            require(file.isFile) { "strings.xml not found: ${file.path}" }
            val doc = newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val keys = LinkedHashSet<String>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                if (el.getAttribute("translatable") == "false") continue
                val name = el.getAttribute("name")
                if (name.isNotBlank()) keys += name
            }
            return keys
        }

        fun parseStringValues(file: File): Map<String, String> {
            require(file.isFile) { "strings.xml not found: ${file.path}" }
            val doc = newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val values = LinkedHashMap<String, String>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val name = el.getAttribute("name")
                if (name.isNotBlank()) values[name] = el.textContent
            }
            return values
        }

        /** Returns a map from plural name to the set of quantity attributes declared on its items. */
        fun parsePluralQuantities(file: File): Map<String, Set<String>> {
            require(file.isFile) { "plurals.xml not found: ${file.path}" }
            val doc = newDocumentBuilder().parse(file)
            val pluralNodes = doc.getElementsByTagName("plurals")
            val result = LinkedHashMap<String, Set<String>>()
            for (i in 0 until pluralNodes.length) {
                val plural = pluralNodes.item(i) as Element
                val name = plural.getAttribute("name")
                val quantities = LinkedHashSet<String>()
                val items = plural.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    quantities += (items.item(j) as Element).getAttribute("quantity")
                }
                result[name] = quantities
            }
            return result
        }

        fun parsePluralNames(file: File): Set<String> = parsePluralQuantities(file).keys
    }
}
