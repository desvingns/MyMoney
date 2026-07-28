package com.kshavrin.mymoney.core.network.shared

import java.time.Instant

enum class WorkspaceRole {
    Owner,
    Editor,
}

data class SharedUser(
    val id: String,
    val email: String,
)

data class SharedWorkspace(
    val id: String,
    val name: String,
    val ownerId: String,
    val createdAt: Instant,
)

data class WorkspaceMember(
    val userId: String,
    val email: String,
    val role: WorkspaceRole,
    val joinedAt: Instant,
    val active: Boolean,
)

data class WorkspaceInvite(
    val id: String,
    val workspaceId: String,
    val role: WorkspaceRole,
    val expiresAt: Instant,
)

data class CreatedInvite(
    val invite: WorkspaceInvite,
    val token: String,
)
