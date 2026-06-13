package com.kshavrin.mymoney.feature.settings.fake

import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CsvImportFocus

class FakeBackupRepository : BackupRepository {
    val exportedUris: MutableList<String> = mutableListOf()
    val importedUris: MutableList<String> = mutableListOf()
    val exportedCsvUris: MutableList<String> = mutableListOf()
    val importedCsvUris: MutableList<String> = mutableListOf()
    val exportedFilePaths: MutableList<String> = mutableListOf()
    val importedFilePaths: MutableList<String> = mutableListOf()
    val rotatedUris: MutableList<String> = mutableListOf()
    var clearDatabaseCalls: Int = 0
        private set

    private var exportResult: Result<Unit> = Result.success(Unit)
    private var importResult: Result<Unit> = Result.success(Unit)
    private var exportCsvResult: Result<Unit> = Result.success(Unit)
    private var importCsvResult: Result<CsvImportFocus?> = Result.success(null)
    private var clearDatabaseResult: Result<Unit> = Result.success(Unit)
    private var localBackups: List<BackupFile> = emptyList()

    fun simulateExportFailure(throwable: Throwable = RuntimeException("export failed")) {
        exportResult = Result.failure(throwable)
    }

    fun simulateImportFailure(throwable: Throwable = RuntimeException("import failed")) {
        importResult = Result.failure(throwable)
    }

    fun simulateCsvExportFailure(throwable: Throwable = RuntimeException("csv export failed")) {
        exportCsvResult = Result.failure(throwable)
    }

    fun simulateCsvImportFailure(throwable: Throwable = RuntimeException("csv import failed")) {
        importCsvResult = Result.failure(throwable)
    }

    fun seedCsvImportFocus(focus: CsvImportFocus) {
        importCsvResult = Result.success(focus)
    }

    fun simulateClearDatabaseFailure(throwable: Throwable = RuntimeException("clear database failed")) {
        clearDatabaseResult = Result.failure(throwable)
    }

    fun seedLocalBackups(backups: List<BackupFile>) {
        localBackups = backups
    }

    override suspend fun exportDb(treeUriString: String): Result<Unit> {
        exportedUris += treeUriString
        return exportResult
    }

    override suspend fun importDb(documentUriString: String): Result<Unit> {
        importedUris += documentUriString
        return importResult
    }

    override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = localBackups

    override suspend fun rotateBackups(treeUriString: String): Result<Unit> {
        rotatedUris += treeUriString
        return exportResult
    }

    override suspend fun exportTransactionsCsv(documentUriString: String): Result<Unit> {
        exportedCsvUris += documentUriString
        return exportCsvResult
    }

    override suspend fun importTransactionsCsv(documentUriString: String): Result<CsvImportFocus?> {
        importedCsvUris += documentUriString
        return importCsvResult
    }

    override suspend fun clearDatabase(): Result<Unit> {
        clearDatabaseCalls += 1
        return clearDatabaseResult
    }

    override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> {
        exportedFilePaths += destAbsolutePath
        return exportResult
    }

    override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> {
        importedFilePaths += srcAbsolutePath
        return importResult
    }
}
