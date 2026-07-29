package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.domain.sync.SharedConflict

data class SharedWorkspaceSummary(
    val id: String,
    val name: String,
)

data class SharedWorkspaceInvite(
    val token: String,
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

    suspend fun leaveWorkspace(): Result<Unit>
}
