package com.kshavrin.mymoney.feature.settings.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
class AboutHelpContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes about help back callback`() {
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
    fun `about help rows render and invoke their action callbacks`() {
        val opened = mutableListOf<String>()

        setContent(
            versionName = "1.2.3",
            versionCode = 45,
            onOpenPrivacy = { opened += "privacy" },
            onOpenHelp = { opened += "help" },
            onOpenLicences = { opened += "licences" },
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.about_version, "1.2.3", 45))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.about_attribution))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(targetString(R.string.about_privacy))
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.about_help))
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.about_licences))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf("privacy", "help", "licences"), opened)
        }
    }

    private fun setContent(
        versionName: String = "1.0.0",
        versionCode: Int = 42,
        onOpenPrivacy: () -> Unit = {},
        onOpenHelp: () -> Unit = {},
        onOpenLicences: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                AboutHelpContent(
                    versionName = versionName,
                    versionCode = versionCode,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenHelp = onOpenHelp,
                    onOpenLicences = onOpenLicences,
                    onBack = onBack,
                )
            }
        }
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
