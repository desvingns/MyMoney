package com.kshavrin.mymoney.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.core.datastore.model.SecureSharedSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorageImpl private constructor(
    @ApplicationContext context: Context,
    dataStore: DataStore<Preferences>?,
    ioDispatcher: CoroutineDispatcher?,
    prefsCreator: (Context, MasterKey) -> SharedPreferences,
    prefsDeleter: (Context, String) -> Unit,
) : SecureStorage {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context, dataStore, ioDispatcher, ::createEncryptedPrefs, ::deletePrefs)

    constructor(
        @ApplicationContext context: Context,
    ) : this(context, null, null, ::createEncryptedPrefs, ::deletePrefs)

    internal constructor(
        context: Context,
        dataStore: DataStore<Preferences>,
        ioDispatcher: CoroutineDispatcher,
        prefsCreator: (Context, MasterKey) -> SharedPreferences,
    ) : this(context, dataStore, ioDispatcher, prefsCreator, ::deletePrefs)

    // Built lazily so EncryptedSharedPreferences / Keystore work stays off the
    // MainActivity inject path. The recovery factory (audit3-lock-security-04) is
    // preserved inside createPrefs.
    private val prefs: SharedPreferences by lazy {
        createPrefs(
            context = context,
            dataStore = dataStore,
            ioDispatcher = ioDispatcher,
            prefsCreator = prefsCreator,
            prefsDeleter = prefsDeleter,
        )
    }

    override fun read(): SecureSettings =
        SecureSettings(
            dropboxRefreshToken = prefs.getString(KEY_DROPBOX_TOKEN, null),
            gdriveAccountEmail = prefs.getString(KEY_GDRIVE_EMAIL, null),
            pinHash = prefs.getString(KEY_PIN_HASH, null),
            failedPinAttempts = prefs.getInt(KEY_FAILED_PIN_ATTEMPTS, 0),
            pinLockoutDeadlineEpochMs =
                if (prefs.contains(KEY_PIN_LOCKOUT_DEADLINE_EPOCH_MS)) {
                    prefs.getLong(KEY_PIN_LOCKOUT_DEADLINE_EPOCH_MS, 0L)
                } else {
                    null
                },
        )

    override fun writeDropboxRefreshToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_DROPBOX_TOKEN) else putString(KEY_DROPBOX_TOKEN, token)
            apply()
        }
    }

    override fun writeGdriveAccountEmail(email: String?) {
        prefs.edit().apply {
            if (email == null) remove(KEY_GDRIVE_EMAIL) else putString(KEY_GDRIVE_EMAIL, email)
            apply()
        }
    }

    override fun writePinHash(hash: String?) {
        prefs.edit().apply {
            if (hash == null) remove(KEY_PIN_HASH) else putString(KEY_PIN_HASH, hash)
            apply()
        }
    }

    override fun writePinLockout(
        failedPinAttempts: Int,
        deadlineEpochMs: Long?,
    ) {
        prefs.edit().apply {
            putInt(KEY_FAILED_PIN_ATTEMPTS, failedPinAttempts)
            if (deadlineEpochMs == null) {
                remove(KEY_PIN_LOCKOUT_DEADLINE_EPOCH_MS)
            } else {
                putLong(KEY_PIN_LOCKOUT_DEADLINE_EPOCH_MS, deadlineEpochMs)
            }
            apply()
        }
    }

    override fun readSharedSession(): SecureSharedSession? {
        val userId = prefs.getString(KEY_SHARED_USER_ID, null)
        val userEmail = prefs.getString(KEY_SHARED_USER_EMAIL, null)
        val accessToken = prefs.getString(KEY_SHARED_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_SHARED_REFRESH_TOKEN, null)
        if (
            userId.isNullOrBlank() ||
            userEmail.isNullOrBlank() ||
            accessToken.isNullOrBlank() ||
            refreshToken.isNullOrBlank() ||
            !prefs.contains(KEY_SHARED_ACCESS_TOKEN_EXPIRES_AT)
        ) {
            return null
        }
        return SecureSharedSession(
            userId = userId,
            userEmail = userEmail,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochSeconds = prefs.getLong(KEY_SHARED_ACCESS_TOKEN_EXPIRES_AT, 0L),
        )
    }

    override fun writeSharedSession(session: SecureSharedSession) {
        check(
            prefs
                .edit()
                .putString(KEY_SHARED_USER_ID, session.userId)
                .putString(KEY_SHARED_USER_EMAIL, session.userEmail)
                .putString(KEY_SHARED_ACCESS_TOKEN, session.accessToken)
                .putString(KEY_SHARED_REFRESH_TOKEN, session.refreshToken)
                .putLong(KEY_SHARED_ACCESS_TOKEN_EXPIRES_AT, session.accessTokenExpiresAtEpochSeconds)
                .commit(),
        ) { "Unable to persist shared session" }
    }

    override fun clearSharedSession() {
        check(
            prefs
                .edit()
                .remove(KEY_SHARED_USER_ID)
                .remove(KEY_SHARED_USER_EMAIL)
                .remove(KEY_SHARED_ACCESS_TOKEN)
                .remove(KEY_SHARED_REFRESH_TOKEN)
                .remove(KEY_SHARED_ACCESS_TOKEN_EXPIRES_AT)
                .commit(),
        ) { "Unable to clear shared session" }
    }

    override fun clearAll() {
        check(prefs.edit().clear().commit()) { "Unable to clear secure storage" }
    }

    private companion object {
        const val FILE_NAME = "com.kshavrin.mymoney_secure"
        const val KEY_DROPBOX_TOKEN = "dropbox_refresh_token"
        const val KEY_GDRIVE_EMAIL = "gdrive_account_email"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_FAILED_PIN_ATTEMPTS = "failed_pin_attempts"
        const val KEY_PIN_LOCKOUT_DEADLINE_EPOCH_MS = "pin_lockout_deadline_epoch_ms"
        const val KEY_SHARED_USER_ID = "shared_user_id"
        const val KEY_SHARED_USER_EMAIL = "shared_user_email"
        const val KEY_SHARED_ACCESS_TOKEN = "shared_access_token"
        const val KEY_SHARED_REFRESH_TOKEN = "shared_refresh_token"
        const val KEY_SHARED_ACCESS_TOKEN_EXPIRES_AT = "shared_access_token_expires_at"

        fun createPrefs(
            context: Context,
            dataStore: DataStore<Preferences>?,
            ioDispatcher: CoroutineDispatcher?,
            prefsCreator: (Context, MasterKey) -> SharedPreferences,
            prefsDeleter: (Context, String) -> Unit,
        ): SharedPreferences {
            val prefs =
                try {
                    prefsCreator(context, createMasterKey(context))
                } catch (e: GeneralSecurityException) {
                    e.reportToSentry()
                    recoverPrefs(context, prefsCreator, prefsDeleter)
                } catch (e: IOException) {
                    e.reportToSentry()
                    recoverPrefs(context, prefsCreator, prefsDeleter)
                }
            if (dataStore != null && ioDispatcher != null) {
                resetLockIfPinMissing(prefs, dataStore, ioDispatcher)
            }
            return prefs
        }

        fun recoverPrefs(
            context: Context,
            prefsCreator: (Context, MasterKey) -> SharedPreferences,
            prefsDeleter: (Context, String) -> Unit,
        ): SharedPreferences {
            prefsDeleter(context, FILE_NAME)
            return prefsCreator(context, createMasterKey(context))
        }

        fun resetLockIfPinMissing(
            prefs: SharedPreferences,
            dataStore: DataStore<Preferences>,
            ioDispatcher: CoroutineDispatcher,
        ) {
            if (prefs.getString(KEY_PIN_HASH, null) != null) return
            runCatching {
                runBlocking(ioDispatcher) {
                    dataStore.edit { settings ->
                        settings[AppSettingsKeys.BIOMETRIC_LOCK_ENABLED] = false
                        settings[AppSettingsKeys.SETTINGS_REVISION] =
                            (settings[AppSettingsKeys.SETTINGS_REVISION] ?: 0L) + 1L
                    }
                }
            }.onFailure { it.reportToSentry() }
        }

        fun createMasterKey(context: Context): MasterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        fun createEncryptedPrefs(
            context: Context,
            masterKey: MasterKey,
        ): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        fun deletePrefs(
            context: Context,
            fileName: String,
        ) {
            if (!context.deleteSharedPreferences(fileName)) {
                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(sharedPrefsDir, "$fileName.xml").delete()
                File(sharedPrefsDir, "$fileName.xml.bak").delete()
            }
        }
    }
}
