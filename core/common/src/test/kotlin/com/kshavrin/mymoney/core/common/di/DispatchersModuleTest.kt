package com.kshavrin.mymoney.core.common.di

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Test

class DispatchersModuleTest {

    @Test
    fun `provideIoDispatcher returns Dispatchers IO`() {
        val provided = DispatchersModule.provideIoDispatcher()

        assertSame(Dispatchers.IO, provided)
    }

    @Test
    fun `provideDefaultDispatcher returns Dispatchers Default`() {
        val provided = DispatchersModule.provideDefaultDispatcher()

        assertSame(Dispatchers.Default, provided)
    }
}
