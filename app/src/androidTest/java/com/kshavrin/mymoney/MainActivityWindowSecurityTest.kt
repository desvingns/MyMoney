package com.kshavrin.mymoney

import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityWindowSecurityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `launch with biometric lock enabled applies secure window flag`() = runTest {
        seedSettings(biometricLockEnabled = true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.waitForFlagSecure(expected = true)
        }
    }

    @Test
    fun `launch with biometric lock disabled leaves secure window flag cleared`() = runTest {
        seedSettings(biometricLockEnabled = false)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.waitForFlagSecure(expected = false)
        }
    }

    private suspend fun seedSettings(biometricLockEnabled: Boolean) {
        appSettingsRepository.reset()
        appSettingsRepository.update {
            it.copy(
                biometricLockEnabled = biometricLockEnabled,
                onboardingCompletedAt = 1L,
            )
        }
    }

    private fun ActivityScenario<MainActivity>.waitForFlagSecure(expected: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var actual = !expected
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            onActivity { activity ->
                actual = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            }
            if (actual == expected) return
        }
        assertEquals(expected, actual)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
