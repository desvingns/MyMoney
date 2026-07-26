package com.kshavrin.mymoney.feature.settings.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FactoryResetDialogContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        state: SettingsState,
        onEvent: (SettingsEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme(themeMode = ThemeMode.System) {
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

    @Test
    fun `factory reset entry is visible in the danger zone section`() {
        setContent(SettingsState())

        composeTestRule.onNodeWithText("Factory reset").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping factory reset entry emits FactoryResetRequested`() {
        var lastEvent: SettingsEvent? = null
        setContent(SettingsState(), onEvent = { lastEvent = it })

        composeTestRule.onNodeWithText("Factory reset").performScrollTo().performClick()

        assertEquals(SettingsEvent.FactoryResetRequested, lastEvent)
    }

    @Test
    fun `Confirm step shows the initial confirmation dialog with expected buttons`() {
        setContent(SettingsState(factoryResetStep = FactoryResetStep.Confirm))

        composeTestRule.onNodeWithText("Factory reset?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `Cancel on Confirm step emits FactoryResetDismissed`() {
        var lastEvent: SettingsEvent? = null
        setContent(
            SettingsState(factoryResetStep = FactoryResetStep.Confirm),
            onEvent = { lastEvent = it },
        )

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(SettingsEvent.FactoryResetDismissed, lastEvent)
    }

    @Test
    fun `Continue on Confirm step emits FactoryResetContinued`() {
        var lastEvent: SettingsEvent? = null
        setContent(
            SettingsState(factoryResetStep = FactoryResetStep.Confirm),
            onEvent = { lastEvent = it },
        )

        composeTestRule.onNodeWithText("Continue").performClick()

        assertEquals(SettingsEvent.FactoryResetContinued, lastEvent)
    }

    @Test
    fun `TypeWord step shows the typed-word confirm field`() {
        setContent(SettingsState(factoryResetStep = FactoryResetStep.TypeWord, factoryResetConfirmText = ""))

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_FIELD_TAG).assertIsDisplayed()
    }

    @Test
    fun `destructive button is disabled when confirm text is empty`() {
        setContent(SettingsState(factoryResetStep = FactoryResetStep.TypeWord, factoryResetConfirmText = ""))

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test
    fun `destructive button is disabled when confirm text is partial match`() {
        setContent(SettingsState(factoryResetStep = FactoryResetStep.TypeWord, factoryResetConfirmText = "RES"))

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test
    fun `destructive button is disabled when confirm text is lowercase reset`() {
        setContent(SettingsState(factoryResetStep = FactoryResetStep.TypeWord, factoryResetConfirmText = "reset"))

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test
    fun `destructive button is enabled when confirm text matches RESET exactly`() {
        setContent(
            SettingsState(
                factoryResetStep = FactoryResetStep.TypeWord,
                factoryResetConfirmText = FACTORY_RESET_CONFIRM_WORD,
            ),
        )

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun `confirm button emits FactoryResetConfirmed when text matches RESET`() {
        var lastEvent: SettingsEvent? = null
        setContent(
            SettingsState(
                factoryResetStep = FactoryResetStep.TypeWord,
                factoryResetConfirmText = FACTORY_RESET_CONFIRM_WORD,
            ),
            onEvent = { lastEvent = it },
        )

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_BUTTON_TAG).performClick()

        assertEquals(SettingsEvent.FactoryResetConfirmed, lastEvent)
    }

    @Test
    fun `Cancel on TypeWord step emits FactoryResetDismissed`() {
        var lastEvent: SettingsEvent? = null
        setContent(
            SettingsState(factoryResetStep = FactoryResetStep.TypeWord),
            onEvent = { lastEvent = it },
        )

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(SettingsEvent.FactoryResetDismissed, lastEvent)
    }

    @Test
    fun `destructive button is disabled while factory reset is in progress`() {
        setContent(
            SettingsState(
                factoryResetStep = FactoryResetStep.TypeWord,
                factoryResetConfirmText = FACTORY_RESET_CONFIRM_WORD,
                factoryResetInProgress = true,
            ),
        )

        composeTestRule.onNodeWithTag(FACTORY_RESET_CONFIRM_BUTTON_TAG).assertIsNotEnabled()
    }
}
