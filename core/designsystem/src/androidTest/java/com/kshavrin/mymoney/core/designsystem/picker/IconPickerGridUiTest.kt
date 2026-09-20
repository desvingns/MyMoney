package com.kshavrin.mymoney.core.designsystem.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconPickerGridUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun iconPickerGrid_pinsFourColumnSelectionAndClickContract() {
        val iconKeys = List(8) { index -> "icon_$index" }
        val selectedIconKey = iconKeys.first()
        val emittedKeys = mutableListOf<String>()

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 240.dp)) {
                    IconPickerGrid(
                        iconKeys = iconKeys,
                        selectedIconKey = selectedIconKey,
                        iconFor = ::categoryIcon,
                        onIconSelected = { emittedKeys += it },
                        modifier = Modifier.testTag(GRID_TAG),
                        iconContentDescription = { key -> "Choose $key" },
                    )
                }
            }
        }

        val collectionInfo =
            composeTestRule
                .onNodeWithTag(GRID_TAG)
                .fetchSemanticsNode()
                .config[SemanticsProperties.CollectionInfo]
        assertEquals(4, collectionInfo.columnCount)

        iconKeys.forEach { key ->
            composeTestRule.onNodeWithTag(key).assertIsDisplayed()
        }

        val firstRowBounds = iconKeys.take(4).map(::boundsFor)
        val secondRowBounds = iconKeys.drop(4).map(::boundsFor)
        assertEquals(1, firstRowBounds.map { it.top }.distinct().size)
        assertEquals(1, secondRowBounds.map { it.top }.distinct().size)
        assertTrue(secondRowBounds.first().top > firstRowBounds.first().top)
        assertTrue(firstRowBounds.zipWithNext().all { (left, right) -> right.left > left.left })

        composeTestRule
            .onNodeWithTag(selectedIconKey)
            .assertIsSelected()
            .assertWidthIsEqualTo(Spacing.wizardIconPickerTileSize)
            .assertHeightIsEqualTo(Spacing.wizardIconPickerTileSize)
        composeTestRule.onNodeWithTag(iconKeys[1]).assertIsNotSelected()

        composeTestRule.onNodeWithTag(iconKeys[5]).performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(iconKeys[5]), emittedKeys)
        }
    }

    private fun boundsFor(key: String) =
        composeTestRule.onNodeWithTag(key).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val GRID_TAG = "icon_picker_grid"
    }
}
