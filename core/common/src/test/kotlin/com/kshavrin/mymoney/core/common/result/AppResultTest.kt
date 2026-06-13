package com.kshavrin.mymoney.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppResultTest {
    @Test
    fun `onSuccess invokes action on Success and returns same instance`() {
        val original: AppResult<Int> = AppResult.Success(42)
        var captured: Int? = null

        val returned = original.onSuccess { captured = it }

        assertEquals(42, captured)
        assertSame(original, returned)
    }

    @Test
    fun `onSuccess does not invoke action on Error and returns same instance`() {
        val error = IllegalStateException("boom")
        val original: AppResult<Int> = AppResult.Error(error)

        val returned = original.onSuccess { fail("onSuccess action must not run on Error") }

        assertSame(original, returned)
    }

    @Test
    fun `onError invokes action on Error and returns same instance`() {
        val error = IllegalStateException("boom")
        val original: AppResult<Int> = AppResult.Error(error)
        var captured: Throwable? = null

        val returned = original.onError { captured = it }

        assertSame(error, captured)
        assertSame(original, returned)
    }

    @Test
    fun `onError does not invoke action on Success and returns same instance`() {
        val original: AppResult<Int> = AppResult.Success(42)

        val returned = original.onError { fail("onError action must not run on Success") }

        assertSame(original, returned)
    }

    @Test
    fun `map transforms Success value`() {
        val original: AppResult<Int> = AppResult.Success(21)

        val mapped = original.map { it * 2 }

        assertTrue(mapped is AppResult.Success)
        assertEquals(42, (mapped as AppResult.Success).data)
    }

    @Test
    fun `map preserves Error without invoking transform`() {
        val error = IllegalStateException("boom")
        val original: AppResult<Int> = AppResult.Error(error)

        val mapped =
            original.map<Int, String> {
                fail("transform must not run on Error")
                "unreached"
            }

        assertTrue(mapped is AppResult.Error)
        assertSame(error, (mapped as AppResult.Error).cause)
    }

    @Test
    fun `getOrElse returns data on Success without invoking default`() {
        val original: AppResult<Int> = AppResult.Success(42)

        val value =
            original.getOrElse {
                fail("default must not run on Success")
                -1
            }

        assertEquals(42, value)
    }

    @Test
    fun `getOrElse returns default value on Error and receives cause`() {
        val error = IllegalStateException("boom")
        val original: AppResult<Int> = AppResult.Error(error)
        var receivedCause: Throwable? = null

        val value =
            original.getOrElse { t ->
                receivedCause = t
                -1
            }

        assertEquals(-1, value)
        assertSame(error, receivedCause)
    }

    @Test
    fun `getOrNull returns data on Success`() {
        val original: AppResult<Int> = AppResult.Success(42)

        assertEquals(42, original.getOrNull())
    }

    @Test
    fun `getOrNull returns null on Error`() {
        val original: AppResult<Int> = AppResult.Error(IllegalStateException("boom"))

        assertNull(original.getOrNull())
    }
}
