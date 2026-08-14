package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

        override suspend fun currentWorkspace(): Result<SharedWorkspace?> =
            withAccessToken { accessToken ->
                http
                    .get(
                        path =
                            "rest/v1/workspace_members?select=workspace:workspaces!inner(id,name,owner_id,created_at,billing_state,billing_state_until)&active=eq.true&limit=1",
                        accessToken = accessToken,
                    ).mapCatching { response ->
                        response.jsonArray
                            .firstOrNull()
                            ?.jsonObject
                            ?.requiredObject("workspace")
                            ?.let(::workspaceFrom)
                    }
            }

        override suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>> =
            withAccessToken { accessToken ->
                http
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
            rpcUnit("revoke_invite", buildJsonObject { put("p_invite_id", inviteId) })

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> =
            rpcUnit("leave_workspace", buildJsonObject { put("p_workspace_id", workspaceId) })

        override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> =
            rpcUnit("delete_workspace", buildJsonObject { put("p_workspace_id", workspaceId) })

        override suspend fun deleteAccount(): Result<Unit> =
            rpcUnit(
                name = "delete_my_account",
                payload = buildJsonObject { },
                mapAccountDeletionWorkspaceConflict = true,
            )

        private suspend fun <T> rpc(
            name: String,
            payload: kotlinx.serialization.json.JsonObject,
            decode: (kotlinx.serialization.json.JsonObject) -> T,
        ): Result<T> =
            withAccessToken { accessToken ->
                http
                    .post(
                        path = "rest/v1/rpc/$name",
                        payload = payload,
                        accessToken = accessToken,
                        mapMembershipDeniedToAuth = true,
                    ).mapCatching { response -> decode(response.jsonObject) }
            }

        private suspend fun rpcUnit(
            name: String,
            payload: kotlinx.serialization.json.JsonObject,
            mapAccountDeletionWorkspaceConflict: Boolean = false,
        ): Result<Unit> =
            withAccessToken { accessToken ->
                http
                    .post(
                        path = "rest/v1/rpc/$name",
                        payload = payload,
                        accessToken = accessToken,
                        mapMembershipDeniedToAuth = true,
                        mapAccountDeletionWorkspaceConflict = mapAccountDeletionWorkspaceConflict,
                    ).map { Unit }
            }

        private suspend fun <T> withAccessToken(
            request: suspend (String) -> Result<T>,
        ): Result<T> {
            val accessToken = auth.accessToken().getOrElse { return Result.failure(it) }
            return request(accessToken)
        }

        private fun workspaceFrom(value: kotlinx.serialization.json.JsonObject): SharedWorkspace =
            SharedWorkspace(
                id = value.requiredString("id"),
                name = value.requiredString("name"),
                ownerId = value.requiredString("owner_id"),
                createdAt = Instant.parse(value.requiredString("created_at")),
                billingState =
                    value["billing_state"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.toWorkspaceBillingState()
                        ?: WorkspaceBillingState.Active,
                billingStateUntil = value["billing_state_until"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse),
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

private fun String.toWorkspaceBillingState(): WorkspaceBillingState =
    when (this) {
        "active" -> WorkspaceBillingState.Active
        "grace" -> WorkspaceBillingState.Grace
        "expired" -> WorkspaceBillingState.Expired
        else -> throw SyncException(SyncError.Server)
    }
