package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {

    private val backupRulesFile = resolveResFile("backup_rules.xml")

    @Test
    fun `backup rules include the database and datastore payloads`() {
        val rules = parseRules(backupRulesFile)

        assertEquals(
            setOf(
                RuleEntry(domain = "database", path = "monefy.db"),
                RuleEntry(domain = "database", path = "monefy.db-shm"),
                RuleEntry(domain = "database", path = "monefy.db-wal"),
                RuleEntry(domain = "file", path = "datastore/app_settings.preferences_pb"),
            ),
            rules.includes,
        )
    }

    @Test
    fun `backup rules exclude the encrypted secure shared preferences`() {
        val rules = parseRules(backupRulesFile)

        assertEquals(
            setOf(RuleEntry(domain = "sharedpref", path = "com.kshavrin.mymoney_secure.xml")),
            rules.excludes,
        )
        assertFalse(rules.includes.contains(RuleEntry(domain = "sharedpref", path = "com.kshavrin.mymoney_secure.xml")))
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
            val candidates = listOf(
                File("src/main/res/xml/$fileName"),
                File("app/src/main/res/xml/$fileName"),
            )
            return candidates.firstOrNull(File::isFile)
                ?: candidates.first().absoluteFile
        }

        fun parseRules(file: File): ParsedRules {
            assertTrue("Missing ${file.path}", file.isFile)
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val includes = document.getElementsByTagName("include").asRuleEntries()
            val excludes = document.getElementsByTagName("exclude").asRuleEntries()
            return ParsedRules(includes = includes, excludes = excludes)
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
