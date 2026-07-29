package com.kshavrin.mymoney.core.network.shared

import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder transports for Shared mode. The concrete Supabase HTTP implementation
 * (URL + anon key + auth-bearer injection over PostgREST) is intentionally deferred to a
 * dedicated networking SPEC — see the contract comments on [SharedWorkspaceRpc] /
 * [SharedJournalRpc]. These stubs keep the Hilt graph complete so the full Shared-mode
 * orchestration, UI, and lifecycle can be built and tested against fakes; every call fails
 * with a clear signal until the real transport lands.
 */
private fun notWired(): IllegalStateException = IllegalStateException("Shared transport is not wired yet")

@Singleton
class StubSharedAuth
    @Inject
    constructor() : SharedAuth {
        override fun currentSession(): SharedSession? = null

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> = Result.failure(notWired())

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }

@Singleton
class StubSharedWorkspaceRpc
    @Inject
    constructor() : SharedWorkspaceRpc {
        override suspend fun createWorkspace(name: String): Result<SharedWorkspace> = Result.failure(notWired())

        override suspend fun currentWorkspace(): Result<SharedWorkspace?> = Result.failure(notWired())

        override suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>> = Result.failure(notWired())

        override suspend fun createInvite(
            workspaceId: String,
            tokenHash: String,
        ): Result<WorkspaceInvite> = Result.failure(notWired())

        override suspend fun joinWorkspace(token: String): Result<SharedWorkspace> = Result.failure(notWired())

        override suspend fun revokeInvite(inviteId: String): Result<Unit> = Result.failure(notWired())

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> = Result.failure(notWired())

        override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> = Result.failure(notWired())
    }

@Singleton
class StubSharedJournalRpc
    @Inject
    constructor() : SharedJournalRpc {
        override suspend fun pushOperation(
            workspaceId: String,
            idempotencyKey: String,
            baseSequence: Long,
            deviceId: String,
            entityKind: String,
            entityId: String,
            payload: JsonElement?,
            tombstone: Boolean,
        ): Result<SharedOperationDto> = Result.failure(notWired())

        override suspend fun pullOperations(
            workspaceId: String,
            afterSequence: Long,
            limit: Int,
        ): Result<List<SharedOperationDto>> = Result.failure(notWired())

        override suspend fun listPendingConflicts(workspaceId: String): Result<List<SharedConflictDto>> =
            Result.failure(notWired())

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<SharedOperationDto> = Result.failure(notWired())
    }
