package com.kshavrin.mymoney.core.testing.contract

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class AppSettingsRepositoryContract {
    protected abstract fun createRepository(): AppSettingsRepository

    @Test
    fun newRepositoryExposesDefaultSettings() =
        runTest {
            assertEquals(AppSettings(), createRepository().settings.first())
        }

    @Test
    fun updatePublishesTheTransformedSettings() =
        runTest {
            val repository = createRepository()

            repository.update {
                it.copy(
                    language = "ru",
                    themeMode = "dark",
                    defaultAccountId = 42L,
                    onboardingCompletedAt = 123L,
                    hideAppContentInRecents = true,
                )
            }

            assertEquals(
                AppSettings(
                    language = "ru",
                    themeMode = "dark",
                    defaultAccountId = 42L,
                    onboardingCompletedAt = 123L,
                    hideAppContentInRecents = true,
                ),
                repository.settings.first(),
            )
        }

    @Test
    fun resetRestoresDefaultSettings() =
        runTest {
            val repository = createRepository()
            repository.update { it.copy(language = "ru", firstPositiveSeen = true) }

            repository.reset()

            assertEquals(AppSettings(), repository.settings.first())
        }

    @Test
    fun firstPositiveSeenCannotChangeFromTrueToFalse() =
        runTest {
            val repository = createRepository()
            repository.update { it.copy(firstPositiveSeen = true) }

            val exception =
                runCatching {
                    repository.update { it.copy(firstPositiveSeen = false) }
                }.exceptionOrNull()

            assertTrue(exception is IllegalStateException)
            assertTrue(repository.settings.first().firstPositiveSeen)
        }
}
