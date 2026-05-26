package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeToDeleteUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `swiping a transaction row invokes delete once`() {
        var deleteCalls = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                SwipeToDelete(
                    onDelete = { deleteCalls += 1 },
                    modifier = Modifier
                        .width(320.dp)
                        .height(72.dp),
                ) {
                    Text(
                        text = "Coffee",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Coffee").performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { deleteCalls == 1 }

        assertEquals(1, deleteCalls)
    }
}
