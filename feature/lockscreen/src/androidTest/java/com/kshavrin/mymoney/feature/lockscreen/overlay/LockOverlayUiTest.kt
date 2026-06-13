package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.lockscreen.R
import com.kshavrin.mymoney.feature.lockscreen.setup.PinHasher
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LockOverlayUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<LockOverlayTestActivity>()

    @Inject
    lateinit var secureStorage: SecureStorage

    private val pinHasher = PinHasher()

    @Before
    fun setUp() {
        hiltRule.inject()
        secureStorage.clearAll()
    }

    @Test
    fun `biometric negative button fallback enters pin path when a pin exists`() {
        secureStorage.writePinHash(pinHasher.hash("1234"))
        var unlockCount = 0

        setOverlayContent(
            onUnlocked = { unlockCount++ },
            launchBiometric = { _, _, _, _, _, _, onPinFallback -> onPinFallback() },
        )

        composeRule
            .onNodeWithText(stringRes(R.string.lock_pin_prompt))
            .assertIsDisplayed()

        tapPinDigit(1)
        tapPinDigit(2)
        tapPinDigit(3)
        tapPinDigit(4)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            unlockCount == 1
        }
        composeRule.runOnIdle {
            assertEquals(1, unlockCount)
        }
    }

    @Test
    fun `legacy pinless fallback shows retry biometric instead of dead keypad`() {
        secureStorage.writePinHash(null)

        setOverlayContent(
            launchBiometric = { _, _, _, _, _, _, onPinFallback -> onPinFallback() },
        )

        composeRule
            .onNodeWithText(stringRes(R.string.lock_pin_unavailable))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(stringRes(R.string.lock_retry_biometric))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(stringRes(R.string.lock_pin_backspace))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText("1")
            .assertDoesNotExist()
    }

    @Test
    fun `pin fallback survives saveable restoration in fragment activity context`() {
        secureStorage.writePinHash(pinHasher.hash("1234"))
        val restorationTester = StateRestorationTester(composeRule)
        var launchCount = 0

        restorationTester.setContent {
            MyMoneyTheme {
                LockOverlay(
                    onUnlocked = {},
                    launchBiometric = { _, _, _, _, _, _, onPinFallback ->
                        launchCount++
                        onPinFallback()
                    },
                )
            }
        }

        assertPinFallbackVisible()
        composeRule.runOnIdle {
            assertEquals(1, launchCount)
        }

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertPinFallbackVisible()
        composeRule.runOnIdle {
            assertEquals(1, launchCount)
        }
    }

    private fun setOverlayContent(
        onUnlocked: () -> Unit = {},
        launchBiometric: (
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            cancel: String,
            onSuccess: () -> Unit,
            onLockout: () -> Unit,
            onPinFallback: () -> Unit,
        ) -> Unit,
    ) {
        composeRule.setContent {
            MyMoneyTheme {
                LockOverlay(
                    onUnlocked = onUnlocked,
                    launchBiometric = launchBiometric,
                )
            }
        }
    }

    private fun assertPinFallbackVisible() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(stringRes(R.string.lock_pin_prompt))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithText(stringRes(R.string.lock_pin_prompt))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("1")
            .assertIsDisplayed()
    }

    private fun stringRes(resourceId: Int): String = composeRule.activity.getString(resourceId)

    private fun tapPinDigit(digit: Int) {
        composeRule
            .onNodeWithText(digit.toString())
            .performClick()
    }
}

@AndroidEntryPoint
class LockOverlayTestActivity : FragmentActivity()
