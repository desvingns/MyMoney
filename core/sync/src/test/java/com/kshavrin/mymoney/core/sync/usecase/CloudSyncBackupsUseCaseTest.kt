package com.kshavrin.mymoney.core.sync.usecase

import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncBackupsUseCaseTest {
    @Test
    fun `exportMigrationBackup delegates tree uri and successful result`() = runTest {
        val repository = FakeBackupRepository()
        val useCase = CloudSyncBackupsUseCase(repository)
        val treeUri = "content://com.example.documents/tree/backups"

        val result = useCase.exportMigrationBackup(treeUri)

        assertTrue(result.isSuccess)
        assertEquals(listOf(treeUri), repository.exportedTreeUris)
    }

    @Test
    fun `exportMigrationBackup delegates failure result`() = runTest {
        val failure = IllegalStateException("export failed")
        val repository = FakeBackupRepository().apply {
            exportResult = Result.failure(failure)
        }
        val useCase = CloudSyncBackupsUseCase(repository)
        val treeUri = "content://com.example.documents/tree/backups"

        val result = useCase.exportMigrationBackup(treeUri)

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf(treeUri), repository.exportedTreeUris)
    }

    @Test
    fun `listInternalBackups returns repository files`() = runTest {
        val expected =
            listOf(
                BackupFile(
                    name = "backup-new.db",
                    uriString = "/data/user/0/com.kshavrin.mymoney/files/backup-new.db",
                    lastModifiedEpochMs = 200L,
                ),
                BackupFile(
                    name = "backup-old.db",
                    uriString = "/data/user/0/com.kshavrin.mymoney/files/backup-old.db",
                    lastModifiedEpochMs = 100L,
                ),
            )
        val repository = FakeBackupRepository().apply {
            internalBackups = expected
        }
        val useCase = CloudSyncBackupsUseCase(repository)

        val result = useCase.listInternalBackups()

        assertEquals(expected, result)
    }

    private class FakeBackupRepository : BackupRepository {
        val exportedTreeUris = mutableListOf<String>()
        var exportResult: Result<Unit> = Result.success(Unit)
        var internalBackups: List<BackupFile> = emptyList()

        override suspend fun exportDb(treeUriString: String): Result<Unit> {
            exportedTreeUris += treeUriString
            return exportResult
        }

        override suspend fun importDb(documentUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

        override suspend fun rotateBackups(treeUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> = Result.success(Unit)

        override suspend fun listInternalBackups(): List<BackupFile> = internalBackups
    }
}
