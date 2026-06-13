package com.kshavrin.mymoney

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.form.CATEGORY_GRID_ADD_CELL_TAG
import com.kshavrin.mymoney.core.designsystem.form.CATEGORY_GRID_TAG
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR
import com.kshavrin.mymoney.feature.dictionaries.R as DictionariesR
import com.kshavrin.mymoney.feature.onboarding.R as OnboardingR
import com.kshavrin.mymoney.feature.transaction.R as TransactionR

/**
 * J3 — create-a-category round trip preserves the amount and applies the new category (AS-4).
 *
 * From the expense form with an amount already entered: embedded "+ ADD" -> S22 create -> Save.
 * The flow returns to S06 with the amount preserved, so the saved expense carries the new category
 * and the original amount.
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
    fun createCategoryFromEmbeddedGridPreservesAmountAndAppliesNewCategory() =
        runTest {
            hiltRule.inject()

            // Onboarding -> Dashboard
            val skip = targetString(OnboardingR.string.onboarding_skip)
            waitForText(skip)
            composeRule.onNodeWithText(skip).performClick()

            // Dashboard -> Add expense form
            val expenseFab = targetString(DashboardR.string.fab_expense)
            waitForContentDescription(expenseFab)
            composeRule.onNodeWithContentDescription(expenseFab).performClick()

            waitForText(targetString(TransactionR.string.new_expense_title))
            composeRule.onNode(hasText("0") and hasClickAction()).performClick()
            composeRule.onNode(hasText("9") and hasClickAction()).performClick()
            composeRule.onNodeWithText(targetString(TransactionR.string.choose_category_button)).performClick()

            composeRule
                .onNodeWithTag(CATEGORY_GRID_TAG)
                .performScrollToNode(hasTestTag(CATEGORY_GRID_ADD_CELL_TAG))
            composeRule
                .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
                .performScrollTo()
                .performClick()

            waitForText(targetString(DictionariesR.string.dictionaries_field_name))
            composeRule.onNode(hasSetTextAction()).performTextInput(NEW_CATEGORY)
            composeRule.onNodeWithText(targetString(DictionariesR.string.dictionaries_save)).performClick()

            // Back on the dashboard once the expense auto-saved.
            waitForContentDescription(expenseFab)

            // AS-4: exactly one expense, with the preserved amount 9 and the newly created category.
            val newCategory = categoryRepository.observeAll().first().first { it.name == NEW_CATEGORY }
            val txns = transactionRepository.observeAll().first()
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
