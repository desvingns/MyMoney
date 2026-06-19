package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodIndicator
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodSelected
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodSelectedText
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodUnselected
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodUnselectedText
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PeriodLabel(
    period: Period,
    modifier: Modifier = Modifier,
    onPreviousClick: (() -> Unit)? = null,
    onNextClick: (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val allLabel = stringResource(R.string.period_all)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPreviousClick?.invoke() }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.period_previous),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = period.previous().localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.dashboardPeriodUnselected,
            color = MaterialTheme.colorScheme.dashboardPeriodUnselectedText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f, fill = false),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = period.localizedLabel(locale, allLabel),
                style = MaterialTheme.typography.dashboardPeriodSelected,
                color = MaterialTheme.colorScheme.dashboardPeriodSelectedText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier =
                    Modifier
                        .width(Spacing.dashboardPeriodIndicatorWidth)
                        .height(Spacing.dashboardPeriodIndicatorHeight)
                        .clip(MaterialTheme.shapes.dashboardPeriodIndicator)
                        .background(MaterialTheme.colorScheme.dashboardPeriodIndicator),
            )
        }
        Text(
            text = period.next().localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.dashboardPeriodUnselected,
            color = MaterialTheme.colorScheme.dashboardPeriodUnselectedText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .alpha(0f),
        )
        IconButton(onClick = { onNextClick?.invoke() }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.period_next),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun PeriodSwitcher(
    period: Period,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val allLabel = stringResource(R.string.period_all)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.period_previous),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(Spacing.xxl),
            )
        }
        Text(
            text = period.localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.dashboardPeriodSelected,
            color = MaterialTheme.colorScheme.dashboardPeriodSelectedText,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNextClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.period_next),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(Spacing.xxl),
            )
        }
    }
}

private fun Period.localizedLabel(
    locale: Locale,
    allLabel: String,
): String =
    when (this) {
        is Period.Day -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        is Period.Week -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
            "${weekStart.format(formatter)} – ${weekStart.plusDays(6).format(formatter)}"
        }
        is Period.Month -> yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        is Period.Year -> year.toString()
        Period.All -> allLabel
        is Period.CustomRange -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
            "${start.format(formatter)} – ${end.format(formatter)}"
        }
    }
