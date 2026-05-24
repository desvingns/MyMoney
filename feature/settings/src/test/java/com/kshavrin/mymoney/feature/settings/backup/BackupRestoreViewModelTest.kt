package com.kshavrin.mymoney.feature.settings.backup

import android.content.Context
import android.content.ContextWrapper
import app.cash.turbine.test
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.fake.FakeBackupRepository
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dbFile = File.createTempFile("monefy", ".db").apply {
        writeBytes(ByteArray(2048))
        deleteOnExit()
    }

    private val context: Context = object : ContextWrapper(null) {
        override fun getDatabasePath(name: String): File = dbFile
    }

    private lateinit var repository: FakeBackupRepository

    private fun buildViewModel(): BackupRestoreViewModel {
        repository = FakeBackupRepository()
        return BackupRestoreViewModel(repository, context, mainDispatcherRule.testDispatcher)
    }

    @Test
    fun `initial state reflects the database file size`() = runTest {
        val viewModel = buildViewModel()

        viewModel.state.test {
            assertEquals(2048L, awaitItem().dbSizeBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `export forwards the picked tree uri to the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))

        assertEquals(listOf("content://tree/backups"), repository.exportedUris)
    }

    @Test
    fun `successful export emits ExportSucceeded action`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))
            assertEquals(BackupRestoreAction.ExportSucceeded, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful export leaves no error banner and clears progress`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed export sets the backup error banner`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateExportFailure()

        viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))

        viewModel.state.test {
            assertEquals(R.string.backup_error, awaitItem().errorBannerRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed export clears the in progress flag`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateExportFailure()

        viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))

        viewModel.state.test {
            assertFalse(awaitItem().inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed export emits no success action`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateExportFailure()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))
            expectNoEvents()
        }
    }

    @Test
    fun `import forwards the picked document uri to the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ImportFilePicked("content://doc/backup.db"))

        assertEquals(listOf("content://doc/backup.db"), repository.importedUris)
    }

    @Test
    fun `successful import emits RestartAfterRestore action`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ImportFilePicked("content://doc/backup.db"))
            assertEquals(BackupRestoreAction.RestartAfterRestore, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful import clears progress and leaves no error banner`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ImportFilePicked("content://doc/backup.db"))

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.inProgress)
            assertNull(state.errorBannerRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed import sets the backup error banner and clears progress`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateImportFailure()

        viewModel.onEvent(BackupRestoreEvent.ImportFilePicked("content://doc/backup.db"))

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(R.string.backup_error, state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed import emits no restart action`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateImportFailure()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ImportFilePicked("content://doc/backup.db"))
            expectNoEvents()
        }
    }

    @Test
    fun `dismiss error clears an existing error banner`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateExportFailure()
        viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked("content://tree/backups"))

        viewModel.onEvent(BackupRestoreEvent.DismissError)

        viewModel.state.test {
            assertNull(awaitItem().errorBannerRes)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
