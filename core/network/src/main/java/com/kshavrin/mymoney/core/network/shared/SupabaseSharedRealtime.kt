package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
                                }
                            }

                            override fun onMessage(
                                webSocket: WebSocket,
                                text: String,
                            ) {
                                val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                                when (message["event"]?.jsonPrimitive?.content) {
                                    "phx_reply" -> {
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

                                    "postgres_changes" -> trySend(SharedRealtimeEvent.OperationAvailable)
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
                awaitClose { socket.cancel() }
            }

        private fun joinMessage(
            workspaceId: String,
            accessToken: String,
        ) =
            buildJsonObject {
                put("topic", "realtime:public:operations")
                put("event", "phx_join")
                put("ref", "1")
                put("payload", buildJsonObject {
                    put("access_token", accessToken)
                    put("config", buildJsonObject {
                        put("broadcast", buildJsonObject { put("self", false) })
                        put("presence", buildJsonObject { put("key", "") })
                        put("postgres_changes", buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("event", "INSERT")
                                    put("schema", "public")
                                    put("table", "operations")
                                    put("filter", "workspace_id=eq.$workspaceId")
                                },
                            )
                        })
                    })
                })
            }

        private fun SupabaseConfig.realtimeUrl() =
            url
                .toHttpUrl()
                .newBuilder()
                .scheme(if (url.startsWith("https://")) "wss" else "ws")
                .addPathSegments("realtime/v1/websocket")
                .addQueryParameter("apikey", anonKey)
                .addQueryParameter("vsn", "1.0.0")
                .build()
    }

private class SharedRealtimeDisconnectedException : IllegalStateException()
