package com.kshavrin.mymoney

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppThemeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeAppSettingsRepository

    private fun buildViewModel(stored: String = "system"): AppThemeViewModel {
        repository = FakeAppSettingsRepository(AppSettings(themeMode = stored))
        return AppThemeViewModel(repository)
    }

    @Test
    fun `themeMode starts at System before any subscriber collects`() {
        val viewModel = buildViewModel(stored = "dark")

        assertEquals(ThemeMode.System, viewModel.themeMode.value)
    }

    @Test
    fun `themeMode maps the persisted light setting once collected`() =
        runTest {
            val viewModel = buildViewModel(stored = "light")

            viewModel.themeMode.test {
                assertEquals(ThemeMode.Light, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `themeMode maps the persisted dark setting once collected`() =
        runTest {
            val viewModel = buildViewModel(stored = "dark")

            viewModel.themeMode.test {
                assertEquals(ThemeMode.Dark, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `themeMode maps an unknown persisted setting to System`() =
        runTest {
            val viewModel = buildViewModel(stored = "neon")

            viewModel.themeMode.test {
                assertEquals(ThemeMode.System, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `themeMode re-emits live when the persisted setting changes`() =
        runTest {
            val viewModel = buildViewModel(stored = "system")

            viewModel.themeMode.test {
                assertEquals(ThemeMode.System, awaitItem())

                repository.update { it.copy(themeMode = "dark") }
                assertEquals(ThemeMode.Dark, awaitItem())

                repository.update { it.copy(themeMode = "light") }
                assertEquals(ThemeMode.Light, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
