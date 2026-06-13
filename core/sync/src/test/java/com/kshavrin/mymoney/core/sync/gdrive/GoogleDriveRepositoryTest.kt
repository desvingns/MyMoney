package com.kshavrin.mymoney.core.sync.gdrive

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class GoogleDriveRepositoryTest {
    // --- mapGdriveError ------------------------------------------------------
    //
    // The §9.5 error-classification seam is the only part of GoogleDriveRepository
    // that can be unit-tested without real Drive credentials (OQ-3) — and the
    // project forbids mocking, so the Drive client is never touched here. We feed
    // mapGdriveError the internal GdriveHttpException marker directly (the seam
    // already translates GoogleJsonResponseException -> GdriveHttpException at the
    // IO boundary, so we never need a real SDK exception). GdriveHttpException's
    // constructor demands a non-null cause, so every marker carries a throwaway.

    private fun gdriveError(
        statusCode: Int,
        reason: String? = null,
    ): GdriveHttpException =
        GdriveHttpException(statusCode, reason, IOException("cause"))

    @Test
    fun `mapGdriveError maps HTTP 401 to Auth`() {
        assertEquals(SyncError.Auth, mapGdriveError(gdriveError(statusCode = 401)))
    }

    @Test
    fun `mapGdriveError maps HTTP 403 with storageQuotaExceeded reason to Quota`() {
        assertEquals(
            SyncError.Quota,
            mapGdriveError(gdriveError(statusCode = 403, reason = GDRIVE_QUOTA_REASON)),
        )
    }

    @Test
    fun `mapGdriveError maps HTTP 403 with a non-quota reason to Auth`() {
        assertEquals(
            SyncError.Auth,
            mapGdriveError(gdriveError(statusCode = 403, reason = "insufficientPermissions")),
        )
    }

    @Test
    fun `mapGdriveError maps HTTP 403 with a null reason to Auth`() {
        assertEquals(SyncError.Auth, mapGdriveError(gdriveError(statusCode = 403, reason = null)))
    }

    @Test
    fun `mapGdriveError maps HTTP 500 to Server`() {
        assertEquals(SyncError.Server, mapGdriveError(gdriveError(statusCode = 500)))
    }

    @Test
    fun `mapGdriveError maps HTTP 503 to Server`() {
        assertEquals(SyncError.Server, mapGdriveError(gdriveError(statusCode = 503)))
    }

    @Test
    fun `mapGdriveError maps an unmapped 4xx status to Unknown`() {
        // 429 is < 500 and not one of the explicit branches -> the else arm.
        assertEquals(SyncError.Unknown, mapGdriveError(gdriveError(statusCode = 429)))
    }

    @Test
    fun `mapGdriveError maps a plain IOException to Network`() {
        assertEquals(SyncError.Network, mapGdriveError(IOException("offline")))
    }

    @Test
    fun `mapGdriveError maps any IOException subtype to Network`() {
        assertEquals(SyncError.Network, mapGdriveError(SocketTimeoutException()))
    }

    @Test
    fun `mapGdriveError passes through the inner error of a SyncException`() {
        for (error in SyncError.entries) {
            assertEquals(error, mapGdriveError(SyncException(error)))
        }
    }

    @Test
    fun `mapGdriveError maps an unrecognised throwable to Unknown`() {
        assertEquals(SyncError.Unknown, mapGdriveError(RuntimeException("boom")))
    }
}
