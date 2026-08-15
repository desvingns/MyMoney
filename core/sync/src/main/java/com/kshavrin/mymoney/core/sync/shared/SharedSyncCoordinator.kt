package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

data class SharedWorkspaceSummary(
    val id: String,
    val name: String,
)

data class SharedWorkspaceInvite(
    val token: String,
)

/**
 * Server-verified membership role for the active Shared workspace.
 *
 * [Unknown] means the current signed-in user has no confirmable active self-membership on the
 * server (absent or unverifiable). It must never be collapsed into [VerifiedParticipant]: an
 * unverifiable member may not push/pull, reattach, or clear LocalOnly/outbox state.
 */
enum class SharedWorkspaceRole {
    VerifiedOwner,
    VerifiedParticipant,
    Unknown,
}

data class SharedWorkspaceOwnership(
    val isOwner: Boolean = false,
    val isSoleOwner: Boolean = false,
    // Fail-safe default: a role is Unknown until the coordinator explicitly proves a server-verified
    // membership. Never infer VerifiedParticipant/VerifiedOwner from isOwner — an unverified member
    // constructed without an explicit role must not slip past the Unknown-blocks-reattach guard.
    val role: SharedWorkspaceRole = SharedWorkspaceRole.Unknown,
) {
    val isRoleVerified: Boolean
        get() = role != SharedWorkspaceRole.Unknown
}

enum class SharedWorkspaceBillingState {
    Active,
    Grace,
    Expired,
}

data class SharedWorkspaceAccess(
    val billingState: SharedWorkspaceBillingState = SharedWorkspaceBillingState.Active,
    val billingStateUntil: Instant? = null,
    val localOnly: LocalOnlyState? = null,
) {
    val isReadOnly: Boolean
        get() = localOnly != null || billingState != SharedWorkspaceBillingState.Active
}

enum class LocalOnlyReason {
    EntitlementExpired,
    RemoteKillswitch,
    AdRewardWindowEnded,
}

data class LocalOnlyState(
    val reason: LocalOnlyReason,
    val since: Instant,
    val workspaceBillingState: SharedWorkspaceBillingState? = null,
    val isWorkspaceOwner: Boolean? = null,
)

/**
 * Single orchestration entry point for Shared sync mode. Unlike the file-exchange
 * [com.kshavrin.mymoney.core.sync.JournalSync], Shared mode is a server-side operation log:
 * local Transaction/Account/Category rows are published as operations and remote operations
 * are applied back into Room. Mutual exclusivity with Dropbox/GoogleDrive is enforced through
 * the single active [com.kshavrin.mymoney.core.datastore.CloudBinding].
 */
interface SharedSyncCoordinator {
    val foregroundRealtimeStatus: StateFlow<SharedRealtimeStatus>
        get() = defaultSharedRealtimeStatus

    fun isSignedIn(): Boolean

    fun accountEmail(): String?

    suspend fun signIn(
        googleIdToken: String,
        nonce: String,
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>

    suspend fun activeWorkspace(): SharedWorkspaceSummary?

    suspend fun discoverRemoteWorkspace(): Result<SharedWorkspaceSummary?> = Result.success(null)

    suspend fun recoverRemoteWorkspace(importLocalData: Boolean): Result<SharedWorkspaceSummary> =
        Result.failure(UnsupportedOperationException("Remote workspace recovery is not supported"))

    suspend fun activeWorkspaceOwnership(): Result<SharedWorkspaceOwnership> =
        Result.success(SharedWorkspaceOwnership(role = SharedWorkspaceRole.Unknown))

    suspend fun currentLocalOnlyState(): LocalOnlyState? = null

    suspend fun activeWorkspaceAccess(): Result<SharedWorkspaceAccess>

    fun consumeRestartRequiredAfterAdoptionRecovery(): Boolean = false

    suspend fun createWorkspace(
        name: String,
        importLocalData: Boolean,
    ): Result<SharedWorkspaceSummary>

    suspend fun joinWorkspace(
        inviteToken: String,
        importLocalData: Boolean,
    ): Result<SharedWorkspaceSummary>

    suspend fun createInvite(): Result<SharedWorkspaceInvite>

    suspend fun syncNow(): Result<Unit>

    suspend fun disconnectFromDevice(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Device disconnect is not supported"))

    suspend fun detachToLocalOnly(reason: LocalOnlyReason): Result<Unit> = Result.success(Unit)

    suspend fun reattachAfterEntitlementRestored(): Result<Unit> = Result.success(Unit)

    suspend fun startForegroundRealtime(): Result<Unit> = Result.success(Unit)

    fun stopForegroundRealtime() = Unit

    suspend fun listConflicts(): Result<List<SharedConflict>>

    suspend fun resolveConflict(
        conflictId: String,
        winnerOperationId: String,
    ): Result<Unit>

    suspend fun restoreInternalBackup(backupPath: String): Result<Unit> = Result.success(Unit)

    suspend fun leaveWorkspace(): Result<Unit>

    suspend fun deleteWorkspace(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Workspace deletion is not supported"))

    suspend fun deleteAccount(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Account deletion is not supported"))
}

private val defaultSharedRealtimeStatus = MutableStateFlow<SharedRealtimeStatus>(SharedRealtimeStatus.Inactive)
