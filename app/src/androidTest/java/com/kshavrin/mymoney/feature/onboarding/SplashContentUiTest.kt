package com.kshavrin.mymoney.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashRendersLogo() {
        composeTestRule.setContent {
            MyMoneyTheme {
                SplashContent()
            }
        }

        composeTestRule
            .onNodeWithTag(SPLASH_LOGO_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.splash_logo_content_description))
            .assertDoesNotExist()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
