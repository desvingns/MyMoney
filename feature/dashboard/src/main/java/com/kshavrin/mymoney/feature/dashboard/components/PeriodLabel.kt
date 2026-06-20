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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodIndicator
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodSelected
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodSelectedText
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodTitleMinSp
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodUnselected
import com.kshavrin.mymoney.core.ui.theme.dashboardPeriodUnselectedText
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.Year
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
    val currentYear = Year.now().value
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
            text = period.previous().localizedLabel(locale, allLabel, currentYear),
            style = MaterialTheme.typography.dashboardPeriodUnselected,
            color = MaterialTheme.colorScheme.dashboardPeriodUnselectedText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f, fill = false),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = period.localizedLabel(locale, allLabel, currentYear),
                style = MaterialTheme.typography.dashboardPeriodSelected,
                color = MaterialTheme.colorScheme.dashboardPeriodSelectedText,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
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
            text = period.next().localizedLabel(locale, allLabel, currentYear),
            style = MaterialTheme.typography.dashboardPeriodUnselected,
            color = MaterialTheme.colorScheme.dashboardPeriodUnselectedText,
            textAlign = TextAlign.Center,
            maxLines = 2,
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
    val currentYear = Year.now().value
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.period_previous),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
            )
        }
        val label = period.localizedLabel(locale, allLabel, currentYear)
        AutoShrinkPeriodTitle(
            text = label,
            allowedLines = if (label.contains('\n')) 2 else 1,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        IconButton(onClick = onNextClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.period_next),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
            )
        }
    }
}

// Manual auto-shrink for the top-bar period title.  Compose BoM 2024.11
// (Foundation 1.7.5) has no BasicText(autoSize=...), so we measure-and-shrink:
// start at dashboardPeriodSelected.fontSize (22sp) and step down by 1sp while the
// laid-out text either overflows the available width (didOverflowWidth) or exceeds
// the allowed line count, never dropping below dashboardPeriodTitleMinSp (14sp).
// The text is never visually truncated (no Ellipsis) — at the floor it simply
// renders at the smallest readable size.
@Composable
private fun AutoShrinkPeriodTitle(
    text: String,
    allowedLines: Int,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.dashboardPeriodSelected
    val maxFontSize = baseStyle.fontSize
    val minFontSize = dashboardPeriodTitleMinSp
    var fontSize by remember(text, allowedLines) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        style = baseStyle.copy(fontSize = fontSize),
        color = MaterialTheme.colorScheme.dashboardPeriodSelectedText,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = allowedLines,
        overflow = TextOverflow.Clip,
        softWrap = allowedLines > 1,
        modifier = modifier,
        onTextLayout = { result ->
            val overflows = result.didOverflowWidth || result.lineCount > allowedLines
            if (overflows && fontSize > minFontSize) {
                fontSize = shrinkOneStep(fontSize, minFontSize)
            }
        },
    )
}

private fun shrinkOneStep(
    current: TextUnit,
    floor: TextUnit,
): TextUnit {
    val next = (current.value - 1f).sp
    return if (next.value < floor.value) floor else next
}

internal fun Period.localizedLabel(
    locale: Locale,
    allLabel: String,
    currentYear: Int,
): String {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy", locale)
    return when (this) {
        is Period.Day -> date.format(dateFormatter)
        is Period.Week ->
            "${weekStart.format(dateFormatter)}\n${weekStart.plusDays(6).format(dateFormatter)}"
        is Period.Month -> {
            val monthName = yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL", locale))
            if (yearMonth.year == currentYear) {
                monthName
            } else {
                "$monthName\n${yearMonth.year}"
            }
        }
        is Period.Year -> year.toString()
        Period.All -> allLabel
        is Period.CustomRange ->
            "${start.format(dateFormatter)}\n${end.format(dateFormatter)}"
    }
}
