package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for the parts of hermes-webui's HTTP API that we surface as
 * native UI: login (cookie capture), session list, health probe.
 *
 * Everything else (chat, streaming, file ops, slash commands, profiles,
 * skills, themes, etc.) is owned by the embedded WebView so we don't
 * reimplement the wheel.
 */
class HermesApi(
    private val store: SessionStore,
) {
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun base(): String = store.spaceUrl
        ?: throw IllegalStateException("Space URL not configured")

    /**
     * Posts the gateway token to /login and captures the resulting
     * session cookie. hermes-webui sets `webui_session=...` on success.
     *
     * Returns the captured cookie value; throws on any failure.
     */
    suspend fun login(spaceUrl: String, token: String): LoginResult = withContext(Dispatchers.IO) {
        val cleanUrl = spaceUrl.trimEnd('/')
        val body = FormBody.Builder()
            .add("password", token)
            .add("token", token)        // tolerate both webui's and our /hm form name
            .add("next", "/")
            .build()

        val req = Request.Builder()
            .url("$cleanUrl/login")
            .post(body)
            .header("User-Agent", "HermesMobile/1.0 (Android)")
            .header("Accept", "text/html,application/json")
            .build()

        val resp = http.newCall(req).execute()
        resp.use {
            val cookie = it.headers("Set-Cookie")
                .firstOrNull { c -> c.startsWith("webui_session=") }
                ?.substringBefore(';')
                ?.removePrefix("webui_session=")

            // Success looks like a 302 redirect with the cookie header set.
            if (cookie.isNullOrBlank() && it.code !in 200..399) {
                return@withContext LoginResult.Failure(
                    "Login failed (HTTP ${it.code}). Check your token."
                )
            }
            if (cookie.isNullOrBlank()) {
                // No cookie but 2xx — likely password not configured server-side
                return@withContext LoginResult.Failure(
                    "No session cookie returned. Is HERMES_WEBUI_PASSWORD set on the Space?"
                )
            }
            // Persist
            store.spaceUrl = cleanUrl
            store.gatewayToken = token
            store.sessionCookie = cookie
            LoginResult.Success(cleanUrl, cookie)
        }
    }

    suspend fun health(): HealthSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${base()}/health")
                .header("User-Agent", "HermesMobile/1.0 (Android)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.decodeFromString<HealthSnapshot>(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    suspend fun sessions(): List<SessionEntry> = withContext(Dispatchers.IO) {
        val cookie = store.sessionCookie ?: return@withContext emptyList()
        runCatching {
            val req = Request.Builder()
                .url("${base()}/api/sessions")
                .header("Cookie", "webui_session=$cookie")
                .header("User-Agent", "HermesMobile/1.0 (Android)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                // hermes-webui returns either a bare array or { sessions: [...] }
                val element = json.parseToJsonElement(body)
                val arr = when (element) {
                    is kotlinx.serialization.json.JsonArray -> element
                    is kotlinx.serialization.json.JsonObject ->
                        element["sessions"] as? kotlinx.serialization.json.JsonArray
                    else -> null
                } ?: return@withContext emptyList()
                arr.mapNotNull { node ->
                    runCatching {
                        json.decodeFromJsonElement(SessionEntry.serializer(), node)
                    }.getOrNull()
                }
            }
        }.getOrElse { emptyList() }
    }
}

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
)

sealed interface LoginResult {
    data class Success(val url: String, val cookie: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}
