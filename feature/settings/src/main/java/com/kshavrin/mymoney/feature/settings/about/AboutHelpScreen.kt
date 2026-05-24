package com.kshavrin.mymoney.feature.settings.about

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kshavrin.mymoney.feature.settings.R

@Composable
fun AboutHelpRoute(
    versionName: String,
    versionCode: Int,
    onOpenPrivacy: () -> Unit,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    AboutHelpContent(
        versionName = versionName,
        versionCode = versionCode,
        onOpenPrivacy = onOpenPrivacy,
        onOpenHelp = onOpenHelp,
        onOpenLicences = {
            context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutHelpContent(
    versionName: String,
    versionCode: Int,
    onOpenPrivacy: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.about_version, versionName, versionCode))
                },
                supportingContent = { Text(stringResource(R.string.about_attribution)) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_privacy)) },
                modifier = Modifier.clickable(onClick = onOpenPrivacy),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_help)) },
                modifier = Modifier.clickable(onClick = onOpenHelp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_licences)) },
                modifier = Modifier.clickable(onClick = onOpenLicences),
            )
        }
    }
}
