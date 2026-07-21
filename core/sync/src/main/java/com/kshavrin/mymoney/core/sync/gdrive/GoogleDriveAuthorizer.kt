package com.kshavrin.mymoney.core.sync.gdrive

/**
 * Mints a short-lived DriveScopes.DRIVE_FILE access token for [accountEmail].
 *
 * Must go through the same Identity Services Authorization API the folder Picker consents
 * through — GoogleAccountCredential/GoogleAuthUtil is a separate, deprecated consent ledger
 * that never sees a grant recorded by the Authorization API, which throws NEED_REMOTE_CONSENT
 * for an account that just finished the Picker's consent screen.
 */
interface GoogleDriveAuthorizer {
    suspend fun accessToken(accountEmail: String): Result<String>
}
