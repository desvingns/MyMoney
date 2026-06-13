package com.kshavrin.mymoney.feature.dictionaries.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconPickerSheetUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `picker cells resolve vectors and expose each key once`() {
        val iconKeys = listOf("ic_cat_food", "ic_cat_bills", "ic_cat_salary")
        val resolvedKeys = linkedSetOf<String>()

        composeTestRule.setContent {
            MyMoneyTheme {
                IconPickerSheet(
                    iconKeys = iconKeys,
                    selectedIconKey = "ic_cat_food",
                    iconFor = { key ->
                        resolvedKeys += key
                        Icons.Filled.Add
                    },
                    onIconSelected = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_choose_icon)).assertIsDisplayed()
        iconKeys.forEach { key ->
            composeTestRule.onAllNodesWithContentDescription(key, useUnmergedTree = true).assertCountEquals(1)
            composeTestRule.onNodeWithContentDescription(key).assertIsDisplayed()
        }

        composeTestRule.runOnIdle {
            assertEquals(iconKeys, resolvedKeys.toList())
        }
    }

    @Test
    fun `picker cell click emits its key`() {
        val selectedKeys = mutableListOf<String>()

        composeTestRule.setContent {
            MyMoneyTheme {
                IconPickerSheet(
                    iconKeys = listOf("ic_account_cash", "ic_account_bank"),
                    selectedIconKey = "ic_account_cash",
                    iconFor = { Icons.Filled.Add },
                    onIconSelected = { selectedKeys += it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("ic_account_bank").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf("ic_account_bank"), selectedKeys)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
