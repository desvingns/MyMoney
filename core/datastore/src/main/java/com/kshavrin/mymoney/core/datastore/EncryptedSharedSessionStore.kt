package com.kshavrin.mymoney.core.datastore

import com.kshavrin.mymoney.core.datastore.model.SecureSharedSession
import com.kshavrin.mymoney.core.network.shared.SharedSessionStore
import com.kshavrin.mymoney.core.network.shared.StoredSharedSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedSharedSessionStore
    @Inject
    constructor(
        private val secureStorage: SecureStorage,
    ) : SharedSessionStore {
        override fun readSharedSession(): StoredSharedSession? =
            secureStorage.readSharedSession()?.toStoredSession()

        override fun writeSharedSession(session: StoredSharedSession) {
            secureStorage.writeSharedSession(session.toSecureSession())
        }

        override fun clearSharedSession() {
            secureStorage.clearSharedSession()
        }

        private fun SecureSharedSession.toStoredSession(): StoredSharedSession =
            StoredSharedSession(
                userId = userId,
                userEmail = userEmail,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresAtEpochSeconds = accessTokenExpiresAtEpochSeconds,
            )

        private fun StoredSharedSession.toSecureSession(): SecureSharedSession =
            SecureSharedSession(
                userId = userId,
                userEmail = userEmail,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresAtEpochSeconds = accessTokenExpiresAtEpochSeconds,
            )
    }
