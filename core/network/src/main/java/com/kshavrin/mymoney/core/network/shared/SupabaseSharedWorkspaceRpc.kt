package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedWorkspaceRpc
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val http: SupabaseHttpTransport,
    ) : SharedWorkspaceRpc {
        override suspend fun createWorkspace(name: String): Result<SharedWorkspace> =
            rpc("create_workspace", buildJsonObject { put("p_name", name) }, ::workspaceFrom)

        override suspend fun currentWorkspace(): Result<SharedWorkspace?> {
            val accessToken = accessTokenOrFailure() ?: return Result.failure(SyncException(SyncError.Auth))
            return http
                .get(
                    path =
                        "rest/v1/workspace_members?select=workspace:workspaces!inner(id,name,owner_id,created_at)&active=eq.true&limit=1",
                    accessToken = accessToken,
                ).mapCatching { response ->
                    response.jsonArray.firstOrNull()?.jsonObject?.requiredObject("workspace")?.let(::workspaceFrom)
                }
        }

        override suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>> {
            val accessToken = accessTokenOrFailure() ?: return Result.failure(SyncException(SyncError.Auth))
            return http
                .get(
                    path =
                        "rest/v1/workspace_members?select=user_id,role,joined_at,active&workspace_id=eq.$workspaceId",
                    accessToken = accessToken,
                ).mapCatching { response ->
                    response.jsonArray.map { member ->
                        memberFrom(member.jsonObject, auth.currentSession()?.user)
                    }
                }
        }

        override suspend fun createInvite(
            workspaceId: String,
            tokenHash: String,
        ): Result<WorkspaceInvite> =
            rpc(
                "create_invite",
                buildJsonObject {
                    put("p_workspace_id", workspaceId)
                    put("p_token_hash", tokenHash)
                },
                ::inviteFrom,
            )

        override suspend fun joinWorkspace(token: String): Result<SharedWorkspace> =
            rpc("join_workspace", buildJsonObject { put("p_token", token) }, ::workspaceFrom)

        override suspend fun revokeInvite(inviteId: String): Result<Unit> =
            rpc("revoke_invite", buildJsonObject { put("p_invite_id", inviteId) }) { Unit }

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> =
            rpc("leave_workspace", buildJsonObject { put("p_workspace_id", workspaceId) }) { Unit }

        override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> =
            rpc("delete_workspace", buildJsonObject { put("p_workspace_id", workspaceId) }) { Unit }

        private suspend fun <T> rpc(
            name: String,
            payload: kotlinx.serialization.json.JsonObject,
            decode: (kotlinx.serialization.json.JsonObject) -> T,
        ): Result<T> {
            val accessToken = accessTokenOrFailure() ?: return Result.failure(SyncException(SyncError.Auth))
            return http
                .post("rest/v1/rpc/$name", payload, accessToken)
                .mapCatching { response -> decode(response.jsonObject) }
        }

        private fun accessTokenOrFailure(): String? = runCatching(auth::requireAccessToken).getOrNull()

        private fun workspaceFrom(value: kotlinx.serialization.json.JsonObject): SharedWorkspace =
            SharedWorkspace(
                id = value.requiredString("id"),
                name = value.requiredString("name"),
                ownerId = value.requiredString("owner_id"),
                createdAt = Instant.parse(value.requiredString("created_at")),
            )

        private fun inviteFrom(value: kotlinx.serialization.json.JsonObject): WorkspaceInvite =
            WorkspaceInvite(
                id = value.requiredString("id"),
                workspaceId = value.requiredString("workspace_id"),
                role = value.requiredString("role").toWorkspaceRole(),
                expiresAt = Instant.parse(value.requiredString("expires_at")),
            )

        private fun memberFrom(
            value: kotlinx.serialization.json.JsonObject,
            currentUser: SharedUser?,
        ): WorkspaceMember {
            val userId = value.requiredString("user_id")
            return WorkspaceMember(
                userId = userId,
                email = currentUser?.takeIf { it.id == userId }?.email.orEmpty(),
                role = value.requiredString("role").toWorkspaceRole(),
                joinedAt = Instant.parse(value.requiredString("joined_at")),
                active = value.requiredString("active").toBooleanStrict(),
            )
        }
    }

private fun String.toWorkspaceRole(): WorkspaceRole =
    when (this) {
        "owner" -> WorkspaceRole.Owner
        "editor" -> WorkspaceRole.Editor
        else -> throw SyncException(SyncError.Server)
    }
