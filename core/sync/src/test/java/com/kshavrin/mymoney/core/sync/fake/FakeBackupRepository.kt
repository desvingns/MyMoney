package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository

/**
 * Records the file-path overloads SnapshotSyncRepository depends on
 * (exportToFile / importFromFile) and lets each be flipped to a failure.
 * The SAF Uri overloads are unused by the orchestrator and return success.
 */
class FakeBackupRepository : BackupRepository {

    val exportedPaths: MutableList<String> = mutableListOf()
    val importedPaths: MutableList<String> = mutableListOf()

    private var exportResult: Result<Unit> = Result.success(Unit)
    private var importResult: Result<Unit> = Result.success(Unit)

    fun simulateExportFailure(throwable: Throwable = RuntimeException("export failed")) {
        exportResult = Result.failure(throwable)
    }

    fun simulateImportFailure(throwable: Throwable = RuntimeException("import failed")) {
        importResult = Result.failure(throwable)
    }

    override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> {
        exportedPaths += destAbsolutePath
        return exportResult
    }

    override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> {
        importedPaths += srcAbsolutePath
        return importResult
    }

    override suspend fun exportDb(treeUriString: String): Result<Unit> = Result.success(Unit)

    override suspend fun importDb(documentUriString: String): Result<Unit> = Result.success(Unit)

    override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

    override suspend fun rotateBackups(treeUriString: String): Result<Unit> = Result.success(Unit)
}
