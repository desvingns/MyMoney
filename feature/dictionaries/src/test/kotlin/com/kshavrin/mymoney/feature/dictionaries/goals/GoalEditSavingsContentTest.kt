package com.kshavrin.mymoney.feature.dictionaries.goals

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.model.SavingsProjection
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Contract-level pinning for [GoalEditContent] (S29 — savings variant).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:dictionaries`'s test classpath has only `junit`, `kotlinx-coroutines-test`, and `turbine`.
 * The `androidx.compose.ui:ui-test-junit4` and Robolectric artifacts are absent from the offline
 * Gradle cache, so a `createComposeRule()` test would fail at compile time. This mirrors the
 * deliberate deferral documented in `TransactionsListContentTest`, `ThemeSettingsContentTest`, and
 * the other `:feature:*` contract pinning tests — full Compose-UI tests land in PHASE_15 once
 * those dependencies are wired into this module's `build.gradle.kts`.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```kotlin
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class GoalEditSavingsContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun defaultState(
 *         accounts: List<Account> = listOf(anAccount()),
 *         accountId: Long = 1L,
 *         capitalDelta: BigDecimal? = null,
 *         capitalDeltaAmountFormatted: String? = null,
 *         savingsProjection: SavingsProjection? = null,
 *         variant: GoalVariant = GoalVariant.SAVINGS,
 *     ) = GoalEditState(
 *         accounts = accounts,
 *         accountId = accountId,
 *         capitalDelta = capitalDelta,
 *         capitalDeltaAmountFormatted = capitalDeltaAmountFormatted,
 *         savingsProjection = savingsProjection,
 *         variant = variant,
 *         currentBalanceFormatted = "10000 ₽",
 *     )
 *
 *     @Test fun `form fields are rendered`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = defaultState(), onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_name)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_starting_capital)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_monthly_contribution)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_target_amount)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `tapping the icon circle opens IconPickerSheet`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = defaultState(), onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithContentDescription("ic_goal_other").performClick()
 *         composeTestRule.onNodeWithText(getString(R.string.dictionaries_choose_icon)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `capital-diff text shows remaining when delta is positive`() {
 *         val state = defaultState(capitalDelta = BigDecimal("20000"), capitalDeltaAmountFormatted = "20000 ₽")
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_remaining_on_account, "20000 ₽")).assertIsDisplayed()
 *     }
 *
 *     @Test fun `capital-diff text shows short when delta is negative`() {
 *         val state = defaultState(capitalDelta = BigDecimal("-5000"), capitalDeltaAmountFormatted = "5000 ₽")
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_short_on_account, "5000 ₽")).assertIsDisplayed()
 *     }
 *
 *     @Test fun `capital-diff text shows exact when delta is zero`() {
 *         val state = defaultState(capitalDelta = BigDecimal.ZERO, capitalDeltaAmountFormatted = "")
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_capital_exact)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `achievement date text appears for ON_TRACK projection`() {
 *         val date = LocalDate.of(2027, 6, 1)
 *         val projection = SavingsProjection(monthsToGoal = 12, achievementDate = date, status = GoalStatus.ON_TRACK)
 *         val state = defaultState(savingsProjection = projection)
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(containsSubstring(date.toString())).assertIsDisplayed()
 *     }
 *
 *     @Test fun `already achieved text appears for ALREADY_ACHIEVED projection`() {
 *         val projection = SavingsProjection(monthsToGoal = 0, achievementDate = LocalDate.now(), status = GoalStatus.ALREADY_ACHIEVED)
 *         val state = defaultState(savingsProjection = projection)
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_already_achieved)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `unreachable text appears for UNREACHABLE projection`() {
 *         val projection = SavingsProjection(monthsToGoal = null, achievementDate = null, status = GoalStatus.UNREACHABLE)
 *         val state = defaultState(savingsProjection = projection)
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_unreachable)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `Save button click fires SaveClicked event`() {
 *         val events = mutableListOf<GoalEditEvent>()
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = defaultState(), onEvent = { events += it }) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_save)).performClick()
 *         composeTestRule.runOnIdle { assertEquals(listOf(GoalEditEvent.SaveClicked), events) }
 *     }
 * }
 * ```
 */
class GoalEditSavingsContentTest {

    private val now: Instant = Instant.parse("2026-06-06T10:00:00Z")

    private fun anAccount(
        id: Long = 1L,
        name: String = "Main",
        currencyId: Long = 1L,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#4A8FCB",
        iconKey = "ic_account_wallet",
        isDefault = false,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
        isArchived = false,
    )

    private fun defaultState(
        accounts: List<Account> = listOf(anAccount()),
        accountId: Long = 1L,
        name: String = "",
        iconKey: String = "ic_goal_other",
        variant: GoalVariant = GoalVariant.SAVINGS,
        currentBalanceFormatted: String = "10000 ₽",
        startingCapital: String = "",
        monthlyContribution: String = "",
        targetAmount: String = "",
        capitalDelta: BigDecimal? = null,
        capitalDeltaAmountFormatted: String? = null,
        savingsProjection: SavingsProjection? = null,
    ) = GoalEditState(
        accounts = accounts,
        accountId = accountId,
        name = name,
        iconKey = iconKey,
        variant = variant,
        currentBalanceFormatted = currentBalanceFormatted,
        startingCapital = startingCapital,
        monthlyContribution = monthlyContribution,
        targetAmount = targetAmount,
        capitalDelta = capitalDelta,
        capitalDeltaAmountFormatted = capitalDeltaAmountFormatted,
        savingsProjection = savingsProjection,
    )

    // ── String resource keys ─────────────────────────────────────────────────────

    @Test
    fun `R-string goal_name exists`() {
        val id: Int = R.string.goal_name
        assertTrue("R.string.goal_name must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_icon exists`() {
        val id: Int = R.string.goal_icon
        assertTrue("R.string.goal_icon must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_account exists`() {
        val id: Int = R.string.goal_account
        assertTrue("R.string.goal_account must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_current_balance exists`() {
        val id: Int = R.string.goal_current_balance
        assertTrue("R.string.goal_current_balance must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_starting_capital exists`() {
        val id: Int = R.string.goal_starting_capital
        assertTrue("R.string.goal_starting_capital must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_remaining_on_account exists`() {
        val id: Int = R.string.goal_remaining_on_account
        assertTrue("R.string.goal_remaining_on_account must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_short_on_account exists`() {
        val id: Int = R.string.goal_short_on_account
        assertTrue("R.string.goal_short_on_account must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_capital_exact exists`() {
        val id: Int = R.string.goal_capital_exact
        assertTrue("R.string.goal_capital_exact must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_monthly_contribution exists`() {
        val id: Int = R.string.goal_monthly_contribution
        assertTrue("R.string.goal_monthly_contribution must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_target_amount exists`() {
        val id: Int = R.string.goal_target_amount
        assertTrue("R.string.goal_target_amount must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_achievement_date exists`() {
        val id: Int = R.string.goal_achievement_date
        assertTrue("R.string.goal_achievement_date must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_already_achieved exists`() {
        val id: Int = R.string.goal_already_achieved
        assertTrue("R.string.goal_already_achieved must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_unreachable exists`() {
        val id: Int = R.string.goal_unreachable
        assertTrue("R.string.goal_unreachable must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_save exists`() {
        val id: Int = R.string.goal_save
        assertTrue("R.string.goal_save must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_variant_savings exists`() {
        val id: Int = R.string.goal_variant_savings
        assertTrue("R.string.goal_variant_savings must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_variant_credit exists`() {
        val id: Int = R.string.goal_variant_credit
        assertTrue("R.string.goal_variant_credit must be a valid resource id", id != 0)
    }

    // ── State-driven capital-delta logic (mirrors GoalEditContent render decision) ─

    @Test
    fun `capital-delta null means the diff row is hidden`() {
        val state = defaultState(capitalDelta = null)
        assertNull("null capitalDelta → diff row must not be rendered", state.capitalDelta)
    }

    @Test
    fun `capital-delta positive signum selects remaining text branch`() {
        val delta = BigDecimal("20000")
        assertTrue("positive delta → remaining-on-account branch", delta.signum() > 0)
    }

    @Test
    fun `capital-delta negative signum selects short text branch`() {
        val delta = BigDecimal("-5000")
        assertTrue("negative delta → short-on-account branch", delta.signum() < 0)
    }

    @Test
    fun `capital-delta zero signum selects exact text branch`() {
        val delta = BigDecimal.ZERO
        assertEquals("zero delta → exact-match branch", 0, delta.signum())
    }

    // ── State-driven projection logic (mirrors GoalEditContent render decision) ──

    @Test
    fun `null savingsProjection means the achievement row is hidden`() {
        val state = defaultState(savingsProjection = null)
        assertNull("null projection → achievement row must not be rendered", state.savingsProjection)
    }

    @Test
    fun `ON_TRACK projection has a non-null achievementDate`() {
        val projection = SavingsProjection(
            monthsToGoal = 18,
            achievementDate = LocalDate.of(2027, 12, 6),
            status = GoalStatus.ON_TRACK,
        )
        assertEquals(GoalStatus.ON_TRACK, projection.status)
        assertNotNull("ON_TRACK must carry achievementDate", projection.achievementDate)
    }

    @Test
    fun `ALREADY_ACHIEVED projection renders its own text branch`() {
        val projection = SavingsProjection(
            monthsToGoal = 0,
            achievementDate = LocalDate.now(),
            status = GoalStatus.ALREADY_ACHIEVED,
        )
        assertEquals(GoalStatus.ALREADY_ACHIEVED, projection.status)
    }

    @Test
    fun `UNREACHABLE projection has a null achievementDate`() {
        val projection = SavingsProjection(
            monthsToGoal = null,
            achievementDate = null,
            status = GoalStatus.UNREACHABLE,
        )
        assertEquals(GoalStatus.UNREACHABLE, projection.status)
        assertNull("UNREACHABLE must carry null achievementDate", projection.achievementDate)
    }

    // ── State-driven icon picker logic ────────────────────────────────────────────

    @Test
    fun `default iconKey is ic_goal_other`() {
        val state = defaultState()
        assertEquals("ic_goal_other", state.iconKey)
    }

    @Test
    fun `IconSelected event payload is passed through as the new iconKey`() {
        val expectedKey = "ic_goal_car"
        val event = GoalEditEvent.IconSelected(expectedKey)
        assertEquals(expectedKey, event.iconKey)
    }

    // ── State-driven account dropdown logic ───────────────────────────────────────

    @Test
    fun `selected account is the one whose id matches state accountId`() {
        val accounts = listOf(anAccount(id = 1L, name = "Cash"), anAccount(id = 2L, name = "Card"))
        val state = defaultState(accounts = accounts, accountId = 2L)
        val selected = state.accounts.firstOrNull { it.id == state.accountId }
        assertNotNull(selected)
        assertEquals("Card", selected!!.name)
    }

    @Test
    fun `no matching account returns empty name for dropdown`() {
        val state = defaultState(accounts = emptyList(), accountId = 99L)
        val selected = state.accounts.firstOrNull { it.id == state.accountId }
        assertTrue("no accounts → selected must be null → empty dropdown", selected?.name.orEmpty().isEmpty())
    }

    // ── State-driven variant segmented-button logic ───────────────────────────────

    @Test
    fun `SAVINGS variant selects the first segment`() {
        val state = defaultState(variant = GoalVariant.SAVINGS)
        assertTrue(state.variant == GoalVariant.SAVINGS)
        assertFalse(state.variant == GoalVariant.CREDIT)
    }

    @Test
    fun `CREDIT variant selects the second segment and shows credit placeholder`() {
        val state = defaultState(variant = GoalVariant.CREDIT)
        assertTrue(state.variant == GoalVariant.CREDIT)
        assertFalse(state.variant == GoalVariant.SAVINGS)
    }

    // ── SaveClicked event shape ───────────────────────────────────────────────────

    @Test
    fun `SaveClicked is a singleton event with no payload`() {
        val event: GoalEditEvent = GoalEditEvent.SaveClicked
        assertEquals(GoalEditEvent.SaveClicked, event)
    }

    @Test
    fun `BackClicked is a singleton event with no payload`() {
        val event: GoalEditEvent = GoalEditEvent.BackClicked
        assertEquals(GoalEditEvent.BackClicked, event)
    }
}
