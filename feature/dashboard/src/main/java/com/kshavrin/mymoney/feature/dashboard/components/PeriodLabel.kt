package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.feature.dashboard.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PeriodLabel(
    period: Period,
    modifier: Modifier = Modifier,
) {
    Text(
        text = period.localizedLabel(LocalConfiguration.current.locales[0]),
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Period.localizedLabel(locale: Locale): String = when (this) {
    is Period.Day -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    is Period.Week -> {
        val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
        "${weekStart.format(formatter)} \u2013 ${weekStart.plusDays(6).format(formatter)}"
    }
    is Period.Month -> yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    is Period.Year -> year.toString()
    Period.All -> stringResource(R.string.period_all)
    is Period.CustomRange -> {
        val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
        "${start.format(formatter)} \u2013 ${end.format(formatter)}"
    }
}
