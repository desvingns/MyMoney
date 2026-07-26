package com.kshavrin.mymoney.core.sync.dropbox

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RetryException
import com.dropbox.core.ServerException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.sync.CloudAccountIdentity
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.SyncTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxRepository
    @Inject
    constructor(
        private val secureStorage: SecureStorage,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CloudSyncBackend {
        override val target: SyncTarget = SyncTarget.Dropbox

        // The serialized DbxCredential (refresh-token flow) is persisted in the Dropbox
        // refresh-token slot of SecureStorage; a blank slot means "not connected".
        private fun storedCredential(): String? =
            secureStorage.read().dropboxRefreshToken?.takeIf { it.isNotBlank() }

        private fun client(): DbxClientV2 {
            val serialized = storedCredential() ?: throw SyncException(SyncError.Auth)
            val credential = DbxCredential.Reader.readFully(serialized)
            val config = DbxRequestConfig.newBuilder(CLIENT_IDENTIFIER).build()
            return DbxClientV2(config, credential)
        }

        override fun connect(payload: String) {
            secureStorage.writeDropboxRefreshToken(payload)
        }

        override fun isConnected(): Boolean = storedCredential() != null

        override fun disconnect() {
            secureStorage.writeDropboxRefreshToken(null)
        }

        override suspend fun accountLabel(): Result<String> =
            runOnIo {
                client()
                    .users()
                    .currentAccount.email
            }

        override suspend fun accountIdentity(): Result<CloudAccountIdentity> =
            runOnIo {
                client().users().currentAccount.let { account ->
                    CloudAccountIdentity(
                        stableId = account.accountId,
                        label = account.email,
                    )
                }
            }

        private suspend fun <T> runOnIo(block: () -> T): Result<T> =
            withContext(ioDispatcher) {
                var lastError: Throwable
                var attempt = 0
                do {
                    try {
                        return@withContext Result.success(block())
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        lastError = t
                        val backoff = transientBackoffMillis(t, attempt) ?: break
                        delay(backoff)
                        attempt++
                    }
                } while (true)
                Result.failure(SyncException(mapDropboxError(lastError)))
            }

        private fun transientBackoffMillis(
            t: Throwable,
            attempt: Int,
        ): Long? {
            if (attempt >= MAX_RETRIES - 1) return null
            return when (t) {
                is RetryException -> t.backoffMillis.coerceAtLeast(retryDelayMillis(attempt))
                is NetworkIOException, is IOException -> retryDelayMillis(attempt)
                is ServerException -> retryDelayMillis(attempt)
                else -> null
            }
        }

        private fun retryDelayMillis(attempt: Int): Long = BASE_BACKOFF_MILLIS shl attempt

        private companion object {
            const val CLIENT_IDENTIFIER = "MyMoney/1.0"
            const val MAX_RETRIES = 3
            const val BASE_BACKOFF_MILLIS = 1_000L
        }
    }

internal fun mapDropboxError(t: Throwable): SyncError =
    when {
        t is SyncException -> t.syncError
        t is IOException -> SyncError.Network
        else ->
            when (t::class.simpleName) {
                "InvalidAccessTokenException" -> SyncError.Auth
                "SpaceError" -> SyncError.Quota
                "NetworkIOException" -> SyncError.Network
                "RateLimitException", "RetryException", "ServerException", "DbxException" ->
                    SyncError.Server
                else -> SyncError.Unknown
            }
    }
