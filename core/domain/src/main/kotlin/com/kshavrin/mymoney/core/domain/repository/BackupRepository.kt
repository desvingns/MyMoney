package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.BackupFile

data class CsvImportFocus(
    val occurredAtEpochMs: Long,
    val currencyId: Long,
)

interface BackupRepository {
    suspend fun exportDb(treeUriString: String): Result<Unit>

    suspend fun importDb(documentUriString: String): Result<Unit>

    suspend fun listLocalBackups(treeUriString: String): List<BackupFile>

    suspend fun rotateBackups(treeUriString: String): Result<Unit>

    suspend fun exportTransactionsCsv(documentUriString: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("CSV export is not supported"))

    suspend fun importTransactionsCsv(documentUriString: String): Result<CsvImportFocus?> =
        Result.failure(UnsupportedOperationException("CSV import is not supported"))

    suspend fun clearDatabase(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Factory reset is not supported"))

    suspend fun exportToFile(destAbsolutePath: String): Result<Unit>

    suspend fun importFromFile(srcAbsolutePath: String): Result<Unit>

    companion object {
        const val KEEP_NEWEST: Int = 3

        fun backupsToDelete(files: List<BackupFile>): List<BackupFile> =
            files.sortedBy { it.lastModifiedEpochMs }.dropLast(KEEP_NEWEST)
    }
}
