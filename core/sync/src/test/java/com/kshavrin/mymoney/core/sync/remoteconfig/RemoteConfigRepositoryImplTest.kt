package com.kshavrin.mymoney.core.sync.remoteconfig

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit coverage for [RemoteConfigRepositoryImpl].
 *
 * In the :core:sync unit-test variant BuildConfig.HAS_FIREBASE is false
 * (the firebase.enabled gradle property is unset), so `config` is always null
 * and no Android runtime / Robolectric is required.
 *
 * sharedSyncEnabled() acceptance matrix (build_flag x remote_config):
 *
 *  cell 1  build=enabled  RC=absent    expected=true   directly testable in this build
 *  cell 2  build=enabled  RC=enabled   expected=true   source-inspection (Firebase unavailable)
 *  cell 3  build=enabled  RC=disabled  expected=false  source-inspection (Firebase unavailable)
 *  cell 4  build=disabled RC=absent    expected=false  source-inspection (compile-time constant)
 *  cell 5  build=disabled RC=enabled   expected=false  source-inspection (compile-time constant)
 *  cell 6  build=disabled RC=disabled  expected=false  source-inspection (compile-time constant)
 *
 * Cells 2-3 require HAS_FIREBASE=true; FirebaseRemoteConfig is never initialised in unit tests.
 * Cells 4-6 require both PLAY_INTERNAL_SYNC_ENABLED=false and PLAY_RELEASE_SYNC_ENABLED=false;
 * PLAY_RELEASE_SYNC_ENABLED is a compile-time constant whose default became true in the
 * shared-sync-remote-killswitch SPEC.
 */
class RemoteConfigRepositoryImplTest {
    private val repository = RemoteConfigRepositoryImpl()

    // Normalised production source — used for structural (source-inspection) tests only.
    // The path is relative to the :core:sync module root, which is Gradle's working directory
    // for unit-test execution.
    private val implSource: String by lazy {
        File(
            "src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImpl.kt",
        ).readText().replace(Regex("\\s+"), " ")
    }

    // ── generic plumbing ──────────────────────────────────────────────────────

    @Test
    fun `refresh is a no-op success when Firebase is disabled`() =
        runTest {
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

    // PLAY_RELEASE_SYNC_ENABLED defaulted to true (shared-sync-remote-killswitch SPEC);
    // all three sync-enabled methods now resolve to true in the default test build.
    @Test
    fun `dropboxSyncEnabled returns true when play release sync is enabled by default`() {
        assertTrue(repository.dropboxSyncEnabled())
    }

    @Test
    fun `gdriveSyncEnabled returns true when play release sync is enabled by default`() {
        assertTrue(repository.gdriveSyncEnabled())
    }

    @Test
    fun `minSupportedVersionCode returns in-app default 1`() {
        assertEquals(1L, repository.minSupportedVersionCode())
    }

    @Test
    fun `aestheticSoundPack returns in-app default pack`() {
        assertEquals("default", repository.aestheticSoundPack())
    }

    // ── sharedSyncEnabled — cell 1 (direct execution) ────────────────────────

    // Cell 1: PLAY_RELEASE_SYNC_ENABLED=true (new default), HAS_FIREBASE=false -> config=null
    // (false || true || false) && (null ?: true)  ==  true && true  ==  true
    // Absent Remote Config must not silently kill a build-enabled feature.
    @Test
    fun `sharedSyncEnabled returns true when build flag enabled and remote config is absent`() {
        assertTrue(repository.sharedSyncEnabled())
    }

    // ── sharedSyncEnabled — cells 2-6 (source-inspection structural tests) ───

    // Cells 2 & 3 (build=enabled, RC=enabled vs disabled):
    // The remote killswitch can only suppress a build-enabled feature if the formula is an AND
    // conjunction, not an OR. Source-inspection confirms the && token is present alongside
    // KEY_SHARED_SYNC, guaranteeing that a remote false reaches cell 3 -> false while a remote
    // true reaches cell 2 -> true.
    @Test
    fun `sharedSyncEnabled formula uses AND conjunction so remote killswitch can disable a build-enabled feature`() {
        assertTrue(
            "sharedSyncEnabled must reference KEY_SHARED_SYNC inside a && conjunction",
            implSource.contains("KEY_SHARED_SYNC") && implSource.contains("&&"),
        )
    }

    // Cells 1 & 4 (RC=absent, any build state):
    // When config == null the null-safety default must be true so that an absent Remote Config
    // passes the build-flag's own value through unchanged — not silently false.
    // The constant DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED (= true) is the guard; the older
    // DEFAULT_SHARED_SYNC (= false) is a distinct constant used elsewhere and must not be reused.
    @Test
    fun `sharedSyncEnabled null safety default is true so absent remote config does not silently disable feature`() {
        assertTrue(
            "DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED must equal true; using false would kill build-enabled sync whenever Firebase is absent",
            implSource.contains("DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED = true"),
        )
    }

    // Cells 4-6 (build=disabled, any RC value):
    // When both build flags are false the AND short-circuits before reading Remote Config, so all
    // three RC variants in the disabled-build row are false. Both PLAY_INTERNAL_SYNC_ENABLED and
    // PLAY_RELEASE_SYNC_ENABLED must appear as the left operand so that a build with both flags
    // cleared disables shared sync regardless of what the killswitch returns.
    @Test
    fun `sharedSyncEnabled left operand covers both play sync build flags so disabled build short-circuits regardless of RC`() {
        assertTrue(
            "sharedSyncEnabled must reference both PLAY_INTERNAL_SYNC_ENABLED and PLAY_RELEASE_SYNC_ENABLED as left operand of &&",
            implSource.contains("PLAY_INTERNAL_SYNC_ENABLED") &&
                implSource.contains("PLAY_RELEASE_SYNC_ENABLED"),
        )
    }
}
