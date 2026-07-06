package com.kshavrin.mymoney.core.common.exception

import io.sentry.Sentry
import io.sentry.SentryEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SentryExtTest {
    @After
    fun tearDown() {
        Sentry.close()
    }

    @Test
    fun `reportToSentry is a no-op when sdk is disabled`() {
        Sentry.close()

        IllegalStateException("boom").reportToSentry()

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `reportToSentry captures the throwable when sdk is enabled`() {
        val capturedEvent = AtomicReference<SentryEvent?>()
        val throwable = IllegalStateException("boom")

        Sentry.init { options ->
            options.setEnableExternalConfiguration(false)
            options.setDsn(TEST_DSN)
            options.setEnableUncaughtExceptionHandler(false)
            options.setEnableShutdownHook(false)
            options.setEnableBackpressureHandling(false)
            options.setBeforeSend { event, _ ->
                capturedEvent.set(event)
                event
            }
        }

        throwable.reportToSentry()
        Sentry.flush(2_000)

        val event = capturedEvent.get()
        assertNotNull(event)
        assertEquals(throwable, event?.throwable)
        assertEquals("boom", event?.throwable?.message)
    }

    private companion object {
        const val TEST_DSN = "https://public@o0.ingest.sentry.io/1"
    }
}
