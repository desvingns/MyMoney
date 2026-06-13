package com.kshavrin.mymoney.core.common.exception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExceptionTest {
    @Test
    fun `SyncException is a Throwable`() {
        val ex = SyncException(SyncError.Network)

        assertTrue(ex is Exception)
    }

    @Test
    fun `SyncException carries the given SyncError`() {
        val ex = SyncException(SyncError.Auth)

        assertSame(SyncError.Auth, ex.syncError)
    }

    @Test
    fun `SyncException retains SyncError for every enum value`() {
        SyncError.values().forEach { error ->
            val ex = SyncException(error)
            assertSame(error, ex.syncError)
        }
    }

    @Test
    fun `SyncError declares the six expected values in the expected order`() {
        val expected =
            listOf(
                SyncError.Network,
                SyncError.Auth,
                SyncError.Quota,
                SyncError.Conflict,
                SyncError.Server,
                SyncError.Unknown,
            )

        assertEquals(expected, SyncError.values().toList())
    }

    @Test
    fun `SyncError has exactly six values`() {
        assertEquals(6, SyncError.values().size)
    }
}
