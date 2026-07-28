package com.kshavrin.mymoney.core.network.shared

interface SharedWorkspaceApi {
    suspend fun createWorkspace(name: String): Result<SharedWorkspace>

    suspend fun currentWorkspace(): Result<SharedWorkspace?>

    suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>>

    suspend fun createInvite(workspaceId: String): Result<CreatedInvite>

    suspend fun joinWorkspace(token: String): Result<SharedWorkspace>

    suspend fun revokeInvite(inviteId: String): Result<Unit>

    suspend fun leaveWorkspace(workspaceId: String): Result<Unit>

    suspend fun deleteWorkspace(workspaceId: String): Result<Unit>
}
