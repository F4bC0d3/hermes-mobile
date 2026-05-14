package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Native client for hermes-webui's REST API.
 *
 * Hits the same endpoints the web frontend hits, so the Android app gets full
 * feature parity (sessions, profiles, models, memory, workspace files,
 * attachments) without an embedded WebView.
 *
 * Endpoints surfaced here:
 *   POST /api/auth/login            JSON {"password":...}        -> hermes_session cookie
 *   POST /api/auth/logout
 *   GET  /api/sessions              list sessions
 *   GET  /api/session               read a single session (+ messages)
 *   POST /api/chat/start            start a streaming turn       -> {stream_id}
 *   GET  /api/chat/stream?stream_id SSE token+event stream       (see HermesSseClient)
 *   POST /api/chat/cancel?stream_id cancel an active stream
 *   GET  /api/profiles              list profiles
 *   POST /api/profile/activate      switch active profile
 *   GET  /api/models                static + live model catalog
 *   GET  /api/list                  list workspace files
 *   GET  /api/file                  read a workspace file
 *   POST /api/file/save             write a workspace file
 *   GET  /api/memory                read MEMORY.md / USER.md
 *   POST /api/memory/save           write either of those
 *   POST /api/upload                multipart upload (image / file attachment)
 *   GET  /health                    Space-level health probe
 */
class HermesApi(
    private val store: SessionStore,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun base(): String =
        store.spaceUrl ?: throw IllegalStateException("Space URL not configured")

    private fun authCookie(): String {
        val c = store.sessionCookie ?: error("Not logged in")
        return "hermes_session=$c"
    }

    private fun authedRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", "HermesMobile/0.2 (Android)")
            .header("Accept", "application/json")
            .header("Cookie", authCookie())

    /* ── Auth ────────────────────────────────────────────────────── */

    suspend fun login(spaceUrl: String, token: String): LoginResult = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(spaceUrl)
        val body = buildJsonObject { put("password", token) }.toString()
        val req = Request.Builder()
            .url("$cleanUrl/api/auth/login")
            .post(body.toRequestBody(jsonMedia))
            .header("User-Agent", "HermesMobile/0.2 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                val cookie = resp.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("hermes_session=") }
                    ?.substringBefore(';')
                    ?.removePrefix("hermes_session=")

                when {
                    resp.code == 401 ->
                        LoginResult.Failure("Wrong password. Check your GATEWAY_TOKEN.")
                    resp.code == 429 ->
                        LoginResult.Failure("Too many attempts. Wait a minute and try again.")
                    resp.code == 404 ->
                        LoginResult.Failure(
                            "Login endpoint not found at $cleanUrl. Make sure this URL " +
                                "points to a running Hermes WebUI / HuggingMes Space."
                        )
                    !resp.isSuccessful ->
                        LoginResult.Failure("Login failed (HTTP ${resp.code}).")
                    cookie.isNullOrBlank() ->
                        LoginResult.Failure(
                            "Login succeeded but no session cookie was returned. Check " +
                                "that HERMES_WEBUI_PASSWORD or GATEWAY_TOKEN is set on the Space."
                        )
                    else -> {
                        store.spaceUrl = cleanUrl
                        store.gatewayToken = token
                        store.sessionCookie = cookie
                        LoginResult.Success(cleanUrl, cookie)
                    }
                }
            }
        } catch (e: Exception) {
            LoginResult.Failure("Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${base()}/api/auth/logout")
                .post("".toRequestBody(jsonMedia))
                .header("Cookie", authCookie())
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /* ── Sessions ─────────────────────────────────────────────────── */

    suspend fun listSessions(): List<SessionEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val req = authedRequest("${base()}/api/sessions").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                val element = json.parseToJsonElement(body)
                val arr = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["sessions"] as? JsonArray
                    else -> null
                } ?: return@withContext emptyList()
                arr.mapNotNull { node ->
                    runCatching { json.decodeFromJsonElement(SessionEntry.serializer(), node) }
                        .getOrNull()
                }.sortedByDescending { it.updated_at ?: "" }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getSession(sessionId: String, withMessages: Boolean = true, msgLimit: Int = 200): SessionDetail? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildString {
                    append("${base()}/api/session?session_id=")
                    append(sessionId)
                    append("&messages=")
                    append(if (withMessages) "1" else "0")
                    append("&resolve_model=1")
                    if (withMessages) append("&msg_limit=$msgLimit")
                }
                val req = authedRequest(url).get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    json.decodeFromString(SessionDetail.serializer(), resp.body!!.string())
                }
            }.getOrNull()
        }

    suspend fun createSession(title: String? = null): String? = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("title", title ?: "New Chat")
            }.toString()
            val req = authedRequest("${base()}/api/sessions/create")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = json.parseToJsonElement(resp.body!!.string()).let { it as? JsonObject }
                obj?.get("session_id")?.toString()?.trim('"')
            }
        }.getOrNull()
    }

    suspend fun renameSession(sessionId: String, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("session_id", sessionId)
                put("title", newTitle)
            }.toString()
            val req = authedRequest("${base()}/api/session/rename")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("session_id", sessionId) }.toString()
            val req = authedRequest("${base()}/api/session/delete")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun togglePin(sessionId: String, pinned: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("session_id", sessionId)
                put("pinned", pinned)
            }.toString()
            val req = authedRequest("${base()}/api/session/pin")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun toggleArchive(sessionId: String, archived: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("session_id", sessionId)
                put("archived", archived)
            }.toString()
            val req = authedRequest("${base()}/api/session/archive")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /* ── Chat ────────────────────────────────────────────────────── */

    /** Start a new streaming turn on a session. Returns the stream_id to subscribe to. */
    suspend fun chatStart(
        sessionId: String,
        message: String,
        attachments: List<Attachment> = emptyList(),
        workspace: String? = null,
        model: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("session_id", sessionId)
                put("message", message)
                if (workspace != null) put("workspace", workspace)
                if (model != null) put("model", model)
                if (attachments.isNotEmpty()) {
                    put("attachments", buildJsonArray {
                        attachments.forEach {
                            add(buildJsonObject {
                                put("name", it.name)
                                put("path", it.path)
                                put("mime", it.mime)
                                if (it.size != null) put("size", it.size)
                                if (it.isImage != null) put("is_image", it.isImage)
                            })
                        }
                    })
                }
            }.toString()
            val req = authedRequest("${base()}/api/chat/start")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = json.parseToJsonElement(resp.body!!.string()) as? JsonObject
                obj?.get("stream_id")?.toString()?.trim('"')
            }
        }.getOrNull()
    }

    suspend fun chatCancel(streamId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = authedRequest("${base()}/api/chat/cancel?stream_id=$streamId").post(EMPTY).build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Returns the SSE URL for a running stream. Handed off to HermesSseClient. */
    fun chatStreamUrl(streamId: String): String =
        "${base()}/api/chat/stream?stream_id=$streamId"

    /* ── Profiles + models ───────────────────────────────────────── */

    suspend fun listProfiles(): List<ProfileEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val req = authedRequest("${base()}/api/profiles").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body!!.string()
                val element = json.parseToJsonElement(body)
                val arr = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["profiles"] as? JsonArray
                    else -> null
                } ?: return@withContext emptyList()
                arr.mapNotNull {
                    runCatching { json.decodeFromJsonElement(ProfileEntry.serializer(), it) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun activateProfile(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("name", name) }.toString()
            val req = authedRequest("${base()}/api/profile/activate")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun listModels(): List<ModelEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val req = authedRequest("${base()}/api/models").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val element = json.parseToJsonElement(resp.body!!.string())
                val arr = when (element) {
                    is JsonArray -> element
                    is JsonObject -> element["models"] as? JsonArray
                    else -> null
                } ?: return@withContext emptyList()
                arr.mapNotNull {
                    runCatching { json.decodeFromJsonElement(ModelEntry.serializer(), it) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
    }

    /* ── Workspace files ─────────────────────────────────────────── */

    suspend fun listFiles(sessionId: String, path: String = "."): List<FileEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(path, "UTF-8")
                val url = "${base()}/api/list?session_id=$sessionId&path=$encoded"
                val req = authedRequest(url).get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val element = json.parseToJsonElement(resp.body!!.string())
                    val arr = when (element) {
                        is JsonArray -> element
                        is JsonObject -> (element["files"] ?: element["entries"]) as? JsonArray
                        else -> null
                    } ?: return@withContext emptyList()
                    arr.mapNotNull {
                        runCatching { json.decodeFromJsonElement(FileEntry.serializer(), it) }.getOrNull()
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readFile(sessionId: String, path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(path, "UTF-8")
            val req = authedRequest("${base()}/api/file?session_id=$sessionId&path=$encoded").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        }.getOrNull()
    }

    suspend fun saveFile(sessionId: String, path: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("session_id", sessionId)
                    put("path", path)
                    put("content", content)
                }.toString()
                val req = authedRequest("${base()}/api/file/save")
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                http.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /* ── Memory ──────────────────────────────────────────────────── */

    suspend fun getMemory(kind: MemoryKind): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = authedRequest("${base()}/api/memory?kind=${kind.apiName}").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = json.parseToJsonElement(resp.body!!.string()) as? JsonObject
                obj?.get("content")?.toString()?.trim('"')?.replace("\\n", "\n")
            }
        }.getOrNull()
    }

    suspend fun saveMemory(kind: MemoryKind, content: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("kind", kind.apiName)
                put("content", content)
            }.toString()
            val req = authedRequest("${base()}/api/memory/save")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /* ── Attachments / uploads ───────────────────────────────────── */

    suspend fun uploadAttachment(
        sessionId: String,
        filename: String,
        mime: String,
        bytes: ByteArray,
    ): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart(
                    "file", filename,
                    bytes.toRequestBody(mime.toMediaType()),
                )
                .build()
            val req = authedRequest("${base()}/api/upload").post(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = json.parseToJsonElement(resp.body!!.string()) as? JsonObject
                Attachment(
                    name = obj?.get("name")?.toString()?.trim('"') ?: filename,
                    path = obj?.get("path")?.toString()?.trim('"') ?: "",
                    mime = mime,
                    size = bytes.size.toLong(),
                    isImage = mime.startsWith("image/"),
                )
            }
        }.getOrNull()
    }

    /* ── Health probe ────────────────────────────────────────────── */

    suspend fun health(): HealthSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${base()}/health")
                .header("User-Agent", "HermesMobile/0.2 (Android)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.decodeFromString<HealthSnapshot>(resp.body!!.string())
            }
        }.getOrNull()
    }

    /* ── Helpers ─────────────────────────────────────────────────── */

    private fun normalizeUrl(input: String): String {
        var u = input.trim().trimEnd('/')
        if (u.isEmpty()) throw IOException("URL is empty")
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }

    companion object {
        private val EMPTY: RequestBody = "".toRequestBody(null)
    }
}

/* ── Data classes ─────────────────────────────────────────────── */

@Serializable
data class HealthSnapshot(
    val ok: Boolean = false,
    val gateway: Boolean = false,
    val webui: Boolean = false,
    val uptime: String? = null,
)

@Serializable
data class SessionEntry(
    val id: String,
    val title: String? = null,
    val updated_at: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val message_count: Int? = null,
)

@Serializable
data class SessionDetail(
    val id: String? = null,
    val session_id: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    val active_stream_id: String? = null,
    val pending_user_message: String? = null,
    val messages: List<ChatMessage> = emptyList(),
) {
    val effectiveId: String get() = session_id ?: id ?: ""
}

@Serializable
data class ChatMessage(
    val role: String,
    @SerialName("content") private val rawContent: kotlinx.serialization.json.JsonElement? = null,
    val timestamp: Long? = null,
    val attachments: List<Attachment> = emptyList(),
    @SerialName("_error") val error: Boolean = false,
    @SerialName("tool_calls") val toolCalls: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("provider_details") val providerDetails: String? = null,
) {
    val content: String
        get() = when (val c = rawContent) {
            null -> ""
            is kotlinx.serialization.json.JsonPrimitive -> c.contentOrEmpty()
            is JsonArray -> c.joinToString("\n") { part ->
                if (part is JsonObject) {
                    val text = part["text"] ?: part["content"]
                    (text as? kotlinx.serialization.json.JsonPrimitive)?.contentOrEmpty().orEmpty()
                } else ""
            }
            else -> c.toString()
        }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String =
    if (this.isString) this.content else this.toString()

@Serializable
data class Attachment(
    val name: String,
    val path: String,
    val mime: String,
    val size: Long? = null,
    @SerialName("is_image") val isImage: Boolean? = null,
)

@Serializable
data class ProfileEntry(
    val name: String,
    val active: Boolean = false,
    val model: String? = null,
    val provider: String? = null,
)

@Serializable
data class ModelEntry(
    val id: String,
    val name: String? = null,
    val provider: String? = null,
    val context_length: Int? = null,
)

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val type: String? = null, // "file" | "dir"
    val size: Long? = null,
    val modified: String? = null,
) {
    val isDir: Boolean get() = type == "dir"
}

enum class MemoryKind(val apiName: String, val displayName: String) {
    AGENT("MEMORY", "Agent memory"),
    USER("USER", "User profile"),
}

sealed interface LoginResult {
    data class Success(val url: String, val cookie: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}
