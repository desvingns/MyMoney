package com.kshavrin.mymoney.core.sync.usecase

import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import javax.inject.Inject

class CloudSyncBackupsUseCase
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
    ) {
        suspend fun exportMigrationBackup(treeUriString: String): Result<Unit> =
            backupRepository.exportDb(treeUriString)

        suspend fun listInternalBackups(): List<BackupFile> = backupRepository.listInternalBackups()
    }
