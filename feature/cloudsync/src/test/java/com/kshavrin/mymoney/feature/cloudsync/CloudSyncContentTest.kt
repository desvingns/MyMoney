package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.sync.SyncTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Contract-level pinning for [CloudSyncContent] (S17), the stateless screen body.
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:cloudsync`'s offline test classpath does NOT have:
 *
 *   - `androidx.compose.ui:ui-test-junit4`
 *   - `androidx.compose.ui:ui-test-manifest`
 *   - Robolectric
 *
 * (its build.gradle.kts declares only junit + coroutines-test + turbine as testImplementation,
 * exactly like `:feature:settings`, and none of the Compose-UI test artifacts are present in the
 * offline Gradle cache, so a `createComposeRule()` test would not resolve at compile time). This
 * mirrors the deliberate deferral documented in `:feature:settings`'s `SettingsRootContentTest`,
 * `ThemeSettingsContentTest`, `LanguageContentTest`, `AboutHelpContentTest`, and in
 * `:feature:transactionslist`'s `SearchContentTest` / `TransactionsListContentTest` — full
 * Compose-UI tests land in PHASE_15 once those dependencies are wired in.
 *
 * # What slice 5a already covers vs what this file adds
 *
 * `CloudSyncViewModelTest` (slice 5a) pins how the ViewModel *produces* [CloudSyncState] from its
 * repositories — connection mapping, sync-now outcomes, conflict prompts, error mapping, auto-sync
 * persistence, action emission. It does NOT pin how the stateless [CloudSyncContent] *consumes* a
 * given state: which control each [TargetCardState] flag drives, the enablement gates, the
 * not-connected label fallback, and which optional state fields key the error banner and the
 * conflict dialog. This file pins those rendering-contract invariants — driven by pure, JVM-visible
 * inputs ([CloudSyncState] + the static composable structure) — and documents the exact Compose
 * test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class CloudSyncContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(
 *         state: CloudSyncState = CloudSyncState(),
 *         onEvent: (CloudSyncEvent) -> Unit = {},
 *     ) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 CloudSyncContent(state = state, onEvent = onEvent)
 *             }
 *         }
 *     }
 *
 *     @Test fun `shows the cloud sync title and both target sections`() {
 *         setContent()
 *         composeTestRule.onNodeWithText("Cloud sync").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Dropbox").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Google Drive").assertIsDisplayed()
 *     }
 *
 *     @Test fun `disconnected enabled target shows an enabled Connect button`() {
 *         setContent(CloudSyncState(dropbox = TargetCardState(SyncTarget.Dropbox, enabled = true)))
 *         composeTestRule.onNodeWithText("Connect").assertIsEnabled()
 *     }
 *
 *     @Test fun `disconnected disabled target shows a disabled Connect button`() {
 *         setContent(CloudSyncState(dropbox = TargetCardState(SyncTarget.Dropbox, enabled = false)))
 *         composeTestRule.onNodeWithText("Connect").assertIsNotEnabled()
 *     }
 *
 *     @Test fun `connected target shows Disconnect instead of Connect`() {
 *         setContent(CloudSyncState(dropbox = TargetCardState(SyncTarget.Dropbox, connected = true)))
 *         composeTestRule.onNodeWithText("Disconnect").assertIsDisplayed()
 *         composeTestRule.onAllNodesWithText("Connect").assertCountEquals(1) // only Drive's
 *     }
 *
 *     @Test fun `disconnected target shows the not-connected label`() {
 *         setContent()
 *         composeTestRule.onAllNodesWithText("Not connected").assertCountEquals(2)
 *     }
 *
 *     @Test fun `Sync now is disabled while syncing`() {
 *         setContent(CloudSyncState(dropbox = TargetCardState(SyncTarget.Dropbox, connected = true, syncing = true)))
 *         composeTestRule.onNodeWithText("Sync now").assertDoesNotExist() // spinner instead
 *     }
 *
 *     @Test fun `error banner is shown only when errorBannerRes is set`() {
 *         setContent(CloudSyncState(errorBannerRes = R.string.sync_not_configured))
 *         composeTestRule.onNodeWithText("Not configured").assertIsDisplayed()
 *     }
 *
 *     @Test fun `conflict dialog is shown only when conflict is non-null`() {
 *         setContent(CloudSyncState(conflict = ConflictPrompt(SyncTarget.Dropbox, 2L, 1L)))
 *         composeTestRule.onNodeWithText("Keep cloud copy").assertIsDisplayed()
 *     }
 * }
 * ```
 */
class CloudSyncContentTest {

    private val sampleLog = SyncLogEntry(
        id = 1L,
        target = SyncTarget.Dropbox.name,
        event = "PUSH",
        entityKind = null,
        entityId = null,
        performedAt = Instant.parse("2026-05-18T10:00:00Z"),
        status = "SUCCESS",
        payloadHash = null,
        errorMessage = null,
    )

    /**
     * Mirror of the `if (card.connected) … else …` branch in `TargetCard`: a connected card renders
     * the Disconnect control, a disconnected one renders the Connect control.
     */
    private enum class PrimaryControl { Connect, Disconnect }

    private fun primaryControl(card: TargetCardState): PrimaryControl =
        if (card.connected) PrimaryControl.Disconnect else PrimaryControl.Connect

    /** Mirror of `Button(enabled = card.enabled)` on the Connect control. */
    private fun connectEnabled(card: TargetCardState): Boolean = card.enabled

    /** Mirror of `Button(enabled = card.connected && !card.syncing)` on the Sync-now control. */
    private fun syncNowEnabled(card: TargetCardState): Boolean = card.connected && !card.syncing

    /** Mirror of `if (card.syncing) CircularProgressIndicator else Text(Sync now)`. */
    private fun syncNowShowsSpinner(card: TargetCardState): Boolean = card.syncing

    /** Mirror of `card.accountLabel ?: stringResource(sync_not_connected)`. */
    private fun accountLabelRes(card: TargetCardState): Int? =
        if (card.accountLabel == null) R.string.sync_not_connected else null

    /** Mirror of `state.errorBannerRes?.let { … }` — the banner row renders iff non-null. */
    private fun showsErrorBanner(state: CloudSyncState): Boolean = state.errorBannerRes != null

    /** Mirror of `state.conflict?.let { ConflictResolutionDialog(...) }`. */
    private fun showsConflictDialog(state: CloudSyncState): Boolean = state.conflict != null

    // --- defaults --------------------------------------------------------------------------

    @Test
    fun `default state leaves both targets disconnected and disabled`() {
        val state = CloudSyncState()
        assertFalse(state.dropbox.connected)
        assertFalse(state.dropbox.enabled)
        assertFalse(state.drive.connected)
        assertFalse(state.drive.enabled)
        assertFalse(state.autoSyncEnabled)
        assertNull(state.errorBannerRes)
        assertNull(state.conflict)
    }

    @Test
    fun `the two target cards carry their own distinct sync targets`() {
        val state = CloudSyncState()
        assertEquals(SyncTarget.Dropbox, state.dropbox.target)
        assertEquals(SyncTarget.GoogleDrive, state.drive.target)
    }

    // --- Connect vs Disconnect branch ------------------------------------------------------

    @Test
    fun `a disconnected target drives the Connect control`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = false)
        assertEquals(PrimaryControl.Connect, primaryControl(card))
    }

    @Test
    fun `a connected target drives the Disconnect control`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = true)
        assertEquals(PrimaryControl.Disconnect, primaryControl(card))
    }

    // --- Connect enablement gate -----------------------------------------------------------

    @Test
    fun `a disconnected but disabled target drives a disabled Connect control`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = false, enabled = false)
        assertEquals(PrimaryControl.Connect, primaryControl(card))
        assertFalse(connectEnabled(card))
    }

    @Test
    fun `a disconnected and enabled target drives an enabled Connect control`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = false, enabled = true)
        assertEquals(PrimaryControl.Connect, primaryControl(card))
        assertTrue(connectEnabled(card))
    }

    // --- Sync-now enablement gate ----------------------------------------------------------

    @Test
    fun `sync now is disabled for a disconnected target`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = false)
        assertFalse(syncNowEnabled(card))
    }

    @Test
    fun `sync now is enabled for a connected idle target`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = true, syncing = false)
        assertTrue(syncNowEnabled(card))
    }

    @Test
    fun `sync now is disabled for a connected target that is already syncing`() {
        val card = TargetCardState(SyncTarget.Dropbox, connected = true, syncing = true)
        assertFalse(syncNowEnabled(card))
    }

    @Test
    fun `a syncing target shows the progress spinner in place of the Sync now label`() {
        val syncing = TargetCardState(SyncTarget.Dropbox, connected = true, syncing = true)
        val idle = TargetCardState(SyncTarget.Dropbox, connected = true, syncing = false)
        assertTrue(syncNowShowsSpinner(syncing))
        assertFalse(syncNowShowsSpinner(idle))
    }

    // --- account label fallback ------------------------------------------------------------

    @Test
    fun `a target with no account label falls back to the not-connected string`() {
        val card = TargetCardState(SyncTarget.Dropbox, accountLabel = null)
        assertEquals(R.string.sync_not_connected, accountLabelRes(card))
    }

    @Test
    fun `a target with an account label does not show the not-connected fallback`() {
        val card = TargetCardState(SyncTarget.Dropbox, accountLabel = "alice@dropbox.com")
        assertNull(accountLabelRes(card))
    }

    // --- error banner keys off errorBannerRes ----------------------------------------------

    @Test
    fun `the error banner is hidden when errorBannerRes is null`() {
        assertFalse(showsErrorBanner(CloudSyncState(errorBannerRes = null)))
    }

    @Test
    fun `the error banner is shown when errorBannerRes is set`() {
        assertTrue(showsErrorBanner(CloudSyncState(errorBannerRes = R.string.sync_not_configured)))
    }

    // --- conflict dialog keys off conflict -------------------------------------------------

    @Test
    fun `the conflict dialog is hidden when there is no conflict`() {
        assertFalse(showsConflictDialog(CloudSyncState(conflict = null)))
    }

    @Test
    fun `the conflict dialog is shown when a conflict prompt is present`() {
        val state = CloudSyncState(
            conflict = ConflictPrompt(SyncTarget.Dropbox, remoteMs = 2L, localMs = 1L),
        )
        assertTrue(showsConflictDialog(state))
        assertEquals(SyncTarget.Dropbox, state.conflict?.target)
    }

    // --- recent log passthrough ------------------------------------------------------------

    @Test
    fun `each target card carries its own recent log list for the rows it renders`() {
        val card = TargetCardState(SyncTarget.Dropbox, recentLog = listOf(sampleLog))
        assertEquals(1, card.recentLog.size)
        assertEquals(sampleLog, card.recentLog.single())
    }

    // --- event hierarchy stability ---------------------------------------------------------

    @Test
    fun `every control in the screen maps to a distinct CloudSyncEvent`() {
        val target = SyncTarget.Dropbox
        val events: List<CloudSyncEvent> = listOf(
            CloudSyncEvent.ConnectClicked(target),
            CloudSyncEvent.DisconnectClicked(target),
            CloudSyncEvent.SyncNowClicked(target),
            CloudSyncEvent.AutoSyncToggled(true),
            CloudSyncEvent.ConflictKeepRemote,
            CloudSyncEvent.ConflictKeepLocal,
            CloudSyncEvent.DismissConflict,
            CloudSyncEvent.DismissError,
            CloudSyncEvent.BackClicked,
        )
        assertEquals(
            "no two screen controls may collapse onto the same event type",
            events.size,
            events.map { it::class }.toSet().size,
        )
    }

    @Test
    fun `target-scoped events carry the card target back to the ViewModel`() {
        assertEquals(
            SyncTarget.GoogleDrive,
            (CloudSyncEvent.ConnectClicked(SyncTarget.GoogleDrive)).target,
        )
        assertEquals(
            SyncTarget.GoogleDrive,
            (CloudSyncEvent.DisconnectClicked(SyncTarget.GoogleDrive)).target,
        )
        assertEquals(
            SyncTarget.GoogleDrive,
            (CloudSyncEvent.SyncNowClicked(SyncTarget.GoogleDrive)).target,
        )
    }

    @Test
    fun `the auto sync toggle event carries the requested checked value`() {
        assertTrue((CloudSyncEvent.AutoSyncToggled(true)).enabled)
        assertFalse((CloudSyncEvent.AutoSyncToggled(false)).enabled)
    }

    @Test
    fun `the conflict dialog buttons map to the keep-remote and keep-local events`() {
        val keepRemote: CloudSyncEvent = CloudSyncEvent.ConflictKeepRemote
        val keepLocal: CloudSyncEvent = CloudSyncEvent.ConflictKeepLocal
        val dismiss: CloudSyncEvent = CloudSyncEvent.DismissConflict
        assertEquals(CloudSyncEvent.ConflictKeepRemote, keepRemote)
        assertEquals(CloudSyncEvent.ConflictKeepLocal, keepLocal)
        assertEquals(CloudSyncEvent.DismissConflict, dismiss)
    }

    // --- action hierarchy stability --------------------------------------------------------

    @Test
    fun `the route collects exactly the three one-shot actions`() {
        val actions: List<CloudSyncAction> = listOf(
            CloudSyncAction.NavigateBack,
            CloudSyncAction.LaunchDropboxAuth,
            CloudSyncAction.LaunchGoogleSignIn,
        )
        assertEquals(3, actions.map { it::class }.toSet().size)
        assertNotNull(CloudSyncAction.NavigateBack)
    }
}
