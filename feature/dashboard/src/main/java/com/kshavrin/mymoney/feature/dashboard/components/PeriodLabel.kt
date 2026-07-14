package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import java.time.format.FormatStyle
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
    onPeriodLabelClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Drag offset of the body pager in [-1, 1]: 0 at rest, positive as the user drags toward the
    // next period, negative toward the previous. Slides the title so it tracks the finger (G7).
    dragOffset: Float = 0f,
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
        val previousLabel = period.previous().localizedLabel(locale, allLabel, currentYear)
        val nextLabel = period.next().localizedLabel(locale, allLabel, currentYear)
        val pickDateLabel = stringResource(R.string.period_pick_a_date)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Spacing.dashboardPeriodUnderlineRadius))
                    .clickable(onClick = onPeriodLabelClick)
                    .semantics { contentDescription = pickDateLabel },
        ) {
            var titleWidthPx by remember { mutableStateOf(0) }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { titleWidthPx = it.width },
                contentAlignment = Alignment.Center,
            ) {
                // Center title tracks the drag; the incoming neighbour slides in from the side the
                // finger moves toward. When the offset is 0 only the center title is visible.
                val slide = -dragOffset * titleWidthPx
                AutoShrinkPeriodTitle(
                    text = label,
                    allowedLines = if (label.contains('\n')) 2 else 1,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = slide },
                )
                if (dragOffset > 0f) {
                    AutoShrinkPeriodTitle(
                        text = nextLabel,
                        allowedLines = if (nextLabel.contains('\n')) 2 else 1,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = slide + titleWidthPx },
                    )
                } else if (dragOffset < 0f) {
                    AutoShrinkPeriodTitle(
                        text = previousLabel,
                        allowedLines = if (previousLabel.contains('\n')) 2 else 1,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = slide - titleWidthPx },
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .width(Spacing.dashboardPeriodUnderlineWidth)
                        .height(Spacing.dashboardPeriodUnderlineHeight)
                        .clip(RoundedCornerShape(Spacing.dashboardPeriodUnderlineRadius))
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
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

// Auto-shrink for the top-bar period title. Compose Foundation 1.8 provides the
// built-in auto-size path, which avoids a state mutation from onTextLayout while
// measuring long titles. The text is never visually truncated (no Ellipsis) — at the floor it simply
// renders at the smallest readable size.
@Composable
private fun AutoShrinkPeriodTitle(
    text: String,
    allowedLines: Int,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.dashboardPeriodSelected
    BasicText(
        text = text,
        style =
            baseStyle.copy(
                color = MaterialTheme.colorScheme.dashboardPeriodSelectedText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = dashboardPeriodTitleMinSp,
                maxFontSize = baseStyle.fontSize,
                stepSize = 1.sp,
            ),
        maxLines = allowedLines,
        overflow = TextOverflow.Clip,
        softWrap = allowedLines > 1,
        modifier = modifier,
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
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    return when (this) {
        is Period.Day -> date.format(dateFormatter)
        is Period.Week ->
            "${weekStart.format(dateFormatter)}\n${weekStart.plusDays(6).format(dateFormatter)}"
        is Period.Month -> {
            val monthName = yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL", locale))
            if (yearMonth.year == currentYear) {
                monthName
            } else {
                "$monthName ${yearMonth.year}"
            }
        }
        is Period.Year -> year.toString()
        Period.All -> allLabel
        is Period.CustomRange ->
            "${start.format(dateFormatter)}\n${end.format(dateFormatter)}"
    }
}
