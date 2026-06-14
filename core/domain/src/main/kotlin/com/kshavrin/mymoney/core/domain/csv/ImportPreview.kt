package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.CategoryKind
import java.time.LocalDate

data class PreviewCategory(
    val name: String,
    val kind: CategoryKind,
)

data class PreviewAccount(
    val name: String,
    val currencyCode: String,
)

data class PreviewDateRange(
    val start: LocalDate,
    val end: LocalDate,
)

data class ImportPreview(
    val rowCount: Int,
    val categories: Set<PreviewCategory>,
    val accounts: Set<PreviewAccount>,
    val dateRange: PreviewDateRange?,
)
