package com.kshavrin.mymoney.core.designsystem.keypad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.core.designsystem.test.assertTouchWidthIsAtLeast
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeypadA11yUiTest {
    @get:Rule
    val composeTestRule = createComposeRule().apply { enableAccessibilityChecks() }

    @Test
    fun everyCalculatorKeyHasA48dpTouchTarget() {
        composeTestRule.setContent {
            MyMoneyTheme { Keypad(onEvent = {}) }
        }

        (('0'..'9').map(Char::toString) + ".").forEach { label ->
            composeTestRule
                .onNodeWithText(label)
                .assertTouchWidthIsAtLeast(48.dp)
                .assertTouchHeightIsAtLeast(48.dp)
        }

        listOf(
            R.string.keypad_op_plus_cd,
            R.string.keypad_op_minus_cd,
            R.string.keypad_op_multiply_cd,
            R.string.keypad_op_divide_cd,
            R.string.keypad_op_equals_cd,
        ).forEach { resourceId ->
            composeTestRule
                .onNodeWithContentDescription(str(resourceId))
                .assertTouchWidthIsAtLeast(48.dp)
                .assertTouchHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun minusOperatorKeyExposesContentDescriptionFromStringResources() {
        val expectedCd = str(R.string.keypad_op_minus_cd)
        val events = mutableListOf<KeypadEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = { events += it })
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .assertIsDisplayed()
    }

    @Test
    fun multiplyOperatorKeyExposesContentDescriptionFromStringResources() {
        val expectedCd = str(R.string.keypad_op_multiply_cd)

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .assertIsDisplayed()
    }

    @Test
    fun divideOperatorKeyExposesContentDescriptionFromStringResources() {
        val expectedCd = str(R.string.keypad_op_divide_cd)

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .assertIsDisplayed()
    }

    @Test
    fun equalsOperatorKeyExposesContentDescriptionFromStringResources() {
        val expectedCd = str(R.string.keypad_op_equals_cd)

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .assertIsDisplayed()
    }

    @Test
    fun plusOperatorKeyExposesContentDescriptionFromStringResources() {
        val expectedCd = str(R.string.keypad_op_plus_cd)

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .assertIsDisplayed()
    }

    @Test
    fun tappingTheMinusKeyByContentDescriptionFiresMinusOperatorEvent() {
        val expectedCd = str(R.string.keypad_op_minus_cd)
        val events = mutableListOf<KeypadEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = { events += it })
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, events.size)
            assertEquals(KeypadEvent.Op(Operator.Minus), events.single())
        }
    }

    @Test
    fun tappingTheMultiplyKeyByContentDescriptionFiresMultiplyOperatorEvent() {
        val expectedCd = str(R.string.keypad_op_multiply_cd)
        val events = mutableListOf<KeypadEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = { events += it })
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, events.size)
            assertEquals(KeypadEvent.Op(Operator.Multiply), events.single())
        }
    }

    @Test
    fun tappingTheDivideKeyByContentDescriptionFiresDivideOperatorEvent() {
        val expectedCd = str(R.string.keypad_op_divide_cd)
        val events = mutableListOf<KeypadEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = { events += it })
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, events.size)
            assertEquals(KeypadEvent.Op(Operator.Divide), events.single())
        }
    }

    @Test
    fun tappingTheEqualsKeyByContentDescriptionFiresEqualsEvent() {
        val expectedCd = str(R.string.keypad_op_equals_cd)
        val events = mutableListOf<KeypadEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = { events += it })
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedCd)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, events.size)
            assertEquals(KeypadEvent.Equals, events.single())
        }
    }

    @Test
    fun allFourOperatorKeysHaveDistinctNonEmptyContentDescriptions() {
        val minusCd = str(R.string.keypad_op_minus_cd)
        val multiplyCd = str(R.string.keypad_op_multiply_cd)
        val divideCd = str(R.string.keypad_op_divide_cd)
        val equalsCd = str(R.string.keypad_op_equals_cd)

        composeTestRule.setContent {
            MyMoneyTheme {
                Keypad(onEvent = {})
            }
        }

        val descriptions = listOf(minusCd, multiplyCd, divideCd, equalsCd)

        composeTestRule.runOnIdle {
            assertTrue(
                "all operator content descriptions must be non-empty strings",
                descriptions.all { it.isNotBlank() },
            )
            assertEquals(
                "operator content descriptions must all be distinct",
                descriptions.size,
                descriptions.distinct().size,
            )
        }
    }

    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
}
