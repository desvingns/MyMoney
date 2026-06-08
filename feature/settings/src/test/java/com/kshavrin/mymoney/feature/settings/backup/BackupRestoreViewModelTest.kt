package com.kshavrin.mymoney.feature.settings.backup

import android.content.Context
import android.content.ContextWrapper
import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.core.domain.repository.CsvImportFocus
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.fake.FakeAppSettingsRepository
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
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var secureStorage: RecordingSecureStorage

    private fun buildViewModel(): BackupRestoreViewModel {
        repository = FakeBackupRepository()
        appSettingsRepository = FakeAppSettingsRepository()
        secureStorage = RecordingSecureStorage()
        return BackupRestoreViewModel(
            repository,
            context,
            mainDispatcherRule.testDispatcher,
            appSettingsRepository,
            secureStorage,
        )
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
    fun `csv export forwards the picked document uri to the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ExportCsvFilePicked("content://doc/transactions.csv"))

        assertEquals(listOf("content://doc/transactions.csv"), repository.exportedCsvUris)
    }

    @Test
    fun `successful csv export emits CsvExportSucceeded action and clears progress`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ExportCsvFilePicked("content://doc/transactions.csv"))
            assertEquals(BackupRestoreAction.CsvExportSucceeded, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `csv export rejected for non round trippable rows shows its error and emits no success action`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateCsvExportFailure(
            IllegalArgumentException("CSV export cannot represent transfer transactions"),
        )

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ExportCsvFilePicked("content://doc/transactions.csv"))
            expectNoEvents()
        }

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(R.string.backup_export_csv_error, state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `csv import forwards the picked document uri to the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ImportCsvFilePicked("content://doc/import.csv"))

        assertEquals(listOf("content://doc/import.csv"), repository.importedCsvUris)
    }

    @Test
    fun `successful csv import emits CsvImportSucceeded action and clears progress`() = runTest {
        val viewModel = buildViewModel()
        repository.seedCsvImportFocus(CsvImportFocus(occurredAtEpochMs = 1_706_918_400_000L, currencyId = 7L))

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ImportCsvFilePicked("content://doc/import.csv"))
            assertEquals(BackupRestoreAction.CsvImportSucceeded, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1_706_918_400_000L, appSettingsRepository.settings.value.importFocusEpochMs)
        assertEquals(7L, appSettingsRepository.settings.value.importFocusCurrencyId)
    }

    @Test
    fun `csv import rejected for non round trippable rows shows its error and emits no success action`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateCsvImportFailure(
            IllegalArgumentException("CSV import cannot represent transfer transactions"),
        )

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ImportCsvFilePicked("content://doc/import.csv"))
            expectNoEvents()
        }

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(R.string.backup_import_csv_error, state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requesting reset shows confirmation and clears an existing error`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateCsvExportFailure()
        viewModel.onEvent(BackupRestoreEvent.ExportCsvFilePicked("content://doc/transactions.csv"))

        viewModel.onEvent(BackupRestoreEvent.ResetRequested)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.resetConfirmationVisible)
            assertNull(state.errorBannerRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling reset hides confirmation without wiping collaborators`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(BackupRestoreEvent.ResetRequested)

        viewModel.onEvent(BackupRestoreEvent.ResetCancelled)

        viewModel.state.test {
            assertFalse(awaitItem().resetConfirmationVisible)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repository.clearDatabaseCalls)
        assertEquals(0, appSettingsRepository.resetCalls)
        assertEquals(0, secureStorage.clearAllCalls)
    }

    @Test
    fun `confirming reset without visible confirmation does not wipe data`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BackupRestoreEvent.ResetConfirmed)

        assertEquals(0, repository.clearDatabaseCalls)
        assertEquals(0, appSettingsRepository.resetCalls)
        assertEquals(0, secureStorage.clearAllCalls)
    }

    @Test
    fun `confirmed reset wipes every collaborator and emits restart action`() = runTest {
        val viewModel = buildViewModel()
        appSettingsRepository.seed(AppSettings(language = "ru", firstPositiveSeen = true))

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ResetRequested)
            viewModel.onEvent(BackupRestoreEvent.ResetConfirmed)

            assertEquals(BackupRestoreAction.RestartToOnboardingAfterReset(hadFailures = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repository.clearDatabaseCalls)
        assertEquals(1, appSettingsRepository.resetCalls)
        assertEquals(AppSettings(), appSettingsRepository.settings.value)
        assertEquals(1, secureStorage.clearAllCalls)
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.inProgress)
            assertFalse(state.resetConfirmationVisible)
            assertNull(state.errorBannerRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `database reset failure still clears other stores and requests restart with failure`() = runTest {
        val viewModel = buildViewModel()
        repository.simulateClearDatabaseFailure()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ResetRequested)
            viewModel.onEvent(BackupRestoreEvent.ResetConfirmed)
            assertEquals(BackupRestoreAction.RestartToOnboardingAfterReset(hadFailures = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repository.clearDatabaseCalls)
        assertEquals(1, appSettingsRepository.resetCalls)
        assertEquals(1, secureStorage.clearAllCalls)
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(R.string.backup_reset_error, state.errorBannerRes)
            assertFalse(state.inProgress)
            assertFalse(state.resetConfirmationVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `settings reset failure still wipes remaining stores and requests restart with failure`() = runTest {
        val viewModel = buildViewModel()
        appSettingsRepository.simulateResetFailure()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ResetRequested)
            viewModel.onEvent(BackupRestoreEvent.ResetConfirmed)
            assertEquals(BackupRestoreAction.RestartToOnboardingAfterReset(hadFailures = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repository.clearDatabaseCalls)
        assertEquals(1, appSettingsRepository.resetCalls)
        assertEquals(1, secureStorage.clearAllCalls)
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(R.string.backup_reset_error, state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `secure storage reset failure after database wipe still requests restart and shows error`() = runTest {
        val viewModel = buildViewModel()
        secureStorage.simulateClearFailure()

        viewModel.actions.test {
            viewModel.onEvent(BackupRestoreEvent.ResetRequested)
            viewModel.onEvent(BackupRestoreEvent.ResetConfirmed)
            assertEquals(BackupRestoreAction.RestartToOnboardingAfterReset(hadFailures = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repository.clearDatabaseCalls)
        assertEquals(1, appSettingsRepository.resetCalls)
        assertEquals(1, secureStorage.clearAllCalls)
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(R.string.backup_reset_error, state.errorBannerRes)
            assertFalse(state.inProgress)
            cancelAndIgnoreRemainingEvents()
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

    private class RecordingSecureStorage : SecureStorage {
        var clearAllCalls: Int = 0
            private set
        private var clearFailure: Throwable? = null

        override fun read(): SecureSettings = SecureSettings()

        override fun writeDropboxRefreshToken(token: String?) = Unit

        override fun writeGdriveAccountEmail(email: String?) = Unit

        override fun writePinHash(hash: String?) = Unit

        override fun clearAll() {
            clearAllCalls += 1
            clearFailure?.let { throw it }
        }

        fun simulateClearFailure(throwable: Throwable = RuntimeException("secure storage clear failed")) {
            clearFailure = throwable
        }
    }
}
