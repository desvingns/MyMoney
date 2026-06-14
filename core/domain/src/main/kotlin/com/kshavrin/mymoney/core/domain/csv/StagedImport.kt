package com.kshavrin.mymoney.core.domain.csv

/**
 * Result of the parse-only phase of a CSV import (SPEC 02). Holds the tokenized records and the
 * detected [format] so the commit phase can replay the existing per-format resolve+insert logic,
 * plus the [preview] the wizard shows before the user picks an [ImportPlan]. Carrying no database
 * state keeps parsing pure (no DB reads/writes) and lets the commit run entirely inside one
 * withTransaction (G4).
 */
data class StagedImport(
    val format: CsvImportFormat,
    val records: List<List<String>>,
    val preview: ImportPreview,
)
