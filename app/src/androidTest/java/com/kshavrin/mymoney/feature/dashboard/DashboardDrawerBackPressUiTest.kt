package com.kshavrin.mymoney.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardDrawerBackPressUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `system back dismisses the left drawer and closes both drawer flags`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val currentState = setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, leftDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.DrawerDismissed), capturedEvents)
            assertFalse(currentState().leftDrawerOpen)
            assertFalse(currentState().rightDrawerOpen)
        }
    }

    @Test
    fun `system back dismisses the right drawer and closes both drawer flags`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val currentState = setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        pressBack()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.DrawerDismissed), capturedEvents)
            assertFalse(currentState().leftDrawerOpen)
            assertFalse(currentState().rightDrawerOpen)
        }
    }

    private fun setStatefulDashboardContent(
        initialState: DashboardState,
        onCapturedEvent: (DashboardEvent) -> Unit = {},
    ): () -> DashboardState {
        var state by mutableStateOf(initialState)
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = state,
                    onEvent = { event ->
                        onCapturedEvent(event)
                        state = when (event) {
                            DashboardEvent.LeftDrawerToggled -> state.copy(
                                leftDrawerOpen = !state.leftDrawerOpen,
                                rightDrawerOpen = false,
                            )
                            DashboardEvent.RightDrawerToggled -> state.copy(
                                rightDrawerOpen = !state.rightDrawerOpen,
                                leftDrawerOpen = false,
                            )
                            DashboardEvent.DrawerDismissed -> state.copy(
                                leftDrawerOpen = false,
                                rightDrawerOpen = false,
                            )
                            else -> state
                        }
                    },
                )
            }
        }
        return { state }
    }
}
