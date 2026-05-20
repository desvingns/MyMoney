package com.kshavrin.mymoney.core.datastore

import com.kshavrin.mymoney.core.datastore.model.SecureSettings

interface SecureStorage {
    fun read(): SecureSettings
    fun writeDropboxRefreshToken(token: String?)
    fun writeGdriveAccountEmail(email: String?)
    fun writePinHash(hash: String?)
    fun clearAll()
}
