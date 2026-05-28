package com.kshavrin.mymoney.feature.settings.theme

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
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes theme settings back callback`() {
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
    fun `theme mode rows reflect selected state and emit mode selection events`() {
        val emitted = mutableListOf<ThemeSettingsEvent>()

        setContent(
            state = ThemeSettingsState(selected = ThemeMode.Dark),
            onEvent = { event -> emitted += event },
        )

        selectableRow(R.string.theme_dark).assertIsSelected()
        selectableRow(R.string.theme_light).assertIsNotSelected()
        selectableRow(R.string.theme_system).assertIsNotSelected()

        selectableRow(R.string.theme_system).performClick()
        selectableRow(R.string.theme_light).performClick()
        selectableRow(R.string.theme_dark).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    ThemeSettingsEvent.ModeSelected(ThemeMode.System),
                    ThemeSettingsEvent.ModeSelected(ThemeMode.Light),
                    ThemeSettingsEvent.ModeSelected(ThemeMode.Dark),
                ),
                emitted,
            )
        }
    }

    private fun setContent(
        state: ThemeSettingsState = ThemeSettingsState(),
        onEvent: (ThemeSettingsEvent) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                ThemeSettingsContent(
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
