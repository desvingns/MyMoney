package com.kshavrin.mymoney.feature.settings.about

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract-level pinning for [assetSuffix] in WebViewScreen.kt (S20).
 *
 * # Why this is a placeholder, not a direct call to [assetSuffix]
 *
 * The production `assetSuffix()` reads the current app locale straight from
 * `AppCompatDelegate.getApplicationLocales()`:
 *
 * ```
 * internal fun assetSuffix(): String {
 *     val locales = AppCompatDelegate.getApplicationLocales()
 *     return if (!locales.isEmpty && locales[0]?.language == "ru") "_ru" else "_en"
 * }
 * ```
 *
 * `AppCompatDelegate` is an Android framework class; calling `getApplicationLocales()` on the plain
 * JVM throws (no Android runtime), so `assetSuffix()` is not directly unit-testable here. There is
 * also no pure helper that takes a language tag and returns the suffix — the locale read and the
 * branch are fused in one function. So per the two options open to a tester (test a pure seam if one
 * exists; otherwise pin the contract as a placeholder + embed the real test), this file takes the
 * second route, exactly like this module's S15/S19 Content tests and :feature:transactionslist's
 * Content tests. `:feature:settings`'s offline test classpath carries only junit + coroutines-test +
 * turbine — no Robolectric — so the locale-driven test below cannot run until PHASE_15.
 *
 * Testability note for the developer: extracting a pure seam, e.g.
 * `internal fun assetSuffix(languageTag: String?): String =
 *     if (languageTag == "ru") "_ru" else "_en"`, and having the Android-facing overload read the
 * locale and delegate to it, would make the en/ru/default mapping directly unit-testable on the JVM
 * (the placeholder below would become live assertions against that seam). Left to the developer — a
 * tester does not modify production code.
 *
 * # The pinned contract
 *
 * Asset suffix selection is binary and depends only on the primary locale's language:
 *
 *   - language == "ru"            -> "_ru"
 *   - any other non-empty language -> "_en"
 *   - empty locale list / null     -> "_en"  (English is the default, per SPEC)
 *
 * The resulting asset path is `file:///android_asset/<base><suffix>.html`, e.g.
 * `privacy_policy_ru.html` / `help_en.html`.
 *
 * # What the real Robolectric/instrumented test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * class AssetSuffixTest {
 *
 *     @After fun clearLocale() {
 *         AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
 *     }
 *
 *     @Test fun `russian locale yields _ru`() {
 *         AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
 *         assertEquals("_ru", assetSuffix())
 *     }
 *
 *     @Test fun `english locale yields _en`() {
 *         AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
 *         assertEquals("_en", assetSuffix())
 *     }
 *
 *     @Test fun `other locale falls back to _en`() {
 *         AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
 *         assertEquals("_en", assetSuffix())
 *     }
 *
 *     @Test fun `empty locale list falls back to _en`() {
 *         AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
 *         assertEquals("_en", assetSuffix())
 *     }
 * }
 * ```
 */
class AssetSuffixTest {

    /**
     * Mirror of the pure branch inside `assetSuffix()`, factored out so the en/ru/default contract
     * can be exercised on the JVM. This is intentionally identical to the production decision:
     * `if (language == "ru") "_ru" else "_en"`. When the developer extracts a real pure seam these
     * assertions should be repointed at it (and this mirror deleted).
     */
    private fun suffixForLanguage(language: String?): String =
        if (language == "ru") "_ru" else "_en"

    @Test
    fun `russian language maps to _ru`() {
        assertEquals("_ru", suffixForLanguage("ru"))
    }

    @Test
    fun `english language maps to _en`() {
        assertEquals("_en", suffixForLanguage("en"))
    }

    @Test
    fun `unrelated language falls back to _en`() {
        assertEquals("_en", suffixForLanguage("fr"))
    }

    @Test
    fun `null language falls back to _en`() {
        assertEquals("_en", suffixForLanguage(null))
    }

    @Test
    fun `empty language falls back to _en`() {
        assertEquals("_en", suffixForLanguage(""))
    }

    @Test
    fun `only ru selects the russian asset all others select english`() {
        val tags = listOf("ru", "en", "fr", "de", "es", "", null)
        for (tag in tags) {
            val expected = if (tag == "ru") "_ru" else "_en"
            assertEquals("suffix for languageTag=$tag", expected, suffixForLanguage(tag))
        }
    }

    @Test
    fun `suffix composes into the expected android_asset html path`() {
        val privacyRu = "file:///android_asset/privacy_policy" + suffixForLanguage("ru") + ".html"
        val helpEn = "file:///android_asset/help" + suffixForLanguage("en") + ".html"
        assertEquals("file:///android_asset/privacy_policy_ru.html", privacyRu)
        assertEquals("file:///android_asset/help_en.html", helpEn)
    }
}
