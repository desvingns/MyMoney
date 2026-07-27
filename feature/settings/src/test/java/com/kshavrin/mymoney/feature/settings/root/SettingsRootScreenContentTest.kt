package com.kshavrin.mymoney.feature.settings.root

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.GraphicsMode.Mode.NATIVE
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(NATIVE)
class SettingsRootScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `recents privacy switch is visible`() {
        setContent()

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_hide_content_in_recents))
            .assertIsDisplayed()
    }

    @Test
    fun `recents privacy switch reflects enabled state`() {
        setContent(SettingsState(hideAppContentInRecents = true))

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_hide_content_in_recents))
            .assertIsOn()
    }

    @Test
    fun `recents privacy switch reflects disabled state and emits the new value`() {
        var emitted: SettingsEvent? = null
        setContent(onEvent = { emitted = it })

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_hide_content_in_recents))
            .assertIsOff()
            .performClick()

        assertEquals(SettingsEvent.HideAppContentInRecentsToggled(true), emitted)
    }

    private fun setContent(
        state: SettingsState = SettingsState(),
        onEvent: (SettingsEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            MyMoneyTheme {
                SettingsRootContent(
                    state = state,
                    onEvent = onEvent,
                    onOpenTheme = {},
                    onOpenLanguage = {},
                    onOpenBackup = {},
                    onOpenAbout = {},
                    onOpenLicences = {},
                    onBack = {},
                )
            }
        }
    }

    private fun string(resourceId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resourceId)
}
