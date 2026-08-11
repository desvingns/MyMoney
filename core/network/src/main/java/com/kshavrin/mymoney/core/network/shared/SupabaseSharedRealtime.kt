package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSharedRealtime
    @Inject
    constructor(
        private val config: SupabaseConfig,
        @SharedSupabaseClient private val client: OkHttpClient,
        private val json: Json,
    ) : SharedRealtime {
        override fun events(
            workspaceId: String,
            accessToken: String,
        ): Flow<SharedRealtimeEvent> =
            callbackFlow {
                if (!config.isConfigured) {
                    close(SyncException(SyncError.Server))
                    return@callbackFlow
                }
                var heartbeatJob: Job? = null
                val socket =
                    client.newWebSocket(
                        Request
                            .Builder()
                            .url(config.realtimeUrl())
                            .header("Authorization", "Bearer $accessToken")
                            .header("apikey", config.anonKey)
                            .build(),
                        object : WebSocketListener() {
                            override fun onOpen(
                                webSocket: WebSocket,
                                response: Response,
                            ) {
                                if (!webSocket.send(json.encodeToString(joinMessage(workspaceId, accessToken)))) {
                                    close(SyncException(SyncError.Server))
                                    return
                                }
                                heartbeatJob =
                                    launch {
                                        var heartbeatRef = INITIAL_HEARTBEAT_REF
                                        while (isActive) {
                                            delay(HEARTBEAT_INTERVAL_MILLIS)
                                            if (!webSocket.send(json.encodeToString(heartbeatMessage(heartbeatRef++)))) {
                                                close(SyncException(SyncError.Server))
                                                return@launch
                                            }
                                        }
                                    }
                            }

                            override fun onMessage(
                                webSocket: WebSocket,
                                text: String,
                            ) {
                                val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                                when (message["event"]?.jsonPrimitive?.content) {
                                    "phx_reply" -> {
                                        if (message["ref"]?.jsonPrimitive?.content != CHANNEL_JOIN_REF) return
                                        val joined =
                                            message["payload"]
                                                ?.jsonObject
                                                ?.get("status")
                                                ?.jsonPrimitive
                                                ?.content == "ok"
                                        if (joined) {
                                            trySend(SharedRealtimeEvent.Connected)
                                        } else {
                                            close(SyncException(SyncError.Server))
                                        }
                                    }

                                    "broadcast" -> {
                                        if (message.isOperationAvailableBroadcast()) {
                                            trySend(SharedRealtimeEvent.OperationAvailable)
                                        }
                                    }

                                    "phx_close", "phx_error" -> close(SyncException(SyncError.Server))
                                }
                            }

                            override fun onClosing(
                                webSocket: WebSocket,
                                code: Int,
                                reason: String,
                            ) {
                                close(SharedRealtimeDisconnectedException())
                            }

                            override fun onFailure(
                                webSocket: WebSocket,
                                throwable: Throwable,
                                response: Response?,
                            ) {
                                close(throwable)
                            }
                        },
                    )
                awaitClose {
                    heartbeatJob?.cancel()
                    socket.cancel()
                }
            }

        private fun joinMessage(
            workspaceId: String,
            accessToken: String,
        ) =
            buildJsonObject {
                put("topic", "realtime:${workspaceTopic(workspaceId)}")
                put("event", "phx_join")
                put("ref", CHANNEL_JOIN_REF)
                put("join_ref", CHANNEL_JOIN_REF)
                put(
                    "payload",
                    buildJsonObject {
                        put("access_token", accessToken)
                        put(
                            "config",
                            buildJsonObject {
                                put("private", true)
                                put(
                                    "broadcast",
                                    buildJsonObject {
                                        put("ack", false)
                                        put("self", false)
                                    },
                                )
                                put("presence", buildJsonObject { put("enabled", false) })
                                put("postgres_changes", buildJsonArray {})
                            },
                        )
                    },
                )
            }

        private fun heartbeatMessage(reference: Long) =
            buildJsonObject {
                put("topic", "phoenix")
                put("event", "heartbeat")
                put("ref", reference.toString())
                put("payload", buildJsonObject {})
            }

        private fun workspaceTopic(workspaceId: String) =
            "workspace:$workspaceId:operations"

        private fun kotlinx.serialization.json.JsonObject.isOperationAvailableBroadcast(): Boolean =
            runCatching {
                get("payload")
                    ?.jsonObject
                    ?.get("event")
                    ?.jsonPrimitive
                    ?.content == OPERATION_AVAILABLE_EVENT
            }.getOrDefault(false)

        private fun SupabaseConfig.realtimeUrl() =
            url
                .toHttpUrl()
                .newBuilder()
                .addPathSegments("realtime/v1/websocket")
                .addQueryParameter("apikey", anonKey)
                .addQueryParameter("vsn", "1.0.0")
                .build()

        private companion object {
            const val CHANNEL_JOIN_REF = "1"
            const val INITIAL_HEARTBEAT_REF = 2L
            const val HEARTBEAT_INTERVAL_MILLIS = 25_000L
            const val OPERATION_AVAILABLE_EVENT = "operation_available"
        }
    }

private class SharedRealtimeDisconnectedException : IllegalStateException()
