package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JVM (no Robolectric) validation of the :app module's <plurals> resources.
 *
 * The PHASE_15 deliverable requires verifying CLDR plural-category coverage. The classic
 * localization bug this guards against: a Russian <plurals> that only ships {one, other}
 * (the English category set) renders wrong text for the `few` (2-4) and `many` (5+) buckets.
 * English needs exactly {one, other}; Russian needs all of {one, few, many, other}.
 *
 * Resources are read directly off disk. Gradle runs a module's unit tests with the module
 * directory as the working directory, so the module-relative path resolves; [resolveResFile]
 * also tolerates the repo root as the working directory.
 */
class PluralsTest {
    private val enFile = resolveResFile("app", "values/plurals.xml")
    private val ruFile = resolveResFile("app", "values-ru/plurals.xml")

    @Test
    fun `english plurals file exists`() {
        assertTrue("Missing ${enFile.path}", enFile.isFile)
    }

    @Test
    fun `russian plurals file exists`() {
        assertTrue("Missing ${ruFile.path}", ruFile.isFile)
    }

    @Test
    fun `every english plural has exactly one and other categories`() {
        parsePlurals(enFile).forEach { (name, quantities) ->
            assertEquals(
                "EN plural '$name' must declare exactly {one, other} but was $quantities",
                ENGLISH_CATEGORIES,
                quantities,
            )
        }
    }

    @Test
    fun `every russian plural covers all CLDR categories one few many other`() {
        parsePlurals(ruFile).forEach { (name, quantities) ->
            assertTrue(
                "RU plural '$name' is missing CLDR categories ${RUSSIAN_CATEGORIES - quantities}; " +
                    "Russian requires all of $RUSSIAN_CATEGORIES but had $quantities",
                quantities.containsAll(RUSSIAN_CATEGORIES),
            )
        }
    }

    @Test
    fun `english and russian declare the same plural names`() {
        val enNames = parsePlurals(enFile).keys
        val ruNames = parsePlurals(ruFile).keys
        assertEquals(
            "Plural-name parity broken: only in EN=${enNames - ruNames}, only in RU=${ruNames - enNames}",
            enNames,
            ruNames,
        )
    }

    @Test
    fun `english plural transactions_in_period is present`() {
        assertTrue(parsePlurals(enFile).containsKey("transactions_in_period"))
    }

    @Test
    fun `all items within a plural share the same positional format tokens`() {
        listOf(enFile, ruFile).forEach { file ->
            parseItems(file).forEach { (name, items) ->
                val tokenSets = items.values.map(::formatTokens).toSet()
                assertEquals(
                    "Format-token drift in '${file.parentFile.name}/${file.name}' plural '$name': " +
                        "items carry differing arg tokens $tokenSets",
                    1,
                    tokenSets.size,
                )
            }
        }
    }

    private companion object {
        val ENGLISH_CATEGORIES = setOf("one", "other")
        val RUSSIAN_CATEGORIES = setOf("one", "few", "many", "other")

        // Matches positional/format tokens: %d, %s, %1$s, %2$d, %.2f, %% (literal percent ignored).
        val FORMAT_TOKEN = Regex("""%(?:\d+\$)?[-+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

        fun resolveResFile(
            module: String,
            relativeWithinRes: String,
        ): File {
            val candidates =
                listOf(
                    // Working dir = the module directory (Gradle default for unit tests).
                    File("src/main/res/$relativeWithinRes"),
                    // Working dir = repo root.
                    File("$module/src/main/res/$relativeWithinRes"),
                )
            return candidates.firstOrNull(File::isFile)
                ?: candidates.first().absoluteFile
        }

        /** name -> set of quantity attribute values declared on its <item> children. */
        fun parsePlurals(file: File): Map<String, Set<String>> =
            parseItems(file).mapValues { (_, items) -> items.keys }

        /** name -> (quantity -> item text). */
        fun parseItems(file: File): Map<String, Map<String, String>> {
            require(file.isFile) { "plurals file not found: ${file.path}" }
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    isExpandEntityReferences = false
                }
            val doc = factory.newDocumentBuilder().parse(file)
            val pluralNodes = doc.getElementsByTagName("plurals")
            val result = LinkedHashMap<String, Map<String, String>>()
            for (i in 0 until pluralNodes.length) {
                val plural = pluralNodes.item(i) as Element
                val name = plural.getAttribute("name")
                val items = LinkedHashMap<String, String>()
                val itemNodes = plural.getElementsByTagName("item")
                for (j in 0 until itemNodes.length) {
                    val item = itemNodes.item(j) as Element
                    items[item.getAttribute("quantity")] = item.textContent
                }
                result[name] = items
            }
            return result
        }

        fun formatTokens(text: String): List<String> =
            FORMAT_TOKEN
                .findAll(text)
                .map { it.value }
                .sorted()
                .toList()
    }
}
