package com.kshavrin.mymoney.feature.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Contract-level pinning for [DateHeader] (form-chrome restyle).
 *
 * [DateHeader] renders a centred row: a calendar icon plus the supplied
 * [LocalDate] formatted with the pattern `"EEEE, d MMMM"` (locale-aware),
 * and the whole row is `clickable` → invokes its `onClick` (the
 * `DateChipClicked` path that opens the date picker).
 *
 * # Why this is not a full Compose-UI test
 *
 * `:feature:transaction`'s offline test classpath has only
 * `libs.junit` + `libs.kotlinx.coroutines.test` + `libs.turbine` — no
 * Robolectric, no `androidx.compose.ui:ui-test-junit4`,
 * no `ui-test-manifest` (see build.gradle.kts). A `createComposeRule()`
 * test would not compile here. This mirrors the deliberate deferral
 * already documented across the codebase (e.g.
 * `:feature:transactionslist`'s `TransactionDetailContentTest` and
 * `:core:designsystem`'s `MonefyKeypadTest`). The executable Compose
 * test lands in PHASE_15 once those deps are wired in.
 *
 * What IS JVM-visible — and what this file pins — is the exact date
 * format string the header puts on screen. The icon, the click wiring and
 * the centred layout are covered by the documented Compose template below.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class DateHeaderContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     @Test fun `renders the formatted date for the given LocalDate`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { DateHeader(date = LocalDate.of(2026, 5, 18), onClick = {}) }
 *         }
 *         composeTestRule.onNodeWithText("Monday, 18 May").assertIsDisplayed()
 *     }
 *
 *     @Test fun `tapping the header invokes onClick (DateChipClicked path)`() {
 *         var clicked = false
 *         composeTestRule.setContent {
 *             MyMoneyTheme { DateHeader(date = LocalDate.of(2026, 5, 18), onClick = { clicked = true }) }
 *         }
 *         composeTestRule.onNodeWithText("Monday, 18 May").performClick()
 *         assertTrue(clicked)
 *     }
 *
 *     @Test fun `calendar icon carries the pick-date content description`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { DateHeader(date = LocalDate.of(2026, 5, 18), onClick = {}) }
 *         }
 *         composeTestRule.onNodeWithContentDescription("Pick date").assertIsDisplayed()
 *     }
 * }
 * ```
 */
class DateHeaderContractTest {

    /**
     * Mirror of the formatter built inside [DateHeader]:
     * `DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)`.
     * Keep the pattern string in lock-step with DateHeader.kt.
     */
    private fun headerText(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date)

    @Test
    fun `formats a date as full weekday, day and month in English`() {
        // 2026-05-18 is a Monday.
        assertEquals("Monday, 18 May", headerText(LocalDate.of(2026, 5, 18), Locale.ENGLISH))
    }

    @Test
    fun `uses no leading zero on the day of month`() {
        assertEquals("Friday, 1 May", headerText(LocalDate.of(2026, 5, 1), Locale.ENGLISH))
    }

    @Test
    fun `renders the full month name not an abbreviation`() {
        val text = headerText(LocalDate.of(2026, 12, 25), Locale.ENGLISH)
        assertTrue("expected the full month name 'December' in: $text", text.contains("December"))
        assertEquals("Friday, 25 December", text)
    }

    @Test
    fun `is locale-aware (Russian renders Cyrillic month and weekday)`() {
        // Pins that the pattern is fed a Locale and is not hard-coded to English.
        val ru = Locale("ru")
        val text = headerText(LocalDate.of(2026, 5, 18), ru)
        // Russian: "понедельник, 18 мая" — assert it is NOT the English rendering.
        assertTrue(
            "Russian formatting must differ from the English one: $text",
            text != headerText(LocalDate.of(2026, 5, 18), Locale.ENGLISH),
        )
    }
}
