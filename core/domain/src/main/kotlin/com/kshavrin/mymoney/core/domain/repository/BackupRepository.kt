package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.BackupFile

interface BackupRepository {
    suspend fun exportDb(treeUriString: String): Result<Unit>
    suspend fun importDb(documentUriString: String): Result<Unit>
    suspend fun listLocalBackups(treeUriString: String): List<BackupFile>
    suspend fun rotateBackups(treeUriString: String): Result<Unit>

    suspend fun exportToFile(destAbsolutePath: String): Result<Unit>
    suspend fun importFromFile(srcAbsolutePath: String): Result<Unit>

    companion object {
        const val KEEP_NEWEST: Int = 3

        fun backupsToDelete(files: List<BackupFile>): List<BackupFile> =
            files.sortedBy { it.lastModifiedEpochMs }.dropLast(KEEP_NEWEST)
    }
}
