package com.kshavrin.mymoney.core.datastore

import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.core.datastore.model.SecureSharedSession

interface SecureStorage {
    fun read(): SecureSettings

    fun writeDropboxRefreshToken(token: String?)

    fun writeGdriveAccountEmail(email: String?)

    fun writePinHash(hash: String?)

    fun readSharedSession(): SecureSharedSession? = null

    fun writeSharedSession(session: SecureSharedSession) = Unit

    fun clearSharedSession() = Unit

    fun writePinLockout(
        failedPinAttempts: Int,
        deadlineEpochMs: Long?,
    ) = Unit

    fun clearPinLockout() = writePinLockout(failedPinAttempts = 0, deadlineEpochMs = null)

    fun clearAll()
}
