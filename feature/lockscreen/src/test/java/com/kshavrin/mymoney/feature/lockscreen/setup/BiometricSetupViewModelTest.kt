package com.kshavrin.mymoney.feature.lockscreen.setup

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.feature.lockscreen.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.lockscreen.fake.FakeBiometricAvailabilityChecker
import com.kshavrin.mymoney.feature.lockscreen.fake.FakeSecureStorage
import com.kshavrin.mymoney.feature.lockscreen.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appSettings: FakeAppSettingsRepository
    private lateinit var secureStorage: FakeSecureStorage
    private lateinit var availabilityChecker: FakeBiometricAvailabilityChecker
    private val pinHasher = PinHasher()

    private fun buildViewModel(
        initialSettings: AppSettings = AppSettings(),
        availability: BiometricAvailability = BiometricAvailability.Available,
        existingPinHash: String? = null,
    ): BiometricSetupViewModel {
        appSettings = FakeAppSettingsRepository(initialSettings)
        secureStorage = FakeSecureStorage().apply { seedPinHash(existingPinHash) }
        availabilityChecker = FakeBiometricAvailabilityChecker(availability)
        return BiometricSetupViewModel(
            appSettingsRepository = appSettings,
            secureStorage = secureStorage,
            availabilityChecker = availabilityChecker,
            pinHasher = pinHasher,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    // --- 1. init / availability -----------------------------------------------------------

    @Test
    fun `init derives the available state from the availability checker`() = runTest {
        val viewModel = buildViewModel(availability = BiometricAvailability.Available)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(BiometricAvailability.Available, state.availability)
            assertTrue(state.toggleEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init derives the no hardware state and disables the toggle`() = runTest {
        val viewModel = buildViewModel(availability = BiometricAvailability.NoHardware)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(BiometricAvailability.NoHardware, state.availability)
            assertFalse(state.toggleEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init derives the not enrolled state and disables the toggle`() = runTest {
        val viewModel = buildViewModel(availability = BiometricAvailability.NotEnrolled)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(BiometricAvailability.NotEnrolled, state.availability)
            assertFalse(state.toggleEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init reflects the persisted enabled flag and idle timeout from settings`() = runTest {
        val viewModel = buildViewModel(
            initialSettings = AppSettings(biometricLockEnabled = true, biometricIdleTimeoutSec = 300),
        )

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.enabled)
            assertEquals(300, state.idleTimeoutSec)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 2. toggle on ---------------------------------------------------------------------

    @Test
    fun `toggle on emits the launch biometric prompt action`() = runTest {
        val viewModel = buildViewModel(availability = BiometricAvailability.Available)

        viewModel.actions.test {
            viewModel.onEvent(BiometricSetupEvent.ToggleChanged(enabled = true))
            assertEquals(BiometricSetupAction.LaunchBiometricPrompt, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle on does not yet persist the enabled flag`() = runTest {
        val viewModel = buildViewModel(
            initialSettings = AppSettings(biometricLockEnabled = false),
            availability = BiometricAvailability.Available,
        )

        viewModel.onEvent(BiometricSetupEvent.ToggleChanged(enabled = true))

        assertFalse(appSettings.settings.value.biometricLockEnabled)
    }

    @Test
    fun `toggle on is ignored when biometrics are unavailable`() = runTest {
        val viewModel = buildViewModel(availability = BiometricAvailability.NoHardware)

        viewModel.actions.test {
            viewModel.onEvent(BiometricSetupEvent.ToggleChanged(enabled = true))
            expectNoEvents()
        }
        assertFalse(appSettings.settings.value.biometricLockEnabled)
    }

    // --- 3. biometric auth succeeded ------------------------------------------------------

    @Test
    fun `biometric auth succeeded persists the enabled flag`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricLockEnabled = false))

        viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded)

        assertTrue(appSettings.settings.value.biometricLockEnabled)
    }

    @Test
    fun `biometric auth succeeded opens pin setup when no pin hash is present`() = runTest {
        val viewModel = buildViewModel(existingPinHash = null)

        viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded)

        viewModel.state.test {
            assertTrue(awaitItem().pinSetupVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `biometric auth succeeded does not open pin setup when a pin hash already exists`() = runTest {
        val viewModel = buildViewModel(existingPinHash = pinHasher.hash("1234"))

        viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded)

        viewModel.state.test {
            assertFalse(awaitItem().pinSetupVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `biometric auth failed neither persists nor opens pin setup`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricLockEnabled = false))

        viewModel.onEvent(BiometricSetupEvent.BiometricAuthFailed)

        assertFalse(appSettings.settings.value.biometricLockEnabled)
        viewModel.state.test {
            assertFalse(awaitItem().pinSetupVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 4. toggle off --------------------------------------------------------------------

    @Test
    fun `toggle off persists the disabled flag`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricLockEnabled = true))

        viewModel.onEvent(BiometricSetupEvent.ToggleChanged(enabled = false))

        assertFalse(appSettings.settings.value.biometricLockEnabled)
    }

    @Test
    fun `toggle off emits no biometric prompt action`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricLockEnabled = true))

        viewModel.actions.test {
            viewModel.onEvent(BiometricSetupEvent.ToggleChanged(enabled = false))
            expectNoEvents()
        }
    }

    @Test
    fun `toggle off re-emits state with the enabled flag cleared`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricLockEnabled = true))

        viewModel.state.test {
            assertTrue(awaitItem().enabled)

            viewModel.onEvent(BiometricSetupEvent.ToggleChanged(enabled = false))
            assertFalse(awaitItem().enabled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 5. idle timeout selection --------------------------------------------------------

    @Test
    fun `idle timeout selection of 30 seconds is persisted`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricIdleTimeoutSec = 60))

        viewModel.onEvent(BiometricSetupEvent.IdleTimeoutSelected(30))

        assertEquals(30, appSettings.settings.value.biometricIdleTimeoutSec)
    }

    @Test
    fun `idle timeout selection of 300 seconds is persisted`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricIdleTimeoutSec = 60))

        viewModel.onEvent(BiometricSetupEvent.IdleTimeoutSelected(300))

        assertEquals(300, appSettings.settings.value.biometricIdleTimeoutSec)
    }

    @Test
    fun `idle timeout selection re-emits state with the new value`() = runTest {
        val viewModel = buildViewModel(initialSettings = AppSettings(biometricIdleTimeoutSec = 60))

        viewModel.state.test {
            assertEquals(60, awaitItem().idleTimeoutSec)

            viewModel.onEvent(BiometricSetupEvent.IdleTimeoutSelected(120))
            assertEquals(120, awaitItem().idleTimeoutSec)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 6. pin entry ---------------------------------------------------------------------

    @Test
    fun `a confirmed four digit pin is hashed and written to secure storage`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded)

        viewModel.onEvent(BiometricSetupEvent.PinEntered("1234"))

        assertEquals(1, secureStorage.writtenPinHashes.size)
        val written = secureStorage.writtenPinHashes.single()
        assertTrue(written != null && pinHasher.verify("1234", written))
    }

    @Test
    fun `the written pin hash is not the plaintext pin`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BiometricSetupEvent.PinEntered("1234"))

        val written = secureStorage.writtenPinHashes.single()
        assertFalse(written.isNullOrEmpty())
        assertFalse(written!!.contains("1234"))
    }

    @Test
    fun `a confirmed pin closes the pin setup dialog`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded)

        viewModel.onEvent(BiometricSetupEvent.PinEntered("1234"))

        viewModel.state.test {
            assertFalse(awaitItem().pinSetupVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pin shorter than four digits is not written`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BiometricSetupEvent.PinEntered("123"))

        assertTrue(secureStorage.writtenPinHashes.isEmpty())
    }

    @Test
    fun `a pin longer than four digits is not written`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(BiometricSetupEvent.PinEntered("12345"))

        assertTrue(secureStorage.writtenPinHashes.isEmpty())
    }

    // --- 7. pin setup dismissal -----------------------------------------------------------

    @Test
    fun `dismissing pin setup hides the dialog without writing a pin`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded)

        viewModel.onEvent(BiometricSetupEvent.PinSetupDismissed)

        viewModel.state.test {
            assertFalse(awaitItem().pinSetupVisible)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(secureStorage.writtenPinHashes.isEmpty())
    }
}
