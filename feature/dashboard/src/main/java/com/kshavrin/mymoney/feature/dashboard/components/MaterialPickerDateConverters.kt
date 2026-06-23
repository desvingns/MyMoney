package com.kshavrin.mymoney.feature.dashboard.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun materialPickerUtcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun localDateToMaterialPickerUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
