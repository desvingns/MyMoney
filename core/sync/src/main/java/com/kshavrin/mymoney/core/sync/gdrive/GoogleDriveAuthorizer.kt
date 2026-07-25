package com.kshavrin.mymoney.core.sync.gdrive

/**
 * Mints a short-lived Drive app-data access token for [accountEmail].
 *
 * Must use the same Identity Services Authorization API used for account consent.
 * GoogleAccountCredential/GoogleAuthUtil has a separate, deprecated consent ledger and cannot see
 * a grant recorded by that API.
 */
interface GoogleDriveAuthorizer {
    suspend fun accessToken(accountEmail: String): Result<String>
}
