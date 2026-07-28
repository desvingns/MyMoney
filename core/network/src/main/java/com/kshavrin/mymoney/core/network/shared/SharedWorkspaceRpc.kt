package com.kshavrin.mymoney.core.network.shared

// Transport seam over the Supabase PostgREST `/rest/v1/rpc/<fn>` endpoints. Each
// method maps 1:1 onto a SECURITY DEFINER function in
// supabase/migrations/0001_shared_workspaces.sql; parameter names mirror the SQL
// `p_*` arguments. The concrete HTTP implementation (URL + anon key + auth bearer
// injection) lands in a later Shared-mode SPEC — this foundation only fixes the
// contract so callers cannot bypass the hashed-invite guarantee.
interface SharedWorkspaceRpc {
    suspend fun createWorkspace(name: String): Result<SharedWorkspace>

    suspend fun currentWorkspace(): Result<SharedWorkspace?>

    suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>>

    // Maps to create_invite(p_workspace_id, p_token_hash): only the SHA-256 hash
    // crosses the wire; the plaintext token never leaves the device.
    suspend fun createInvite(workspaceId: String, tokenHash: String): Result<WorkspaceInvite>

    suspend fun joinWorkspace(token: String): Result<SharedWorkspace>

    suspend fun revokeInvite(inviteId: String): Result<Unit>

    suspend fun leaveWorkspace(workspaceId: String): Result<Unit>

    suspend fun deleteWorkspace(workspaceId: String): Result<Unit>
}
