package com.kshavrin.mymoney.core.datastore

interface SharedSyncStore {
    suspend fun cursor(): Long

    suspend fun setCursor(sequence: Long)

    suspend fun isMembershipActive(): Boolean

    suspend fun setMembershipActive(active: Boolean)

    suspend fun clear()
}
