package com.kshavrin.mymoney.feature.settings.theme

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeAppSettingsRepository

    private fun buildViewModel(stored: String = "system"): ThemeSettingsViewModel {
        repository = FakeAppSettingsRepository(AppSettings(themeMode = stored))
        return ThemeSettingsViewModel(repository)
    }

    @Test
    fun `initial state reflects the persisted system theme`() =
        runTest {
            val viewModel = buildViewModel(stored = "system")

            viewModel.state.test {
                assertEquals(ThemeMode.System, awaitItem().selected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial state reflects the persisted light theme`() =
        runTest {
            val viewModel = buildViewModel(stored = "light")

            viewModel.state.test {
                assertEquals(ThemeMode.Light, awaitItem().selected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial state reflects the persisted dark theme`() =
        runTest {
            val viewModel = buildViewModel(stored = "dark")

            viewModel.state.test {
                assertEquals(ThemeMode.Dark, awaitItem().selected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting System persists the exact stored value system`() =
        runTest {
            val viewModel = buildViewModel(stored = "dark")

            viewModel.onEvent(ThemeSettingsEvent.ModeSelected(ThemeMode.System))

            assertEquals("system", repository.settings.value.themeMode)
        }

    @Test
    fun `selecting Light persists the exact stored value light`() =
        runTest {
            val viewModel = buildViewModel(stored = "system")

            viewModel.onEvent(ThemeSettingsEvent.ModeSelected(ThemeMode.Light))

            assertEquals("light", repository.settings.value.themeMode)
        }

    @Test
    fun `selecting Dark persists the exact stored value dark`() =
        runTest {
            val viewModel = buildViewModel(stored = "system")

            viewModel.onEvent(ThemeSettingsEvent.ModeSelected(ThemeMode.Dark))

            assertEquals("dark", repository.settings.value.themeMode)
        }

    @Test
    fun `selecting a mode updates the state as the settings flow re-emits`() =
        runTest {
            val viewModel = buildViewModel(stored = "system")

            viewModel.state.test {
                assertEquals(ThemeMode.System, awaitItem().selected)

                viewModel.onEvent(ThemeSettingsEvent.ModeSelected(ThemeMode.Dark))
                assertEquals(ThemeMode.Dark, awaitItem().selected)

                viewModel.onEvent(ThemeSettingsEvent.ModeSelected(ThemeMode.Light))
                assertEquals(ThemeMode.Light, awaitItem().selected)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `persisting a mode leaves the other settings fields untouched`() =
        runTest {
            repository =
                FakeAppSettingsRepository(
                    AppSettings(themeMode = "system", language = "ru", soundEnabled = false),
                )
            val viewModel = ThemeSettingsViewModel(repository)

            viewModel.onEvent(ThemeSettingsEvent.ModeSelected(ThemeMode.Dark))

            val stored = repository.settings.value
            assertEquals("dark", stored.themeMode)
            assertEquals("ru", stored.language)
            assertEquals(false, stored.soundEnabled)
        }
}
