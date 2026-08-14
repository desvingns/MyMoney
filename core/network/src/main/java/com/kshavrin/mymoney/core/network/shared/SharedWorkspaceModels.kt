package com.kshavrin.mymoney.core.network.shared

import java.time.Instant

enum class WorkspaceRole {
    Owner,
    Editor,
}

enum class WorkspaceBillingState {
    Active,
    Grace,
    Expired,
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
    val billingState: WorkspaceBillingState = WorkspaceBillingState.Active,
    val billingStateUntil: Instant? = null,
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
