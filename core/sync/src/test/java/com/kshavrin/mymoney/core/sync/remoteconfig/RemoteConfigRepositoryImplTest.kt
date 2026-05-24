package com.kshavrin.mymoney.core.sync.remoteconfig

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In the :core:sync unit-test variant BuildConfig.HAS_FIREBASE is false
 * (the firebase.enabled gradle property is unset), so the Firebase branch is
 * never reached and no Android runtime / Robolectric is required.
 */
class RemoteConfigRepositoryImplTest {

    private val repository = RemoteConfigRepositoryImpl()

    @Test
    fun `refresh is a no-op success when Firebase is disabled`() = runTest {
        val result = repository.refresh()

        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrNull())
    }

    @Test
    fun `recurringTemplatesEnabled returns in-app default true`() {
        assertTrue(repository.recurringTemplatesEnabled())
    }

    @Test
    fun `budgetModeEnabled returns in-app default true`() {
        assertTrue(repository.budgetModeEnabled())
    }

    @Test
    fun `dropboxSyncEnabled returns in-app default false`() {
        assertFalse(repository.dropboxSyncEnabled())
    }

    @Test
    fun `gdriveSyncEnabled returns in-app default false`() {
        assertFalse(repository.gdriveSyncEnabled())
    }

    @Test
    fun `minSupportedVersionCode returns in-app default 1`() {
        assertEquals(1L, repository.minSupportedVersionCode())
    }

    @Test
    fun `aestheticSoundPack returns in-app default pack`() {
        assertEquals("default", repository.aestheticSoundPack())
    }
}
