package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyPolicyAdvertisingContractTest {
    private val repositoryRoot = findRepositoryRoot()
    private val assetEn = File(repositoryRoot, "app/src/main/assets/privacy_policy_en.html")
    private val assetRu = File(repositoryRoot, "app/src/main/assets/privacy_policy_ru.html")
    private val pagesEn = File(repositoryRoot, "privacy-policy/en/index.html")
    private val pagesRu = File(repositoryRoot, "privacy-policy/ru/index.html")
    private val appAds = File(repositoryRoot, "privacy-policy/app-ads.txt")
    private val pagesWorkflow = File(repositoryRoot, ".github/workflows/privacy-policy-pages.yml")
    private val monetizationDraft = File(repositoryRoot, "docs/legal/privacy-policy-monetization-draft.md")

    @Test
    fun `both app asset locales carry the advertising block with the same key claims`() {
        val en = assetEn.requireReadableText()
        val ru = assetRu.requireReadableText()

        assertContainsAll(
            en,
            listOf(
                "<h3>Advertising (Google AdMob)</h3>",
                "rewarded ads only",
                "Google Mobile Ads SDK collects your device's advertising ID",
                "com.google.android.gms.permission.AD_ID",
                "server-side verification",
                "No transaction, account, category, note or other financial record from MyMoney is ever sent to Google's advertising services.",
            ),
        )
        assertContainsAll(
            ru,
            listOf(
                "<h3>Реклама (Google AdMob)</h3>",
                "только награждаемую рекламу",
                "SDK Google Mobile Ads собирает рекламный идентификатор устройства",
                "com.google.android.gms.permission.AD_ID",
                "серверную верификацию",
                "никогда не передаются рекламным сервисам Google",
            ),
        )
    }

    @Test
    fun `last updated dates in both locales resolve to the same calendar date`() {
        val enDate = parseEnLastUpdated(assetEn.requireReadableText())
        val ruDate = parseRuLastUpdated(assetRu.requireReadableText())

        assertEquals(
            "EN and RU policies must declare the same calendar date in 'Last updated'",
            enDate,
            ruDate,
        )
        assertEquals(enDate, parseEnLastUpdated(pagesEn.requireReadableText()))
        assertEquals(ruDate, parseRuLastUpdated(pagesRu.requireReadableText()))
    }

    @Test
    fun `published en page is identical to the app asset policy`() {
        assertEquals(
            "privacy-policy/en/index.html must match app/src/main/assets/privacy_policy_en.html",
            normalized(assetEn),
            normalized(pagesEn),
        )
    }

    @Test
    fun `published ru page is identical to the app asset policy`() {
        assertEquals(
            "privacy-policy/ru/index.html must match app/src/main/assets/privacy_policy_ru.html",
            normalized(assetRu),
            normalized(pagesRu),
        )
    }

    @Test
    fun `advertising block region matches between app assets and published pages`() {
        val enRegion = advertisingRegion(assetEn.requireReadableText(), "<h3>Advertising (Google AdMob)</h3>")
        val ruRegion = advertisingRegion(assetRu.requireReadableText(), "<h3>Реклама (Google AdMob)</h3>")

        assertEquals(enRegion, advertisingRegion(pagesEn.requireReadableText(), "<h3>Advertising (Google AdMob)</h3>"))
        assertEquals(ruRegion, advertisingRegion(pagesRu.requireReadableText(), "<h3>Реклама (Google AdMob)</h3>"))
    }

    @Test
    fun `app-ads txt contains exactly the admob publisher line`() {
        val lines =
            appAds
                .requireReadableText()
                .lines()
                .map(String::trim)
                .filter(String::isNotEmpty)

        assertEquals("app-ads.txt must contain exactly one record", 1, lines.size)
        assertEquals(ADMOB_PUBLISHER_LINE, lines.single())
    }

    @Test
    fun `pages workflow deploys app-ads txt to the site root`() {
        val text = pagesWorkflow.requireReadableText()

        assertContainsAll(
            text,
            listOf(
                "\"privacy-policy/**\"",
                "cp privacy-policy/app-ads.txt",
                "gh-pages/app-ads.txt",
                "add privacy-policy account-deletion app-ads.txt",
            ),
        )
    }

    @Test
    fun `monetization draft marks the advertising block as applied with the publisher line`() {
        val text = monetizationDraft.requireReadableText()

        assertContainsAll(
            text,
            listOf(
                // Table row ties the advertising epic to the applied date; status header wording
                // may evolve (e.g. "all blocks applied"), but this table entry is stable once set.
                "`support-rewarded-ads` (applied 2026-08-16)",
                // Per-block annotation in the Block 4 section body.
                "**Applied 2026-08-16**",
                ADMOB_PUBLISHER_LINE,
            ),
        )
    }

    @Test
    fun `both app asset locales carry the purchases block with the same key claims`() {
        val en = assetEn.requireReadableText()
        val ru = assetRu.requireReadableText()

        assertContainsAll(
            en,
            listOf(
                "<h3>Purchases (Google Play Billing)</h3>",
                "entirely by Google Play.",
                "MyMoney never sees or stores your card",
                "purchase token",
                "the resulting entitlement",
            ),
        )
        assertContainsAll(
            ru,
            listOf(
                "<h3>Покупки (Google Play Billing)</h3>",
                "обрабатываются Google Play.",
                "MyMoney никогда не видит и не хранит данные вашей карты",
                "токен покупки",
                "итоговое право",
            ),
        )
    }

    @Test
    fun `both app asset locales describe firebase services as two active SDKs`() {
        val en = assetEn.requireReadableText().replace(Regex("\\s+"), " ")
        val ru = assetRu.requireReadableText().replace(Regex("\\s+"), " ")

        assertContainsAll(
            en,
            listOf(
                "<h3>Firebase services</h3>",
                "two Google Firebase services.",
                "Firebase Remote Config",
                "delivers feature configuration values",
                "Firebase Analytics",
                "Firebase Analytics records app usage events",
                "service receives your transactions",
            ),
        )
        assertContainsAll(
            ru,
            listOf(
                "<h3>Сервисы Firebase</h3>",
                "два сервиса Google Firebase.",
                "Firebase Remote Config",
                "доставляет в приложение значения конфигурации функций",
                "Firebase Analytics",
                "Analytics записывает события использования приложения",
                "получает ваши операции",
            ),
        )
    }

    @Test
    fun `retired services-and-feature-flags phrase is absent from all policy files`() {
        listOf(assetEn, assetRu, pagesEn, pagesRu).forEach { file ->
            val text = file.requireReadableText()
            assertTrue(
                "File ${file.name} must not contain the retired 'Services and feature flags' heading",
                !text.contains("Services and feature flags"),
            )
            assertTrue(
                "File ${file.name} must not contain the retired 'Firebase Remote Config is not enabled' phrase",
                !text.contains("Firebase Remote Config is not enabled"),
            )
        }
    }

    private fun advertisingRegion(
        text: String,
        heading: String,
    ): String {
        val start = text.indexOf(heading)
        assertTrue("Expected advertising heading '$heading'", start >= 0)
        val end = text.indexOf("</p>", start)
        assertTrue("Expected a closing paragraph after '$heading'", end >= 0)
        return text.substring(start, end + "</p>".length).replace(Regex("\\s+"), " ")
    }

    private fun parseEnLastUpdated(text: String): Triple<Int, Int, Int> {
        val match = Regex("Last updated: ([A-Za-z]+) (\\d{1,2}), (\\d{4})").find(text)
        assertNotNull("Expected 'Last updated: <Month> <day>, <year>' in the EN policy", match)
        val (monthName, day, year) = match!!.destructured
        val month = EN_MONTHS.indexOf(monthName) + 1
        assertTrue("Unknown English month '$monthName'", month > 0)
        return Triple(year.toInt(), month, day.toInt())
    }

    private fun parseRuLastUpdated(text: String): Triple<Int, Int, Int> {
        val match = Regex("Дата последнего обновления: (\\d{1,2}) ([А-Яа-яё]+) (\\d{4})").find(text)
        assertNotNull("Expected 'Дата последнего обновления: <day> <month> <year>' in the RU policy", match)
        val (day, monthName, year) = match!!.destructured
        val month = RU_MONTHS.indexOf(monthName.lowercase()) + 1
        assertTrue("Unknown Russian month '$monthName'", month > 0)
        return Triple(year.toInt(), month, day.toInt())
    }

    private fun normalized(file: File): String =
        file
            .requireReadableText()
            .replace("\r\n", "\n")
            .trim()

    private fun File.requireReadableText(): String {
        assertTrue("Expected file to exist: $path", isFile)
        return readText()
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private companion object {
        const val ADMOB_PUBLISHER_LINE = "google.com, pub-2270788427402644, DIRECT, f08c47fec0942fa0"

        val EN_MONTHS =
            listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December",
            )

        val RU_MONTHS =
            listOf(
                "января", "февраля", "марта", "апреля", "мая", "июня",
                "июля", "августа", "сентября", "октября", "ноября", "декабря",
            )

        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile &&
                        File(candidate, "privacy-policy/app-ads.txt").isFile
                } ?: start
        }
    }
}
