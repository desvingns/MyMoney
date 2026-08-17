package com.kshavrin.mymoney.core.network.shared

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.network.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

class GoogleIdTokenUnavailableException : Exception("Google ID token unavailable")

class CredentialManagerGoogleIdTokenProvider
    @Inject
    constructor() : GoogleIdTokenProvider {
        override suspend fun fetchIdToken(activity: Activity): Result<GoogleIdTokenResult> {
            val webClientId =
                sharedGoogleWebClientIdOrNull(BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID)
                    ?: return Result.failure(GoogleIdTokenUnavailableException())
            val credentialManager = CredentialManager.create(activity)
            val nonce = generateGoogleNonce()
            val result =
                try {
                    credentialManager.getCredential(
                        context = activity,
                        request =
                            sharedGoogleCredentialRequest(
                                webClientId,
                                sharedGoogleCredentialNonce(nonce),
                                authorizedAccountsOnly = true,
                            ),
                    )
                } catch (_: NoCredentialException) {
                    try {
                        credentialManager.getCredential(
                            context = activity,
                            request =
                                sharedGoogleCredentialRequest(
                                    webClientId,
                                    sharedGoogleCredentialNonce(nonce),
                                    authorizedAccountsOnly = false,
                                ),
                        )
                    } catch (t: Throwable) {
                        t.reportToSentry()
                        return Result.failure(t)
                    }
                } catch (t: Throwable) {
                    t.reportToSentry()
                    return Result.failure(t)
                }
            val credential = result.credential as? CustomCredential
            val idToken =
                credential
                    ?.takeIf { it.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL }
                    ?.let {
                        runCatching { GoogleIdTokenCredential.createFrom(it.data).idToken }
                            .onFailure(Throwable::reportToSentry)
                            .getOrNull()
                    }
            return if (idToken.isNullOrBlank()) {
                Result.failure(GoogleIdTokenUnavailableException())
            } else {
                Result.success(GoogleIdTokenResult(idToken = idToken, nonce = nonce))
            }
        }
    }

private const val NONCE_BYTES = 32
private const val PLACEHOLDER_PREFIX = "PLACEHOLDER_"

private fun generateGoogleNonce(): String =
    ByteArray(NONCE_BYTES)
        .also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }

private fun sharedGoogleCredentialNonce(rawNonce: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(rawNonce.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun normalizeSharedGoogleWebClientId(configuredClientId: String): String =
    configuredClientId
        .trim()
        .removeSurrounding("<", ">")
        .trim()

private fun sharedGoogleWebClientIdOrNull(configuredClientId: String): String? =
    normalizeSharedGoogleWebClientId(configuredClientId)
        .takeIf { it.isNotBlank() && !it.startsWith(PLACEHOLDER_PREFIX) }

private fun sharedGoogleCredentialRequest(
    webClientId: String,
    nonce: String,
    authorizedAccountsOnly: Boolean,
): GetCredentialRequest =
    GetCredentialRequest
        .Builder()
        .addCredentialOption(
            GetGoogleIdOption
                .Builder()
                .setFilterByAuthorizedAccounts(authorizedAccountsOnly)
                .setAutoSelectEnabled(authorizedAccountsOnly)
                .setServerClientId(normalizeSharedGoogleWebClientId(webClientId))
                .setNonce(nonce)
                .build(),
        ).build()
