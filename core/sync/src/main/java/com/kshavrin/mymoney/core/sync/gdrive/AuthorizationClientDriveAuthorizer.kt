package com.kshavrin.mymoney.core.sync.gdrive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthorizationClientDriveAuthorizer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : GoogleDriveAuthorizer {
        override suspend fun accessToken(accountEmail: String): Result<String> =
            runCatching {
                val request =
                    AuthorizationRequest
                        .builder()
                        .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_FILE)))
                        .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
                        .build()
                val result = Identity.getAuthorizationClient(context).authorize(request).await()
                // hasResolution() here means consent was lost/revoked since the Picker grant;
                // there is no Activity to show the recovery UI from a background sync call.
                if (result.hasResolution()) throw SyncException(SyncError.Auth)
                result.accessToken ?: throw SyncException(SyncError.Auth)
            }

        private companion object {
            const val GOOGLE_ACCOUNT_TYPE = "com.google"
        }
    }
