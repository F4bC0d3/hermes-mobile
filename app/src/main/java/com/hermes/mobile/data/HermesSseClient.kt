package com.hermes.mobile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * SSE client for /api/chat/stream.
 *
 * hermes-webui emits SSE events typed by their `event:` field. The relevant
 * ones we surface to the UI are:
 *
 *   token            — a chunk of assistant text. data = {"text": "..."}
 *   reasoning        — Claude/o3 thinking. data = {"text": "..."}
 *   tool_call        — start of a tool invocation
 *   tool_call_result — end of a tool invocation, with output
 *   approval_request — a shell command requires approval
 *   error            — a fatal error in the stream
 *   done             — end-of-turn marker; SSE connection closes
 *   heartbeat        — kernel-keepalive byte every 5s, ignored
 *
 * Anything we don't recognize is exposed as a generic [HermesEvent.Other] so
 * the caller can decide how to treat it.
 */
class HermesSseClient(private val store: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Used by the chat screen to subscribe to a running stream. The flow
     * completes naturally when the server sends `done` or closes the
     * connection. Cancelling the surrounding coroutine cancels the SSE.
     */
    fun subscribe(streamUrl: String): Flow<HermesEvent> = callbackFlow {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // No read timeout — server idle-keeps the SSE alive with heartbeats.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val cookie = store.sessionCookie
        val req = Request.Builder()
            .url(streamUrl)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .apply { if (cookie != null) header("Cookie", "hermes_session=$cookie") }
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(HermesEvent.Open)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data.isBlank()) return
                val event = parseEvent(type, data)
                if (event != null) trySend(event)
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(HermesEvent.Closed)
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(HermesEvent.Error(t?.message ?: "stream error", recoverable = true))
                close(t)
            }
        }

        val source = EventSources.createFactory(client).newEventSource(req, listener)
        awaitClose {
            source.cancel()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun parseEvent(type: String?, data: String): HermesEvent? {
        return when (type ?: "message") {
            "heartbeat", "ping" -> null
            "token" -> {
                val obj = parseJson(data)
                val text = obj?.get("text")?.toString()?.unquote().orEmpty()
                if (text.isEmpty()) null else HermesEvent.Token(text)
            }
            "reasoning" -> {
                val obj = parseJson(data)
                val text = obj?.get("text")?.toString()?.unquote().orEmpty()
                if (text.isEmpty()) null else HermesEvent.Reasoning(text)
            }
            "tool_call" -> {
                val obj = parseJson(data) ?: return null
                HermesEvent.ToolCall(
                    name = obj["name"]?.toString()?.unquote().orEmpty(),
                    args = obj["args"]?.toString().orEmpty(),
                    id = obj["id"]?.toString()?.unquote(),
                )
            }
            "tool_call_result" -> {
                val obj = parseJson(data) ?: return null
                HermesEvent.ToolResult(
                    name = obj["name"]?.toString()?.unquote().orEmpty(),
                    result = obj["result"]?.toString().orEmpty(),
                    id = obj["id"]?.toString()?.unquote(),
                )
            }
            "approval_request" -> {
                val obj = parseJson(data) ?: return null
                HermesEvent.ApprovalRequest(
                    command = obj["command"]?.toString()?.unquote().orEmpty(),
                    explanation = obj["explanation"]?.toString()?.unquote(),
                    id = obj["id"]?.toString()?.unquote().orEmpty(),
                )
            }
            "error" -> {
                val obj = parseJson(data)
                HermesEvent.Error(
                    message = obj?.get("message")?.toString()?.unquote() ?: data,
                    recoverable = false,
                )
            }
            "done", "complete", "end" -> HermesEvent.Done
            "title" -> {
                val obj = parseJson(data)
                val title = obj?.get("title")?.toString()?.unquote()
                if (title.isNullOrBlank()) null else HermesEvent.TitleChanged(title)
            }
            else -> HermesEvent.Other(type ?: "message", data)
        }
    }

    private fun parseJson(data: String): JsonObject? = runCatching {
        when (val el: JsonElement = json.parseToJsonElement(data)) {
            is JsonObject -> el
            else -> null
        }
    }.getOrNull()

    private fun String.unquote(): String =
        if (length >= 2 && startsWith('"') && endsWith('"')) substring(1, length - 1)
            .replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
        else this
}

sealed interface HermesEvent {
    data object Open : HermesEvent
    data object Closed : HermesEvent
    data object Done : HermesEvent
    data class Token(val text: String) : HermesEvent
    data class Reasoning(val text: String) : HermesEvent
    data class ToolCall(val name: String, val args: String, val id: String?) : HermesEvent
    data class ToolResult(val name: String, val result: String, val id: String?) : HermesEvent
    data class ApprovalRequest(val command: String, val explanation: String?, val id: String) : HermesEvent
    data class TitleChanged(val title: String) : HermesEvent
    data class Error(val message: String, val recoverable: Boolean) : HermesEvent
    data class Other(val type: String, val data: String) : HermesEvent
}
