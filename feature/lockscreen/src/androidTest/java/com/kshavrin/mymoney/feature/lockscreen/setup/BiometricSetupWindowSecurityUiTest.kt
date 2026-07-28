package com.kshavrin.mymoney.feature.lockscreen.setup

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.window.LocalSecureWindowController
import com.kshavrin.mymoney.core.ui.window.SecureWindowController
import com.kshavrin.mymoney.core.ui.window.SecureWindowSource
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockOverlayTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BiometricSetupWindowSecurityUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<LockOverlayTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun biometricSetupSetsSecureFlagWhenAppContentSourceIsDisabled() {
        val controller = SecureWindowController(composeRule.activity.window)
        controller.setSecure(SecureWindowSource.AppContent, enabled = false)

        composeRule.setContent {
            CompositionLocalProvider(LocalSecureWindowController provides controller) {
                MyMoneyTheme {
                    BiometricSetupRoute(onBack = {})
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val flags = composeRule.activity.window.attributes.flags
            assertTrue(flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }
}
