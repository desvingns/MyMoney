package com.kshavrin.mymoney.feature.dictionaries

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.accounts.AccountEditContent
import com.kshavrin.mymoney.feature.dictionaries.accounts.AccountEditEvent
import com.kshavrin.mymoney.feature.dictionaries.accounts.AccountEditState
import com.kshavrin.mymoney.feature.dictionaries.categories.CategoryEditContent
import com.kshavrin.mymoney.feature.dictionaries.categories.CategoryEditEvent
import com.kshavrin.mymoney.feature.dictionaries.categories.CategoryEditState
import com.kshavrin.mymoney.feature.dictionaries.currencies.CurrencyEditContent
import com.kshavrin.mymoney.feature.dictionaries.currencies.CurrencyEditEvent
import com.kshavrin.mymoney.feature.dictionaries.currencies.CurrencyEditState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the S22/S24/S26 back-arrow defect (memo mymoney-edit-screen-backarrow-quirk):
 * the TopAppBar back-arrow used to emit SaveClicked, silently persisting the edit on cancel. It
 * must now emit a dedicated BackClicked event and never SaveClicked.
 */
@RunWith(AndroidJUnit4::class)
class EditScreenBackControlUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryEditBackArrowEmitsBackNotSave() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryEditContent(state = CategoryEditState(), onEvent = { events += it })
            }
        }

        composeTestRule.onNodeWithContentDescription(back()).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryEditEvent.BackClicked), events)
        }
    }

    @Test
    fun accountEditBackArrowEmitsBackNotSave() {
        val events = mutableListOf<AccountEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountEditContent(state = AccountEditState(), onEvent = { events += it })
            }
        }

        composeTestRule.onNodeWithContentDescription(back()).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AccountEditEvent.BackClicked), events)
        }
    }

    @Test
    fun currencyEditBackArrowEmitsBackNotSave() {
        val events = mutableListOf<CurrencyEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyEditContent(state = CurrencyEditState(), onEvent = { events += it })
            }
        }

        composeTestRule.onNodeWithContentDescription(back()).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CurrencyEditEvent.BackClicked), events)
        }
    }

    private fun back(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dictionaries_back)
}
