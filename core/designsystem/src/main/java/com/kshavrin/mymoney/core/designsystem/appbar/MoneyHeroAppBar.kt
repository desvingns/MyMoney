package com.kshavrin.mymoney.core.designsystem.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardHeroGradientEnd
import com.kshavrin.mymoney.core.ui.theme.dashboardHeroGradientStart
import com.kshavrin.mymoney.core.ui.theme.dashboardTopBarSubtitle
import com.kshavrin.mymoney.core.ui.theme.dashboardTopBarTitle

const val MONEY_HERO_APP_BAR_TAG = "heroAppBar"
const val MONEY_HERO_APP_BAR_TITLE_TAG = "heroAppBarTitle"
const val MONEY_HERO_APP_BAR_SUBTITLE_TAG = "heroAppBarSubtitle"

@Composable
fun MoneyHeroAppBar(
    subtitle: String?,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleTestTag: String = MONEY_HERO_APP_BAR_TITLE_TAG,
    subtitleTestTag: String = MONEY_HERO_APP_BAR_SUBTITLE_TAG,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MONEY_HERO_APP_BAR_TAG)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.dashboardHeroGradientStart,
                        MaterialTheme.colorScheme.dashboardHeroGradientEnd,
                    ),
                ),
            )
            .statusBarsPadding()
            .height(Spacing.heroAppBarHeight)
            .padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        MoneyHeroAppBarTitle(
            title = stringResource(R.string.hero_app_bar_title),
            subtitle = subtitle,
            titleTestTag = titleTestTag,
            subtitleTestTag = subtitleTestTag,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.s),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            actions()
        }
    }
}

@Composable
private fun MoneyHeroAppBarTitle(
    title: String,
    subtitle: String?,
    titleTestTag: String,
    subtitleTestTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.dashboardTopBarTitle,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            modifier = Modifier.testTag(titleTestTag),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.dashboardTopBarSubtitle,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                modifier = Modifier.testTag(subtitleTestTag),
            )
        }
    }
}
