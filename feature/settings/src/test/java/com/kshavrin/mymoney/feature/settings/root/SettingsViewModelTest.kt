package com.kshavrin.mymoney.feature.settings.root

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.reset.FactoryResetGateway
import com.kshavrin.mymoney.core.domain.usecase.FactoryResetUseCase
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.language.AppLanguage
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeAppSettingsRepository
    private lateinit var fakeGateway: FakeFactoryResetGateway

    private fun buildViewModel(initial: AppSettings = AppSettings()): SettingsViewModel {
        repository = FakeAppSettingsRepository(initial)
        fakeGateway = FakeFactoryResetGateway()
        return SettingsViewModel(repository, FactoryResetUseCase(fakeGateway))
    }

    // ─── existing settings tests ──────────────────────────────────────────────

    @Test
    fun `initial state reflects persisted theme and language`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(themeMode = "dark", language = "ru"))

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(ThemeMode.Dark, state.themeMode)
                assertEquals(AppLanguage.Russian, state.language)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial state reflects persisted sound and haptic flags`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(soundEnabled = false, hapticEnabled = false))

            viewModel.state.test {
                val state = awaitItem()
                assertFalse(state.soundEnabled)
                assertFalse(state.hapticEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial state reflects persisted recents privacy flag`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(hideAppContentInRecents = true))

            viewModel.state.test {
                assertTrue(awaitItem().hideAppContentInRecents)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial state defaults recents privacy flag to disabled`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.state.test {
                assertFalse(awaitItem().hideAppContentInRecents)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial state defaults sound and haptic to enabled`() =
        runTest {
            val viewModel = buildViewModel(AppSettings())

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.soundEnabled)
                assertTrue(state.hapticEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SoundToggled off persists the disabled value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(soundEnabled = true))

            viewModel.onEvent(SettingsEvent.SoundToggled(false))

            assertFalse(repository.settings.value.soundEnabled)
        }

    @Test
    fun `SoundToggled on persists the enabled value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(soundEnabled = false))

            viewModel.onEvent(SettingsEvent.SoundToggled(true))

            assertTrue(repository.settings.value.soundEnabled)
        }

    @Test
    fun `SoundToggled re-emits state with the flipped value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(soundEnabled = true))

            viewModel.state.test {
                assertTrue(awaitItem().soundEnabled)

                viewModel.onEvent(SettingsEvent.SoundToggled(false))
                assertFalse(awaitItem().soundEnabled)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `HapticToggled off persists the disabled value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(hapticEnabled = true))

            viewModel.onEvent(SettingsEvent.HapticToggled(false))

            assertFalse(repository.settings.value.hapticEnabled)
        }

    @Test
    fun `HapticToggled on persists the enabled value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(hapticEnabled = false))

            viewModel.onEvent(SettingsEvent.HapticToggled(true))

            assertTrue(repository.settings.value.hapticEnabled)
        }

    @Test
    fun `HapticToggled re-emits state with the flipped value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(hapticEnabled = true))

            viewModel.state.test {
                assertTrue(awaitItem().hapticEnabled)

                viewModel.onEvent(SettingsEvent.HapticToggled(false))
                assertFalse(awaitItem().hapticEnabled)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `HideAppContentInRecentsToggled on persists and re-emits the enabled value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(hideAppContentInRecents = false))

            viewModel.state.test {
                assertFalse(awaitItem().hideAppContentInRecents)

                viewModel.onEvent(SettingsEvent.HideAppContentInRecentsToggled(true))

                assertTrue(awaitItem().hideAppContentInRecents)
                assertTrue(repository.settings.value.hideAppContentInRecents)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `HideAppContentInRecentsToggled off persists and re-emits the disabled value`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(hideAppContentInRecents = true))

            viewModel.state.test {
                assertTrue(awaitItem().hideAppContentInRecents)

                viewModel.onEvent(SettingsEvent.HideAppContentInRecentsToggled(false))

                assertFalse(awaitItem().hideAppContentInRecents)
                assertFalse(repository.settings.value.hideAppContentInRecents)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggling recents privacy leaves unrelated settings untouched`() =
        runTest {
            val initial =
                AppSettings(
                    language = "ru",
                    themeMode = "dark",
                    hideAppContentInRecents = false,
                    soundEnabled = false,
                    hapticEnabled = false,
                    defaultAccountId = 7L,
                    defaultPeriod = "week",
                    budgetModeEnabled = false,
                )
            val viewModel = buildViewModel(initial)

            viewModel.onEvent(SettingsEvent.HideAppContentInRecentsToggled(true))

            assertEquals(initial.copy(hideAppContentInRecents = true), repository.settings.value)
        }

    @Test
    fun `toggling sound leaves haptic untouched`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(soundEnabled = true, hapticEnabled = true))

            viewModel.onEvent(SettingsEvent.SoundToggled(false))

            assertTrue(repository.settings.value.hapticEnabled)
        }

    @Test
    fun `toggling haptic leaves sound untouched`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(soundEnabled = true, hapticEnabled = true))

            viewModel.onEvent(SettingsEvent.HapticToggled(false))

            assertTrue(repository.settings.value.soundEnabled)
        }

    @Test
    fun `toggling sound leaves all unrelated settings fields untouched`() =
        runTest {
            val initial =
                AppSettings(
                    language = "ru",
                    themeMode = "dark",
                    soundEnabled = true,
                    hapticEnabled = false,
                    defaultAccountId = 7L,
                    defaultPeriod = "week",
                    budgetModeEnabled = false,
                )
            val viewModel = buildViewModel(initial)

            viewModel.onEvent(SettingsEvent.SoundToggled(false))

            val stored = repository.settings.value
            assertEquals(initial.copy(soundEnabled = false), stored)
        }

    @Test
    fun `toggling haptic leaves all unrelated settings fields untouched`() =
        runTest {
            val initial =
                AppSettings(
                    language = "en",
                    themeMode = "light",
                    soundEnabled = false,
                    hapticEnabled = true,
                    defaultAccountId = 3L,
                    defaultPeriod = "year",
                    budgetModeEnabled = false,
                )
            val viewModel = buildViewModel(initial)

            viewModel.onEvent(SettingsEvent.HapticToggled(false))

            val stored = repository.settings.value
            assertEquals(initial.copy(hapticEnabled = false), stored)
        }

    @Test
    fun `state re-emits when the settings flow changes externally`() =
        runTest {
            val viewModel = buildViewModel(AppSettings(themeMode = "system", soundEnabled = true))

            viewModel.state.test {
                val first = awaitItem()
                assertEquals(ThemeMode.System, first.themeMode)
                assertTrue(first.soundEnabled)

                repository.seed(AppSettings(themeMode = "light", soundEnabled = false))

                val second = awaitItem()
                assertEquals(ThemeMode.Light, second.themeMode)
                assertFalse(second.soundEnabled)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── factory reset double-confirm gate ────────────────────────────────────

    @Test
    fun `FactoryResetRequested transitions step to Confirm and clears confirmText`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(SettingsEvent.FactoryResetRequested)

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(FactoryResetStep.Confirm, state.factoryResetStep)
                assertEquals("", state.factoryResetConfirmText)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `FactoryResetContinued from Confirm transitions step to TypeWord`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)

            viewModel.onEvent(SettingsEvent.FactoryResetContinued)

            viewModel.state.test {
                assertEquals(FactoryResetStep.TypeWord, awaitItem().factoryResetStep)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `FactoryResetDismissed from Confirm returns to Idle and clears confirmText`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged("partial"))

            viewModel.onEvent(SettingsEvent.FactoryResetDismissed)

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(FactoryResetStep.Idle, state.factoryResetStep)
                assertEquals("", state.factoryResetConfirmText)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `FactoryResetDismissed from TypeWord returns to Idle and clears confirmText`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged("RESE"))

            viewModel.onEvent(SettingsEvent.FactoryResetDismissed)

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(FactoryResetStep.Idle, state.factoryResetStep)
                assertEquals("", state.factoryResetConfirmText)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `FactoryResetConfirmed with empty text does not invoke the use case`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)

            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            assertEquals("use case must not be called when confirmText is empty", 0, fakeGateway.detachCount)
        }

    @Test
    fun `FactoryResetConfirmed with wrong case does not invoke the use case`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged("reset"))

            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            assertEquals("use case must not be called when text is lowercase", 0, fakeGateway.detachCount)
        }

    @Test
    fun `FactoryResetConfirmed with partial text does not invoke the use case`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged("RES"))

            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            assertEquals("use case must not be called for partial text", 0, fakeGateway.detachCount)
        }

    @Test
    fun `FactoryResetConfirmed at Confirm step (not TypeWord) does not invoke the use case`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)

            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            assertEquals("use case must not fire when step is Confirm not TypeWord", 0, fakeGateway.detachCount)
        }

    @Test
    fun `FactoryResetConfirmed with exact RESET text invokes the use case exactly once`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged(FACTORY_RESET_CONFIRM_WORD))

            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            assertEquals("use case must be called exactly once", 1, fakeGateway.detachCount)
        }

    @Test
    fun `successful factory reset emits RestartToOnboardingAfterReset with hadFailures false`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged(FACTORY_RESET_CONFIRM_WORD))

            viewModel.actions.test {
                viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)
                val action = awaitItem()
                assertTrue(action is SettingsAction.RestartToOnboardingAfterReset)
                assertFalse((action as SettingsAction.RestartToOnboardingAfterReset).hadFailures)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `factory reset use case failure emits RestartToOnboardingAfterReset with hadFailures true`() =
        runTest {
            val viewModel = buildViewModel()
            fakeGateway.willThrow(RuntimeException("detach failed"))
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged(FACTORY_RESET_CONFIRM_WORD))

            viewModel.actions.test {
                viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)
                val action = awaitItem()
                assertTrue(action is SettingsAction.RestartToOnboardingAfterReset)
                assertTrue((action as SettingsAction.RestartToOnboardingAfterReset).hadFailures)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `factory reset state returns to Idle after reset completes`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged(FACTORY_RESET_CONFIRM_WORD))

            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(FactoryResetStep.Idle, state.factoryResetStep)
                assertEquals("", state.factoryResetConfirmText)
                assertFalse(state.factoryResetInProgress)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `second FactoryResetConfirmed while in progress does not double-invoke the use case`() =
        runTest {
            val deferredResume = CompletableDeferred<Unit>()
            val slowGateway =
                object : FactoryResetGateway {
                    var detachCount = 0

                    override suspend fun detachCloudSync() {
                        detachCount++
                        deferredResume.await()
                    }

                    override suspend fun wipeLocalData() = Unit

                    override suspend fun resetSettings() = Unit

                    override suspend fun clearSecrets() = Unit
                }
            val viewModel = SettingsViewModel(FakeAppSettingsRepository(), FactoryResetUseCase(slowGateway))
            viewModel.onEvent(SettingsEvent.FactoryResetRequested)
            viewModel.onEvent(SettingsEvent.FactoryResetContinued)
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmTextChanged(FACTORY_RESET_CONFIRM_WORD))
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)
            // coroutine is now suspended inside detachCloudSync — factoryResetInProgress == true
            viewModel.onEvent(SettingsEvent.FactoryResetConfirmed)

            deferredResume.complete(Unit)

            assertEquals("use case must be invoked exactly once despite two Confirmed events", 1, slowGateway.detachCount)
        }

    // ─── inner fake ───────────────────────────────────────────────────────────

    private class FakeFactoryResetGateway : FactoryResetGateway {
        var detachCount = 0
        private var throwable: Throwable? = null

        fun willThrow(t: Throwable) {
            throwable = t
        }

        override suspend fun detachCloudSync() {
            detachCount++
            throwable?.let { throw it }
        }

        override suspend fun wipeLocalData() = Unit

        override suspend fun resetSettings() = Unit

        override suspend fun clearSecrets() = Unit
    }
}
