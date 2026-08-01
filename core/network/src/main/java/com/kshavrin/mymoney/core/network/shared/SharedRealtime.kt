package com.kshavrin.mymoney.core.network.shared

import kotlinx.coroutines.flow.Flow

interface SharedRealtime {
    fun events(
        workspaceId: String,
        accessToken: String,
    ): Flow<SharedRealtimeEvent>
}

sealed interface SharedRealtimeEvent {
    data object Connected : SharedRealtimeEvent

    data object OperationAvailable : SharedRealtimeEvent

    data class Disconnected(
        val cause: Throwable,
    ) : SharedRealtimeEvent
}
