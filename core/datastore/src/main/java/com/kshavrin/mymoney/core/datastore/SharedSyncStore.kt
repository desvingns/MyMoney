package com.kshavrin.mymoney.core.datastore

data class SharedLocalOnlyState(
    val reason: String,
    val sinceEpochMs: Long,
    val workspaceBillingState: String? = null,
    val isWorkspaceOwner: Boolean? = null,
)

data class SharedWorkspaceAccessContext(
    val billingState: String?,
    val isWorkspaceOwner: Boolean?,
)

interface SharedSyncStore {
    suspend fun cursor(): Long

    suspend fun setCursor(sequence: Long)

    suspend fun isMembershipActive(): Boolean

    suspend fun setMembershipActive(active: Boolean)

    suspend fun workspaceAccessContext(): SharedWorkspaceAccessContext? = null

    suspend fun setWorkspaceAccessContext(
        billingState: String?,
        isWorkspaceOwner: Boolean?,
    ) = Unit

    suspend fun localOnlyState(): SharedLocalOnlyState? = null

    suspend fun setLocalOnly(
        reason: String,
        sinceEpochMs: Long,
        workspaceBillingState: String? = null,
        isWorkspaceOwner: Boolean? = null,
    ) = Unit

    suspend fun clearLocalOnly() = Unit

    suspend fun clear()
}
