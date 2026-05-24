package com.kshavrin.mymoney.feature.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.feature.settings.R

@Composable
fun HelpScreen(onBack: () -> Unit) {
    AssetWebViewScreen(
        title = stringResource(R.string.about_help_title),
        assetBaseName = "help",
        onBack = onBack,
    )
}
