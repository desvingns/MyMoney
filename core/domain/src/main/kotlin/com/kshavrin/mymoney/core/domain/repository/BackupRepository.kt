package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.csv.StagedImport
import com.kshavrin.mymoney.core.domain.model.BackupFile

data class CsvImportFocus(
    val occurredAtEpochMs: Long,
    val currencyId: Long,
)

class BackupSchemaTooNewException(
    val backupVersion: Int,
    val supportedVersion: Int,
) : Exception("Backup schema $backupVersion is newer than supported $supportedVersion")

interface BackupRepository {
    suspend fun exportDb(treeUriString: String): Result<Unit>

    suspend fun importDb(documentUriString: String): Result<Unit>

    suspend fun listLocalBackups(treeUriString: String): List<BackupFile>

    suspend fun rotateBackups(treeUriString: String): Result<Unit>

    suspend fun exportTransactionsCsv(documentUriString: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("CSV export is not supported"))

    suspend fun importTransactionsCsv(documentUriString: String): Result<CsvImportFocus?> =
        Result.failure(UnsupportedOperationException("CSV import is not supported"))

    /** Read and parse a CSV (both formats) into memory for the wizard, without touching the DB. */
    suspend fun parseImport(documentUriString: String): Result<StagedImport> =
        Result.failure(UnsupportedOperationException("CSV import is not supported"))

    /** Apply the chosen [ImportPlan] over an already-parsed import in one withTransaction. */
    suspend fun commitImport(
        staged: StagedImport,
        plan: ImportPlan,
    ): Result<CsvImportFocus?> =
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
