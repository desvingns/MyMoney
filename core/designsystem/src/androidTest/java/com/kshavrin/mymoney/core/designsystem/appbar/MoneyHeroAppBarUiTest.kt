package com.kshavrin.mymoney.core.designsystem.appbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyHeroAppBarUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun barIsPresentInTheCompositionWithTheHeroAppBarTestTag() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = null,
                    leading = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun titleTextFromResourcesIsRenderedInsideTheBar() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = null,
                    leading = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_TITLE_TAG)
            .assertIsDisplayed()

        val expectedTitle = targetString(R.string.hero_app_bar_title)
        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_TITLE_TAG)
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(
                "hero_app_bar_title string resource must match the constant in strings.xml",
                "MyMoney",
                expectedTitle,
            )
        }
    }

    @Test
    fun currencySubtitleIsDisplayedWhenProvided() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_SUBTITLE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun subtitleNodeIsAbsentWhenSubtitleIsNull() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = null,
                    leading = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_SUBTITLE_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun leadingSlotComposableIsRendered() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.testTag(LEADING_TAG),
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LEADING_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun clickOnTheLeadingSlotIconButtonFiresTheProvidedLambda() {
        var clicks = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {
                        IconButton(
                            onClick = { clicks++ },
                            modifier = Modifier.testTag(LEADING_TAG),
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LEADING_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun backArrowLeadingSlotIsRenderedAndFiresItsClickLambda() {
        var clicks = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {
                        IconButton(
                            onClick = { clicks++ },
                            modifier = Modifier.testTag(LEADING_BACK_TAG),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LEADING_BACK_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun trailingActionsSlotComposableIsRenderedAndClickable() {
        var clicks = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {},
                    actions = {
                        IconButton(
                            onClick = { clicks++ },
                            modifier = Modifier.testTag(TRAILING_TAG),
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TRAILING_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun barHeightIsAtLeastTheHeroAppBarHeightSpacingToken() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_TAG)
            .assertHeightIsAtLeast(Spacing.heroAppBarHeight)
    }

    @Test
    fun customTitleAndSubtitleTestTagsOverrideTheDefaults() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MoneyHeroAppBar(
                    subtitle = SUBTITLE_TEXT,
                    leading = {},
                    titleTestTag = CUSTOM_TITLE_TAG,
                    subtitleTestTag = CUSTOM_SUBTITLE_TAG,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(CUSTOM_TITLE_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(CUSTOM_SUBTITLE_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_TITLE_TAG)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(MONEY_HERO_APP_BAR_SUBTITLE_TAG)
            .assertDoesNotExist()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private companion object {
        const val SUBTITLE_TEXT = "RUB"
        const val LEADING_TAG = "test_leading_menu_icon"
        const val LEADING_BACK_TAG = "test_leading_back_icon"
        const val TRAILING_TAG = "test_trailing_action_icon"
        const val CUSTOM_TITLE_TAG = "custom_hero_title"
        const val CUSTOM_SUBTITLE_TAG = "custom_hero_subtitle"
    }
}
