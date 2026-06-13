package com.kshavrin.mymoney.feature.settings.about

import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.kshavrin.mymoney.feature.settings.R

internal fun assetSuffix(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (!locales.isEmpty && locales[0]?.language == "ru") "_ru" else "_en"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssetWebViewScreen(
    title: String,
    assetBaseName: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    loadUrl("file:///android_asset/$assetBaseName${assetSuffix()}.html")
                }
            },
        )
    }
}
