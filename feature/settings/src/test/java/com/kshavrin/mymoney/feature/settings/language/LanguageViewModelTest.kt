package com.kshavrin.mymoney.feature.settings.language

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.feature.settings.fake.FakeAppLocaleController
import com.kshavrin.mymoney.feature.settings.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeAppSettingsRepository
    private lateinit var localeController: FakeAppLocaleController

    private fun buildViewModel(stored: String = "system"): LanguageViewModel {
        repository = FakeAppSettingsRepository(AppSettings(language = stored))
        localeController = FakeAppLocaleController()
        return LanguageViewModel(repository, localeController)
    }

    @Test
    fun `initial state reflects the persisted system language`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.state.test {
            assertEquals(AppLanguage.System, awaitItem().selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state reflects the persisted english language`() = runTest {
        val viewModel = buildViewModel(stored = "en")

        viewModel.state.test {
            assertEquals(AppLanguage.English, awaitItem().selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state reflects the persisted russian language`() = runTest {
        val viewModel = buildViewModel(stored = "ru")

        viewModel.state.test {
            assertEquals(AppLanguage.Russian, awaitItem().selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting System persists the exact stored value system`() = runTest {
        val viewModel = buildViewModel(stored = "en")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.System))

        assertEquals("system", repository.settings.value.language)
    }

    @Test
    fun `selecting English persists the exact stored value en`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.English))

        assertEquals("en", repository.settings.value.language)
    }

    @Test
    fun `selecting Russian persists the exact stored value ru`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.Russian))

        assertEquals("ru", repository.settings.value.language)
    }

    @Test
    fun `selecting English applies the en locale tag`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.English))

        assertTrue(localeController.wasApplied())
        assertEquals("en", localeController.lastAppliedTag)
    }

    @Test
    fun `selecting Russian applies the ru locale tag`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.Russian))

        assertTrue(localeController.wasApplied())
        assertEquals("ru", localeController.lastAppliedTag)
    }

    @Test
    fun `selecting System applies the cleared locale tag`() = runTest {
        val viewModel = buildViewModel(stored = "ru")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.System))

        assertTrue(localeController.wasApplied())
        assertNull(localeController.lastAppliedTag)
    }

    @Test
    fun `selecting a language applies the locale exactly once`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.Russian))

        assertEquals(1, localeController.applyCount)
    }

    @Test
    fun `selecting a language updates the state as the settings flow re-emits`() = runTest {
        val viewModel = buildViewModel(stored = "system")

        viewModel.state.test {
            assertEquals(AppLanguage.System, awaitItem().selected)

            viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.Russian))
            assertEquals(AppLanguage.Russian, awaitItem().selected)

            viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.English))
            assertEquals(AppLanguage.English, awaitItem().selected)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persisting a language leaves the other settings fields untouched`() = runTest {
        repository = FakeAppSettingsRepository(
            AppSettings(language = "system", themeMode = "dark", soundEnabled = false),
        )
        localeController = FakeAppLocaleController()
        val viewModel = LanguageViewModel(repository, localeController)

        viewModel.onEvent(LanguageEvent.LanguageSelected(AppLanguage.Russian))

        val stored = repository.settings.value
        assertEquals("ru", stored.language)
        assertEquals("dark", stored.themeMode)
        assertEquals(false, stored.soundEnabled)
    }
}
