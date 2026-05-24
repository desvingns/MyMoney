package com.kshavrin.mymoney.feature.settings.root

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.settings.language.AppLanguage
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
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

    private fun buildViewModel(initial: AppSettings = AppSettings()): SettingsViewModel {
        repository = FakeAppSettingsRepository(initial)
        return SettingsViewModel(repository)
    }

    @Test
    fun `initial state reflects persisted theme and language`() = runTest {
        val viewModel = buildViewModel(AppSettings(themeMode = "dark", language = "ru"))

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(ThemeMode.Dark, state.themeMode)
            assertEquals(AppLanguage.Russian, state.language)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state reflects persisted sound and haptic flags`() = runTest {
        val viewModel = buildViewModel(AppSettings(soundEnabled = false, hapticEnabled = false))

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.soundEnabled)
            assertFalse(state.hapticEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state defaults sound and haptic to enabled`() = runTest {
        val viewModel = buildViewModel(AppSettings())

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.soundEnabled)
            assertTrue(state.hapticEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SoundToggled off persists the disabled value`() = runTest {
        val viewModel = buildViewModel(AppSettings(soundEnabled = true))

        viewModel.onEvent(SettingsEvent.SoundToggled(false))

        assertFalse(repository.settings.value.soundEnabled)
    }

    @Test
    fun `SoundToggled on persists the enabled value`() = runTest {
        val viewModel = buildViewModel(AppSettings(soundEnabled = false))

        viewModel.onEvent(SettingsEvent.SoundToggled(true))

        assertTrue(repository.settings.value.soundEnabled)
    }

    @Test
    fun `SoundToggled re-emits state with the flipped value`() = runTest {
        val viewModel = buildViewModel(AppSettings(soundEnabled = true))

        viewModel.state.test {
            assertTrue(awaitItem().soundEnabled)

            viewModel.onEvent(SettingsEvent.SoundToggled(false))
            assertFalse(awaitItem().soundEnabled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `HapticToggled off persists the disabled value`() = runTest {
        val viewModel = buildViewModel(AppSettings(hapticEnabled = true))

        viewModel.onEvent(SettingsEvent.HapticToggled(false))

        assertFalse(repository.settings.value.hapticEnabled)
    }

    @Test
    fun `HapticToggled on persists the enabled value`() = runTest {
        val viewModel = buildViewModel(AppSettings(hapticEnabled = false))

        viewModel.onEvent(SettingsEvent.HapticToggled(true))

        assertTrue(repository.settings.value.hapticEnabled)
    }

    @Test
    fun `HapticToggled re-emits state with the flipped value`() = runTest {
        val viewModel = buildViewModel(AppSettings(hapticEnabled = true))

        viewModel.state.test {
            assertTrue(awaitItem().hapticEnabled)

            viewModel.onEvent(SettingsEvent.HapticToggled(false))
            assertFalse(awaitItem().hapticEnabled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling sound leaves haptic untouched`() = runTest {
        val viewModel = buildViewModel(AppSettings(soundEnabled = true, hapticEnabled = true))

        viewModel.onEvent(SettingsEvent.SoundToggled(false))

        assertTrue(repository.settings.value.hapticEnabled)
    }

    @Test
    fun `toggling haptic leaves sound untouched`() = runTest {
        val viewModel = buildViewModel(AppSettings(soundEnabled = true, hapticEnabled = true))

        viewModel.onEvent(SettingsEvent.HapticToggled(false))

        assertTrue(repository.settings.value.soundEnabled)
    }

    @Test
    fun `toggling sound leaves all unrelated settings fields untouched`() = runTest {
        val initial = AppSettings(
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
    fun `toggling haptic leaves all unrelated settings fields untouched`() = runTest {
        val initial = AppSettings(
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
    fun `state re-emits when the settings flow changes externally`() = runTest {
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
}
