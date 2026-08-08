package com.kshavrin.mymoney.core.network.shared

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedWorkspaceApi
    @Inject
    constructor(
        private val rpc: SharedWorkspaceRpc,
        private val tokenFactory: InviteTokenFactory,
    ) : SharedWorkspaceApi {
        override suspend fun createWorkspace(name: String): Result<SharedWorkspace> = rpc.createWorkspace(name)

        override suspend fun currentWorkspace(): Result<SharedWorkspace?> = rpc.currentWorkspace()

        override suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>> = rpc.listMembers(workspaceId)

        // Generate the invite locally, send only its hash to create_invite's
        // p_token_hash, and hand the plaintext back to the caller to share once.
        override suspend fun createInvite(workspaceId: String): Result<CreatedInvite> {
            val token = tokenFactory.newToken()
            return rpc
                .createInvite(workspaceId = workspaceId, tokenHash = token.hash)
                .map { invite -> CreatedInvite(invite = invite, token = token.plaintext) }
        }

        override suspend fun joinWorkspace(token: String): Result<SharedWorkspace> = rpc.joinWorkspace(token)

        override suspend fun revokeInvite(inviteId: String): Result<Unit> = rpc.revokeInvite(inviteId)

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> = rpc.leaveWorkspace(workspaceId)

    override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> = rpc.deleteWorkspace(workspaceId)

    override suspend fun deleteAccount(): Result<Unit> = rpc.deleteAccount()
}
