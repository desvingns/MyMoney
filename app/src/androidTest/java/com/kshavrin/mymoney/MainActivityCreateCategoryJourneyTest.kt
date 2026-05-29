package com.kshavrin.mymoney

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR
import com.kshavrin.mymoney.feature.dictionaries.R as DictionariesR
import com.kshavrin.mymoney.feature.onboarding.R as OnboardingR
import com.kshavrin.mymoney.feature.transaction.R as TransactionR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

/**
 * J3 — create-a-category round trip preserves the amount and applies the new category (AS-4).
 *
 * From the expense form with an amount already entered: Choose category -> S09 picker -> "+ ADD" ->
 * S22 create -> Save. The flow returns PAST S09 with the freshly created category pre-selected and
 * the amount preserved, so the saved expense carries the new category and the original amount.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityCreateCategoryJourneyTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Test
    fun createCategoryFromPickerPreservesAmountAndAppliesNewCategory() {
        hiltRule.inject()

        // Onboarding -> Dashboard
        val skip = targetString(OnboardingR.string.onboarding_skip)
        waitForText(skip)
        composeRule.onNodeWithText(skip).performClick()

        // Dashboard -> Add expense form
        val expenseFab = targetString(DashboardR.string.fab_expense)
        waitForContentDescription(expenseFab)
        composeRule.onNodeWithContentDescription(expenseFab).performClick()

        // Enter amount 9 then Choose category.
        val chooseCategory = targetString(TransactionR.string.choose_category_cta)
        waitForText(chooseCategory)
        composeRule.onNode(hasText("9") and hasClickAction()).performClick()
        composeRule.onNode(hasText(chooseCategory) and hasClickAction()).performClick()

        // S09 picker -> "+ ADD" -> S22 create
        val addCta = targetString(TransactionR.string.add_category_cta)
        waitForContentDescription(addCta)
        composeRule.onNodeWithContentDescription(addCta).performClick()

        // S22: name the new category and save; the flow cascades back past S09 and S06 auto-saves.
        waitForText(targetString(DictionariesR.string.dictionaries_field_name))
        composeRule.onNode(hasSetTextAction()).performTextInput(NEW_CATEGORY)
        composeRule.onNodeWithText(targetString(DictionariesR.string.dictionaries_save)).performClick()

        // Back on the dashboard once the expense auto-saved.
        waitForContentDescription(expenseFab)

        // AS-4: exactly one expense, with the preserved amount 9 and the newly created category.
        val newCategory = runBlocking {
            categoryRepository.observeAll().first().first { it.name == NEW_CATEGORY }
        }
        val txns = runBlocking { transactionRepository.observeAll().first() }
        assertEquals("expected a single saved expense", 1, txns.size)
        val saved = txns.single()
        assertEquals(0, BigDecimal("9").compareTo(saved.amount))
        assertEquals(newCategory.id, saved.categoryId)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(cd: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithContentDescription(cd).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private companion object {
        const val TIMEOUT = 20_000L
        const val NEW_CATEGORY = "Coffee"
    }
}
