package com.kshavrin.mymoney.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class SecureStorageTest {
    private lateinit var context: Context
    private lateinit var storage: SecureStorage
    private val cleanupPrefsNames = mutableListOf<String>()
    private val cleanupFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = SecureStorageImpl(context)
        storage.clearAll()
    }

    @After
    fun tearDown() {
        storage.clearAll()
        cleanupPrefsNames.forEach(context::deleteSharedPreferences)
        cleanupPrefsNames.clear()
        cleanupFiles.forEach(File::delete)
        cleanupFiles.clear()
    }

    @Test
    fun dropbox_token_roundtrip() {
        storage.writeDropboxRefreshToken("token-abc-123")
        assertEquals("token-abc-123", storage.read().dropboxRefreshToken)
    }

    @Test
    fun gdrive_email_roundtrip() {
        storage.writeGdriveAccountEmail("user@example.com")
        assertEquals("user@example.com", storage.read().gdriveAccountEmail)
    }

    @Test
    fun pin_hash_roundtrip() {
        storage.writePinHash("hash-xyz")
        assertEquals("hash-xyz", storage.read().pinHash)
    }

    @Test
    fun pin_lockout_roundtrip() {
        storage.writePinLockout(failedPinAttempts = 10, deadlineEpochMs = 12_345L)

        val read = storage.read()
        assertEquals(10, read.failedPinAttempts)
        assertEquals(12_345L, read.pinLockoutDeadlineEpochMs)
    }

    @Test
    fun clear_pin_lockout_resets_counter_and_deadline() {
        storage.writePinLockout(failedPinAttempts = 5, deadlineEpochMs = 12_345L)

        storage.clearPinLockout()

        val read = storage.read()
        assertEquals(0, read.failedPinAttempts)
        assertNull(read.pinLockoutDeadlineEpochMs)
    }

    @Test
    fun clear_all_removes_each_field() {
        storage.writeDropboxRefreshToken("tok")
        storage.writeGdriveAccountEmail("email")
        storage.writePinHash("hash")
        storage.writePinLockout(failedPinAttempts = 5, deadlineEpochMs = 12_345L)
        storage.clearAll()
        val read = storage.read()
        assertNull(read.dropboxRefreshToken)
        assertNull(read.gdriveAccountEmail)
        assertNull(read.pinHash)
        assertEquals(0, read.failedPinAttempts)
        assertNull(read.pinLockoutDeadlineEpochMs)
    }

    @Test
    fun null_write_removes_field() {
        storage.writeDropboxRefreshToken("tok")
        storage.writeDropboxRefreshToken(null)
        assertNull(storage.read().dropboxRefreshToken)
    }

    @Test
    fun secure_storage_recovers_from_general_security_exception_without_restoring_secrets() =
        runTest {
            val dataStore = newTempDataStore()
            AppSettingsRepositoryImpl(dataStore).update {
                it.copy(biometricLockEnabled = true)
            }
            val deletedFiles = mutableListOf<String>()
            val prefsName = trackedPrefsName("secure_storage_recovery_gse")
            val recoveredPrefs =
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).apply {
                    edit()
                        .putString("dropbox_refresh_token", "stale-token")
                        .putString("pin_hash", "stale-hash")
                        .apply()
                }
            var creatorCalls = 0

            val recoveredStorage =
                instantiateStorage(
                    dataStore = dataStore,
                    prefsCreator = { _, _ ->
                        creatorCalls += 1
                        if (creatorCalls == 1) throw GeneralSecurityException("cannot decrypt restored prefs")
                        recoveredPrefs
                    },
                    prefsDeleter = { _, fileName ->
                        deletedFiles += fileName
                        recoveredPrefs.edit().clear().commit()
                    },
                )

            val read = recoveredStorage.read()
            assertEquals(2, creatorCalls)
            assertEquals(listOf("com.kshavrin.mymoney_secure"), deletedFiles)
            assertNull(read.dropboxRefreshToken)
            assertNull(read.gdriveAccountEmail)
            assertNull(read.pinHash)
            assertEquals(0, read.failedPinAttempts)
            assertNull(read.pinLockoutDeadlineEpochMs)
            assertFalse(
                dataStore.data
                    .first()
                    .toAppSettings()
                    .biometricLockEnabled,
            )
        }

    @Test
    fun secure_storage_recovers_from_io_exception_without_restoring_secrets() =
        runTest {
            val dataStore = newTempDataStore()
            AppSettingsRepositoryImpl(dataStore).update {
                it.copy(biometricLockEnabled = true)
            }
            val deletedFiles = mutableListOf<String>()
            val prefsName = trackedPrefsName("secure_storage_recovery_io")
            val recoveredPrefs =
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).apply {
                    edit()
                        .putString("gdrive_account_email", "stale@example.com")
                        .putString("pin_hash", "stale-hash")
                        .apply()
                }
            var creatorCalls = 0

            val recoveredStorage =
                instantiateStorage(
                    dataStore = dataStore,
                    prefsCreator = { _, _ ->
                        creatorCalls += 1
                        if (creatorCalls == 1) throw IOException("restored prefs file is unreadable")
                        recoveredPrefs
                    },
                    prefsDeleter = { _, fileName ->
                        deletedFiles += fileName
                        recoveredPrefs.edit().clear().commit()
                    },
                )

            val read = recoveredStorage.read()
            assertEquals(2, creatorCalls)
            assertEquals(listOf("com.kshavrin.mymoney_secure"), deletedFiles)
            assertNull(read.dropboxRefreshToken)
            assertNull(read.gdriveAccountEmail)
            assertNull(read.pinHash)
            assertEquals(0, read.failedPinAttempts)
            assertNull(read.pinLockoutDeadlineEpochMs)
            assertFalse(
                dataStore.data
                    .first()
                    .toAppSettings()
                    .biometricLockEnabled,
            )
        }

    @Test
    fun prefs_are_not_created_at_construction_time_lazy_init() =
        runTest {
            var creatorCallCount = 0
            val dataStore = newTempDataStore()
            val prefsName = trackedPrefsName("lazy_init_guard")
            val backingPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

            val storage =
                instantiateStorage(
                    dataStore = dataStore,
                    prefsCreator = { _, _ ->
                        creatorCallCount++
                        backingPrefs
                    },
                    prefsDeleter = { _, _ -> },
                )

            assertEquals(
                "prefsCreator must NOT be called during SecureStorageImpl construction — lazy-init contract",
                0,
                creatorCallCount,
            )

            storage.read()

            assertEquals(
                "prefsCreator must be called exactly once after the first read() access",
                1,
                creatorCallCount,
            )
        }

    @Test
    fun prefs_creator_is_called_only_once_across_multiple_read_write_calls() =
        runTest {
            var creatorCallCount = 0
            val dataStore = newTempDataStore()
            val prefsName = trackedPrefsName("lazy_init_once")
            val backingPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

            val storage =
                instantiateStorage(
                    dataStore = dataStore,
                    prefsCreator = { _, _ ->
                        creatorCallCount++
                        backingPrefs
                    },
                    prefsDeleter = { _, _ -> },
                )

            storage.read()
            storage.writePinHash("hash-one")
            storage.read()
            storage.writeDropboxRefreshToken("token-two")

            assertEquals(
                "by lazy must initialize prefs exactly once regardless of how many operations follow",
                1,
                creatorCallCount,
            )
        }

    private fun instantiateStorage(
        dataStore: DataStore<Preferences>,
        prefsCreator: (Context, androidx.security.crypto.MasterKey) -> SharedPreferences,
        prefsDeleter: (Context, String) -> Unit,
    ): SecureStorage {
        val constructor =
            SecureStorageImpl::class.java.declaredConstructors
                .single { constructor ->
                    val parameterTypes = constructor.parameterTypes
                    parameterTypes.size == 5 &&
                        parameterTypes[0] == Context::class.java &&
                        DataStore::class.java.isAssignableFrom(parameterTypes[1]) &&
                        kotlinx.coroutines.CoroutineDispatcher::class.java.isAssignableFrom(parameterTypes[2])
                }.apply {
                    isAccessible = true
                }
        return constructor.newInstance(
            context,
            dataStore,
            Dispatchers.Unconfined,
            prefsCreator,
            prefsDeleter,
        ) as SecureStorage
    }

    private fun newTempDataStore(): DataStore<Preferences> {
        val file = File(context.cacheDir, "secure-storage-${System.nanoTime()}.preferences_pb")
        cleanupFiles += file
        return PreferenceDataStoreFactory.create(
            produceFile = { file },
        )
    }

    private fun trackedPrefsName(prefix: String): String =
        "$prefix-${System.nanoTime()}".also(cleanupPrefsNames::add)
}
