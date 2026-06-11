package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.lockscreen.R
import com.kshavrin.mymoney.feature.lockscreen.setup.PinHasher
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        setOverlayContent(onUnlocked = { unlockCount++ })
        dispatchNegativeButtonFallback()

        composeRule
            .onNodeWithText(stringRes(R.string.lock_pin_prompt))
            .assertIsDisplayed()

        tapPinDigit(1)
        tapPinDigit(2)
        tapPinDigit(3)
        tapPinDigit(4)

        composeRule.runOnIdle {
            assertEquals(1, unlockCount)
        }
    }

    @Test
    fun `legacy pinless fallback shows retry biometric instead of dead keypad`() {
        secureStorage.writePinHash(null)

        setOverlayContent()
        dispatchAuthenticationError(androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED)

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

        restorationTester.setContent {
            MyMoneyTheme {
                LockOverlay(onUnlocked = {})
            }
        }

        dispatchNegativeButtonFallback()
        assertPinFallbackVisible()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertPinFallbackVisible()
    }

    private fun setOverlayContent(
        onUnlocked: () -> Unit = {},
    ) {
        composeRule.setContent {
            MyMoneyTheme {
                LockOverlay(onUnlocked = onUnlocked)
            }
        }
    }

    private fun assertPinFallbackVisible() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(stringRes(R.string.lock_pin_prompt))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithText(stringRes(R.string.lock_pin_prompt))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("1")
            .assertIsDisplayed()
    }

    private fun dispatchNegativeButtonFallback() {
        waitForBiometricFragment()
        composeRule.activity.runOnUiThread {
            val viewModel = biometricViewModel()
            invokeBiometricViewModelBoolean(viewModel, "setNegativeButtonPressPending", true)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(stringRes(R.string.lock_pin_prompt))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dispatchAuthenticationError(errorCode: Int) {
        waitForBiometricFragment()
        composeRule.activity.runOnUiThread {
            val viewModel = biometricViewModel()
            val errorDataClass = Class.forName("androidx.biometric.BiometricErrorData")
            val constructor = errorDataClass.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                CharSequence::class.java,
            )
            constructor.isAccessible = true
            val errorData = constructor.newInstance(errorCode, "test")
            val method = viewModel.javaClass.getDeclaredMethod("setAuthenticationError", errorDataClass)
            method.isAccessible = true
            method.invoke(viewModel, errorData)
        }
        composeRule.waitForIdle()
    }

    private fun waitForBiometricFragment() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.supportFragmentManager.findFragmentByTag(BIOMETRIC_FRAGMENT_TAG) != null
        }
    }

    private fun biometricViewModel(): Any {
        val provider = ViewModelProvider(composeRule.activity)
        val viewModelClass = Class.forName("androidx.biometric.BiometricViewModel")
        return ViewModelProvider::class.java.getMethod("get", Class::class.java)
            .invoke(provider, viewModelClass)
            ?: error("BiometricViewModel was not created")
    }

    private fun invokeBiometricViewModelBoolean(viewModel: Any, methodName: String, value: Boolean) {
        val method = viewModel.javaClass.getDeclaredMethod(
            methodName,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(viewModel, value)
    }

    private fun stringRes(resourceId: Int): String = composeRule.activity.getString(resourceId)

    private fun tapPinDigit(digit: Int) {
        composeRule
            .onNodeWithText(digit.toString())
            .performClick()
    }

    private companion object {
        const val BIOMETRIC_FRAGMENT_TAG = "androidx.biometric.BiometricFragment"
    }
}

@AndroidEntryPoint
class LockOverlayTestActivity : FragmentActivity()
