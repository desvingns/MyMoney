package com.kshavrin.mymoney

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.ui.window.SecureWindowController
import com.kshavrin.mymoney.core.ui.window.SecureWindowSource
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
    fun `launch with biometric lock enabled applies secure window flag`() =
        runTest {
            seedSettings(biometricLockEnabled = true)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.waitForFlagSecure(expected = true)
            }
        }

    @Test
    fun `launch with biometric lock disabled leaves secure window flag cleared`() =
        runTest {
            seedSettings(biometricLockEnabled = false)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.waitForFlagSecure(expected = false)
            }
        }

    @Test
    fun `launch with recents hiding enabled applies secure flag without biometric lock`() =
        runTest {
            seedSettings(biometricLockEnabled = false, hideAppContentInRecents = true)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.waitForFlagSecure(expected = true)
            }
        }

    @Test
    fun `relaunch applies the current recents privacy setting`() =
        runTest {
            seedSettings(biometricLockEnabled = false, hideAppContentInRecents = true)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.waitForFlagSecure(expected = true)
            }

            seedSettings(biometricLockEnabled = false, hideAppContentInRecents = false)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.waitForFlagSecure(expected = false)
            }
        }

    @Test
    fun `older activity reconciles recents privacy changes from a newer activity`() =
        runTest {
            seedSettings(biometricLockEnabled = false, hideAppContentInRecents = true)

            launchLiveMainActivity(documentId = "older").use { olderScenario ->
                olderScenario.waitForFlagSecure(expected = true)

                launchLiveMainActivity(documentId = "newer").use { newerScenario ->
                    newerScenario.waitForFlagSecure(expected = true)

                    seedSettings(biometricLockEnabled = false, hideAppContentInRecents = false)

                    newerScenario.waitForFlagSecure(expected = false)
                    olderScenario.waitForFlagSecure(expected = false)
                }
            }
        }

    @Test
    fun `secure flag remains while any source is enabled and clears after the last source is removed`() =
        runTest {
            seedSettings(biometricLockEnabled = false)

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = SecureWindowController(activity.window)
                    controller.setSecure(SecureWindowSource.AppContent, enabled = false)
                    controller.setSecure(SecureWindowSource.LockOverlay, enabled = true)
                    assertWindowFlag(activity, expected = true)

                    controller.setSecure(SecureWindowSource.BiometricSetup, enabled = true)
                    controller.setSecure(SecureWindowSource.LockOverlay, enabled = false)
                    assertWindowFlag(activity, expected = true)

                    controller.setSecure(SecureWindowSource.BiometricSetup, enabled = false)
                    assertWindowFlag(activity, expected = false)
                }
            }
        }

    private fun launchLiveMainActivity(documentId: String): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("mymoney-test://main/$documentId"))
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT,
                )
        return ActivityScenario.launch(intent)
    }

    private suspend fun seedSettings(
        biometricLockEnabled: Boolean,
        hideAppContentInRecents: Boolean = false,
    ) {
        appSettingsRepository.reset()
        appSettingsRepository.update {
            it.copy(
                biometricLockEnabled = biometricLockEnabled,
                hideAppContentInRecents = hideAppContentInRecents,
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

    private fun assertWindowFlag(
        activity: MainActivity,
        expected: Boolean,
    ) {
        val actual = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        assertEquals(expected, actual)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
