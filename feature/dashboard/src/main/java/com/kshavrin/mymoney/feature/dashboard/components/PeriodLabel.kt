package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PeriodLabel(
    period: Period,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val allLabel = stringResource(R.string.period_all)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = period.previous().localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = period.localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = period.next().localizedLabel(locale, allLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun Period.localizedLabel(locale: Locale, allLabel: String): String = when (this) {
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
