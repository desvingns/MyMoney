package com.kshavrin.mymoney.feature.onboarding

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.DarkColors
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping skip from the first slide invokes completion once`() {
        var completionCalls = 0

        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()

            MyMoneyTheme {
                OnboardingContent(
                    pagerState = pagerState,
                    currentPage = 0,
                    coroutineScope = coroutineScope,
                    onGetStarted = { completionCalls += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.onboarding_next)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.onboarding_skip)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, completionCalls)
        }
    }

    @Test
    fun `tapping next from the first slide advances without completing onboarding`() {
        var completionCalls = 0

        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()

            MyMoneyTheme {
                OnboardingContent(
                    pagerState = pagerState,
                    currentPage = pagerState.currentPage,
                    coroutineScope = coroutineScope,
                    onGetStarted = { completionCalls += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.onboarding_next)).performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.onboarding_slide_2_headline))
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(0, completionCalls)
        }
    }

    @Test
    fun `pager indicator describes the selected page after tapping next`() {
        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()

            MyMoneyTheme {
                OnboardingContent(
                    pagerState = pagerState,
                    currentPage = pagerState.currentPage,
                    coroutineScope = coroutineScope,
                    onGetStarted = {},
                )
            }
        }

        val indicatorDescription = targetString(R.string.onboarding_pager_indicator_description)
        composeTestRule
            .onNodeWithContentDescription(indicatorDescription)
            .assert(hasStateDescription(targetString(R.string.onboarding_pager_indicator_state, 1, 4)))

        composeTestRule.onNodeWithText(targetString(R.string.onboarding_next)).performClick()

        composeTestRule
            .onNodeWithContentDescription(indicatorDescription)
            .assert(hasStateDescription(targetString(R.string.onboarding_pager_indicator_state, 2, 4)))
    }

    @Test
    fun `swiping from the first slide advances without completing onboarding`() {
        var completionCalls = 0

        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()

            MyMoneyTheme {
                OnboardingContent(
                    pagerState = pagerState,
                    currentPage = pagerState.currentPage,
                    coroutineScope = coroutineScope,
                    onGetStarted = { completionCalls += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.onboarding_slide_1_body))
            .performTouchInput { swipeLeft() }
        composeTestRule
            .onNodeWithText(targetString(R.string.onboarding_slide_2_headline))
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(0, completionCalls)
        }
    }

    @Test
    fun `tapping get started from the fourth slide invokes completion once`() {
        var completionCalls = 0

        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 3, pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()

            MyMoneyTheme {
                OnboardingContent(
                    pagerState = pagerState,
                    currentPage = pagerState.currentPage,
                    coroutineScope = coroutineScope,
                    onGetStarted = { completionCalls += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.onboarding_get_started))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, completionCalls)
        }
    }

    @Test
    fun `first slide headline and body use readable onBackground text on the dark surface`() {
        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()

            MyMoneyTheme {
                OnboardingContent(
                    pagerState = pagerState,
                    currentPage = pagerState.currentPage,
                    coroutineScope = coroutineScope,
                    onGetStarted = {},
                )
            }
        }

        val headlineColor =
            composeTestRule
                .onNodeWithText(targetString(R.string.onboarding_slide_1_headline))
                .textLayout()
                .layoutInput
                .style
                .color
        val bodyColor =
            composeTestRule
                .onNodeWithText(targetString(R.string.onboarding_slide_1_body))
                .textLayout()
                .layoutInput
                .style
                .color

        assertEquals(DarkColors.onBackground, headlineColor)
        assertEquals(DarkColors.onBackground, bodyColor)
        assertTrue(contrastRatio(headlineColor, DarkColors.background) >= 4.5f)
        assertTrue(contrastRatio(bodyColor, DarkColors.background) >= 4.5f)
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
