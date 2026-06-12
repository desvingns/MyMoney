package com.kshavrin.mymoney.feature.lockscreen.fake

import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.SecureSettings

class FakeSecureStorage(
    initial: SecureSettings = SecureSettings(),
) : SecureStorage {

    private var current: SecureSettings = initial

    var onWritePinHash: ((String?) -> Unit)? = null
    val writtenPinHashes = mutableListOf<String?>()
    val writtenPinLockouts = mutableListOf<Pair<Int, Long?>>()
    var clearAllCount = 0
        private set

    override fun read(): SecureSettings = current

    override fun writeDropboxRefreshToken(token: String?) {
        current = current.copy(dropboxRefreshToken = token)
    }

    override fun writeGdriveAccountEmail(email: String?) {
        current = current.copy(gdriveAccountEmail = email)
    }

    override fun writePinHash(hash: String?) {
        onWritePinHash?.invoke(hash)
        writtenPinHashes += hash
        current = current.copy(pinHash = hash)
    }

    override fun writePinLockout(failedPinAttempts: Int, deadlineEpochMs: Long?) {
        writtenPinLockouts += failedPinAttempts to deadlineEpochMs
        current = current.copy(
            failedPinAttempts = failedPinAttempts,
            pinLockoutDeadlineEpochMs = deadlineEpochMs,
        )
    }

    override fun clearAll() {
        clearAllCount++
        current = SecureSettings()
    }

    fun seedPinHash(hash: String?) {
        current = current.copy(pinHash = hash)
    }

    fun seedPinLockout(failedPinAttempts: Int, deadlineEpochMs: Long?) {
        current = current.copy(
            failedPinAttempts = failedPinAttempts,
            pinLockoutDeadlineEpochMs = deadlineEpochMs,
        )
    }
}
