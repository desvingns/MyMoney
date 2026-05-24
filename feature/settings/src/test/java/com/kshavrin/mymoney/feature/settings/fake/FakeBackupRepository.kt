package com.kshavrin.mymoney.feature.settings.fake

import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository

class FakeBackupRepository : BackupRepository {

    val exportedUris: MutableList<String> = mutableListOf()
    val importedUris: MutableList<String> = mutableListOf()

    private var exportResult: Result<Unit> = Result.success(Unit)
    private var importResult: Result<Unit> = Result.success(Unit)
    private var localBackups: List<BackupFile> = emptyList()

    fun simulateExportFailure(throwable: Throwable = RuntimeException("export failed")) {
        exportResult = Result.failure(throwable)
    }

    fun simulateImportFailure(throwable: Throwable = RuntimeException("import failed")) {
        importResult = Result.failure(throwable)
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
}
