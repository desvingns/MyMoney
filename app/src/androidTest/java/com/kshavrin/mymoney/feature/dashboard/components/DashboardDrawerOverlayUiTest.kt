package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardDrawerOverlayUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `open overlay keeps the drawer panel near the configured 62 percent width`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(OVERLAY_ROOT_TAG),
                ) {
                    DashboardDrawerOverlay(
                        open = true,
                        side = DrawerSide.Left,
                        onDismiss = {},
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(DRAWER_PANEL_TAG),
                        )
                    }
                }
            }
        }

        val rootWidth = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.widthPixels.toFloat()
        val panelWidth = composeTestRule
            .onNodeWithTag(DRAWER_PANEL_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val ratio = panelWidth / rootWidth

        assertTrue("drawer width ratio $ratio must stay within [0.60, 0.68]", ratio in 0.60f..0.68f)
    }

    @Test
    fun `closed overlay does not render the drawer panel`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(OVERLAY_ROOT_TAG),
                ) {
                    DashboardDrawerOverlay(
                        open = false,
                        side = DrawerSide.Left,
                        onDismiss = {},
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(DRAWER_PANEL_TAG),
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(DRAWER_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun `overlay scrim exposes a click action for touch consumption`() {
        var dismissCount = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(OVERLAY_ROOT_TAG),
                ) {
                    DashboardDrawerOverlay(
                        open = true,
                        side = DrawerSide.Left,
                        onDismiss = { dismissCount += 1 },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(DRAWER_PANEL_TAG),
                        )
                    }
                }
            }
        }

        val rootBounds = composeTestRule
            .onNodeWithTag(OVERLAY_ROOT_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        composeTestRule
            .onNodeWithTag(OVERLAY_ROOT_TAG)
            .performTouchInput {
                click(Offset(rootBounds.width * 0.82f, rootBounds.height / 2f))
            }

        composeTestRule.runOnIdle {
            assertEquals(1, dismissCount)
        }
    }

    private companion object {
        const val OVERLAY_ROOT_TAG = "dashboard_drawer_overlay_root"
        const val DRAWER_PANEL_TAG = "drawer_panel"
    }
}
