package com.kshavrin.mymoney.feature.lockscreen.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.lockscreen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricSetupContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes biometric setup back callback`() {
        var backed = false

        setContent(onBack = { backed = true })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.lock_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(backed)
        }
    }

    @Test
    fun `available enable switch emits enable event`() {
        val events = mutableListOf<BiometricSetupEvent>()

        setContent(onEvent = events::add)

        composeTestRule
            .onNodeWithText(targetString(R.string.biometric_enable_toggle))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(LOCK_SETUP_ENABLE_TAG)
            .assertIsEnabled()
            .assertIsOff()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(BiometricSetupEvent.ToggleChanged(true)), events)
        }
    }

    @Test
    fun `no biometric hardware disables toggle`() {
        setContent(
            state = BiometricSetupState(availability = BiometricAvailability.NoHardware),
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.biometric_unavailable))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(LOCK_SETUP_ENABLE_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun `not enrolled text opens system settings and disables toggle`() {
        var openedSettings = false

        setContent(
            state = BiometricSetupState(availability = BiometricAvailability.NotEnrolled),
            onOpenSystemSettings = { openedSettings = true },
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.biometric_enrol_required))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithTag(LOCK_SETUP_ENABLE_TAG)
            .assertIsNotEnabled()

        composeTestRule.runOnIdle {
            assertTrue(openedSettings)
        }
    }

    @Test
    fun `idle timeout row emits selected timeout`() {
        val events = mutableListOf<BiometricSetupEvent>()

        setContent(
            state = BiometricSetupState(enabled = true, idleTimeoutSec = 60),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.biometric_idle_timeout_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.biometric_idle_timeout_1m))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(LOCK_SETUP_IDLE_TIMEOUT_TAG)
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.biometric_idle_timeout_2m))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(BiometricSetupEvent.IdleTimeoutSelected(120)), events)
        }
    }

    @Test
    fun `pin setup keypad confirms four digits and supports backspace`() {
        val events = mutableListOf<BiometricSetupEvent>()

        setContent(
            state = BiometricSetupState(pinSetupVisible = true),
            onEvent = events::add,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.pin_setup_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.pin_setup_enter))
            .assertIsDisplayed()

        tapPinDigit(1)
        tapPinDigit(2)
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.lock_pin_backspace))
            .performClick()
        tapPinDigit(3)
        tapPinDigit(4)
        tapPinDigit(5)

        composeTestRule
            .onNodeWithText(targetString(R.string.pin_setup_confirm))
            .assertIsDisplayed()

        tapPinDigit(1)
        tapPinDigit(3)
        tapPinDigit(4)
        tapPinDigit(5)

        composeTestRule.runOnIdle {
            assertEquals(listOf(BiometricSetupEvent.PinEntered("1345")), events)
        }
    }

    private fun setContent(
        state: BiometricSetupState = BiometricSetupState(),
        onEvent: (BiometricSetupEvent) -> Unit = {},
        onBack: () -> Unit = {},
        onOpenSystemSettings: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                BiometricSetupContent(
                    state = state,
                    onEvent = onEvent,
                    onBack = onBack,
                    onOpenSystemSettings = onOpenSystemSettings,
                )
            }
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun tapPinDigit(digit: Int) {
        composeTestRule
            .onNodeWithText(digit.toString())
            .performClick()
    }

    private companion object {
        const val LOCK_SETUP_ENABLE_TAG = "lock_setup_enable"
        const val LOCK_SETUP_IDLE_TIMEOUT_TAG = "lock_setup_idle_timeout"
    }
}
