package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DataExtractionRulesTest {
    private val extractionRulesFile = resolveResFile("data_extraction_rules.xml")

    @Test
    fun `cloud backup section includes the database and datastore payloads and excludes secure prefs`() {
        val rules = parseSection("cloud-backup")

        assertRules(rules)
    }

    @Test
    fun `device transfer section includes the database and datastore payloads and excludes secure prefs`() {
        val rules = parseSection("device-transfer")

        assertRules(rules)
    }

    @Test
    fun `data extraction rules no longer contain template todo text`() {
        assertFalse(extractionRulesFile.readText().contains("TODO"))
    }

    private fun assertRules(rules: ParsedRules) {
        assertEquals(
            setOf(
                RuleEntry(domain = "database", path = "monefy.db"),
                RuleEntry(domain = "database", path = "monefy.db-shm"),
                RuleEntry(domain = "database", path = "monefy.db-wal"),
                RuleEntry(domain = "file", path = "datastore/app_settings.preferences_pb"),
                RuleEntry(domain = "sharedpref", path = "."),
            ),
            rules.includes,
        )
        assertEquals(
            setOf(RuleEntry(domain = "sharedpref", path = "com.kshavrin.mymoney_secure.xml")),
            rules.excludes,
        )
    }

    private fun parseSection(name: String): ParsedRules {
        assertTrue("Missing ${extractionRulesFile.path}", extractionRulesFile.isFile)
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(extractionRulesFile)
        val sections = document.getElementsByTagName(name)
        assertEquals("Expected one <$name> section", 1, sections.length)
        val section = sections.item(0) as Element
        return ParsedRules(
            includes = section.getElementsByTagName("include").asRuleEntries(),
            excludes = section.getElementsByTagName("exclude").asRuleEntries(),
        )
    }

    private data class RuleEntry(
        val domain: String,
        val path: String,
    )

    private data class ParsedRules(
        val includes: Set<RuleEntry>,
        val excludes: Set<RuleEntry>,
    )

    private companion object {
        fun resolveResFile(fileName: String): File {
            val candidates =
                listOf(
                    File("src/main/res/xml/$fileName"),
                    File("app/src/main/res/xml/$fileName"),
                )
            return candidates.firstOrNull(File::isFile)
                ?: candidates.first().absoluteFile
        }

        fun org.w3c.dom.NodeList.asRuleEntries(): Set<RuleEntry> =
            buildSet {
                for (index in 0 until length) {
                    val element = item(index) as Element
                    add(
                        RuleEntry(
                            domain = element.getAttribute("domain"),
                            path = element.getAttribute("path"),
                        ),
                    )
                }
            }
    }
}
