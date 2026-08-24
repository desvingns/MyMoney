package com.kshavrin.mymoney.feature.support.googlesignin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.support.R
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
class GoogleSignInDialogContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun string(id: Int): String = context.getString(id)

    private fun setContent(
        status: GoogleSignInStatus,
        onSignIn: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoogleSignInDialogContent(
                    status = status,
                    onSignIn = onSignIn,
                    onCancel = onCancel,
                )
            }
        }
    }

    // --- Idle state ---

    @Test
    fun `idle state shows title and sign-in message`() {
        setContent(status = GoogleSignInStatus.Idle)

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_message)).assertIsDisplayed()
    }

    @Test
    fun `idle state shows enabled Continue with Google action button`() {
        setContent(status = GoogleSignInStatus.Idle)

        composeTestRule
            .onNodeWithText(string(R.string.support_google_sign_in_action))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `idle state shows Cancel button`() {
        setContent(status = GoogleSignInStatus.Idle)

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_cancel)).assertIsDisplayed()
    }

    // --- Loading state ---

    @Test
    fun `loading state shows signing-in progress text`() {
        setContent(status = GoogleSignInStatus.Loading)

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_loading)).assertIsDisplayed()
    }

    @Test
    fun `loading state disables the action button`() {
        setContent(status = GoogleSignInStatus.Loading)

        // Button text stays "Continue with Google" (not Error, so action string) but is disabled.
        composeTestRule
            .onNodeWithText(string(R.string.support_google_sign_in_action))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun `loading state still shows Cancel button`() {
        setContent(status = GoogleSignInStatus.Loading)

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_cancel)).assertIsDisplayed()
    }

    // --- Error state ---

    @Test
    fun `error state shows error message`() {
        setContent(status = GoogleSignInStatus.Error)

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_error)).assertIsDisplayed()
    }

    @Test
    fun `error state shows enabled Try again button`() {
        setContent(status = GoogleSignInStatus.Error)

        composeTestRule
            .onNodeWithText(string(R.string.support_google_sign_in_retry))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `error state still shows Cancel button`() {
        setContent(status = GoogleSignInStatus.Error)

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_cancel)).assertIsDisplayed()
    }

    // --- Interactions ---

    @Test
    fun `Cancel button fires onCancel in idle state`() {
        val events = mutableListOf<String>()
        setContent(status = GoogleSignInStatus.Idle, onCancel = { events.add("cancel") })

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_cancel)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf("cancel"), events)
        }
    }

    @Test
    fun `action button fires onSignIn in idle state`() {
        val events = mutableListOf<String>()
        setContent(status = GoogleSignInStatus.Idle, onSignIn = { events.add("sign-in") })

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_action)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf("sign-in"), events)
        }
    }

    @Test
    fun `Try again button fires onSignIn in error state`() {
        val events = mutableListOf<String>()
        setContent(status = GoogleSignInStatus.Error, onSignIn = { events.add("retry") })

        composeTestRule.onNodeWithText(string(R.string.support_google_sign_in_retry)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf("retry"), events)
        }
    }
}
