package com.kshavrin.mymoney.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme

// Robolectric API level the screenshots are rendered against — pinned so the render is stable and
// reproducible regardless of the host SDK. Combined with RobolectricDeviceQualifiers.Pixel5 (fixed
// size, density, and locale) and seeded composable data, every recording is deterministic.
internal const val ROBORAZZI_SDK = 34

internal const val SCREENSHOT_ROOT_TAG = "screenshot_root"

// Golden baselines live in VCS next to the test sources; the Roborazzi verify/record tasks resolve
// this path against the module directory. Diff/actual artefacts land under build/ (git-ignored).
// Re-record baselines: `./gradlew :core:designsystem:recordRoborazziDebug`.
// Opt-in verify (mp-runner screenshot hook): `./gradlew :core:designsystem:verifyRoborazziDebug`.
internal const val SCREENSHOT_DIR = "src/test/screenshots"

// Renders [content] inside the real app theme over the app background and writes the golden PNG.
// [darkTheme] is threaded through MyMoneyTheme so both theme entry points are covered; the app
// currently maps both to the neon dark palette (see core/ui Theme.kt), so the light and dark
// baselines are identical today and diverge automatically the day a distinct light palette lands.
internal fun ComposeContentTestRule.captureThemed(
    name: String,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    setContent {
        MyMoneyTheme(darkTheme = darkTheme) {
            Box(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .testTag(SCREENSHOT_ROOT_TAG),
            ) {
                content()
            }
        }
    }
    onNodeWithTag(SCREENSHOT_ROOT_TAG).captureRoboImage("$SCREENSHOT_DIR/$name.png")
}
