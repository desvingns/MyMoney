package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.domain.sync.SharedConflict

data class SharedWorkspaceSummary(
    val id: String,
    val name: String,
)

data class SharedWorkspaceInvite(
    val token: String,
)

data class SharedWorkspaceOwnership(
    val isOwner: Boolean = false,
    val isSoleOwner: Boolean = false,
)

/**
 * Single orchestration entry point for Shared sync mode. Unlike the file-exchange
 * [com.kshavrin.mymoney.core.sync.JournalSync], Shared mode is a server-side operation log:
 * local Transaction/Account/Category rows are published as operations and remote operations
 * are applied back into Room. Mutual exclusivity with Dropbox/GoogleDrive is enforced through
 * the single active [com.kshavrin.mymoney.core.datastore.CloudBinding].
 */
interface SharedSyncCoordinator {
    fun isSignedIn(): Boolean

    fun accountEmail(): String?

    suspend fun signIn(
        googleIdToken: String,
        nonce: String,
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>

    suspend fun activeWorkspace(): SharedWorkspaceSummary?

    /**
     * Reads an already-active server membership without changing local data or the local binding.
     * The caller must ask the person using the device before calling [recoverRemoteWorkspace].
     */
    suspend fun discoverRemoteWorkspace(): Result<SharedWorkspaceSummary?> = Result.success(null)

    suspend fun recoverRemoteWorkspace(importLocalData: Boolean): Result<SharedWorkspaceSummary> =
        Result.failure(UnsupportedOperationException("Remote workspace recovery is not supported"))

    suspend fun activeWorkspaceOwnership(): Result<SharedWorkspaceOwnership> =
        Result.success(SharedWorkspaceOwnership())

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

    suspend fun listConflicts(): Result<List<SharedConflict>>

    suspend fun resolveConflict(
        conflictId: String,
        winnerOperationId: String,
    ): Result<Unit>

    suspend fun restoreInternalBackup(backupPath: String): Result<Unit> = Result.success(Unit)

    suspend fun leaveWorkspace(): Result<Unit>

    suspend fun deleteWorkspace(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Workspace deletion is not supported"))
}
