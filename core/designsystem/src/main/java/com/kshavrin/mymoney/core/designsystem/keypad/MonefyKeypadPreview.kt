package com.kshavrin.mymoney.core.designsystem.keypad

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing

internal const val KEYPAD_PREVIEW_WIDTH_DP = 320

@Composable
internal fun MonefyKeypadSample(modifier: Modifier = Modifier) {
    MonefyKeypad(onEvent = {}, modifier = modifier)
}

@Preview(name = "Keypad · Light", showBackground = true, widthDp = KEYPAD_PREVIEW_WIDTH_DP)
@Preview(name = "Keypad · Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES, widthDp = KEYPAD_PREVIEW_WIDTH_DP)
@Composable
private fun MonefyKeypadPreview() {
    MyMoneyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MonefyKeypadSample(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.m),
            )
        }
    }
}
