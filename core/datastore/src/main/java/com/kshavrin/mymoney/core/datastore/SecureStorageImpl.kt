package com.kshavrin.mymoney.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorageImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SecureStorage {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): SecureSettings = SecureSettings(
        dropboxRefreshToken = prefs.getString(KEY_DROPBOX_TOKEN, null),
        gdriveAccountEmail = prefs.getString(KEY_GDRIVE_EMAIL, null),
        pinHash = prefs.getString(KEY_PIN_HASH, null),
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

    override fun clearAll() {
        check(prefs.edit().clear().commit()) { "Unable to clear secure storage" }
    }

    private companion object {
        const val FILE_NAME = "com.kshavrin.mymoney_secure"
        const val KEY_DROPBOX_TOKEN = "dropbox_refresh_token"
        const val KEY_GDRIVE_EMAIL = "gdrive_account_email"
        const val KEY_PIN_HASH = "pin_hash"
    }
}
