package com.kshavrin.mymoney.core.datastore

data class SharedLocalOnlyState(
    val reason: String,
    val sinceEpochMs: Long,
)

interface SharedSyncStore {
    suspend fun cursor(): Long

    suspend fun setCursor(sequence: Long)

    suspend fun isMembershipActive(): Boolean

    suspend fun setMembershipActive(active: Boolean)

    suspend fun localOnlyState(): SharedLocalOnlyState? = null

    suspend fun setLocalOnly(
        reason: String,
        sinceEpochMs: Long,
    ) = Unit

    suspend fun clearLocalOnly() = Unit

    suspend fun clear()
}
