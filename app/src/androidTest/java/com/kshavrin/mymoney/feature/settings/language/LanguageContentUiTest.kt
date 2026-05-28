package com.kshavrin.mymoney.feature.settings.language

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes language settings back callback`() {
        var backed = false

        setContent(onBack = { backed = true })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(backed)
        }
    }

    @Test
    fun `language rows reflect selected state and emit language selection events`() {
        val emitted = mutableListOf<LanguageEvent>()

        setContent(
            state = LanguageState(selected = AppLanguage.Russian),
            onEvent = { event -> emitted += event },
        )

        selectableRow(R.string.language_ru).assertIsSelected()
        selectableRow(R.string.language_en).assertIsNotSelected()
        selectableRow(R.string.language_system).assertIsNotSelected()

        selectableRow(R.string.language_system).performClick()
        selectableRow(R.string.language_en).performClick()
        selectableRow(R.string.language_ru).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    LanguageEvent.LanguageSelected(AppLanguage.System),
                    LanguageEvent.LanguageSelected(AppLanguage.English),
                    LanguageEvent.LanguageSelected(AppLanguage.Russian),
                ),
                emitted,
            )
        }
    }

    private fun setContent(
        state: LanguageState = LanguageState(),
        onEvent: (LanguageEvent) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                LanguageContent(
                    state = state,
                    onEvent = onEvent,
                    onBack = onBack,
                )
            }
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun selectableRow(resourceId: Int) =
        composeTestRule.onNode(isSelectable() and hasText(targetString(resourceId)))
}
