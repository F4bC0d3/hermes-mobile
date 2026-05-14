package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for hermes-webui's HTTP API.
 *
 * Endpoints used:
 *   - POST /api/auth/login    JSON {"password": "..."}  -> sets hermes_session cookie
 *   - GET  /api/sessions      -> list of session metadata
 *   - GET  /health            -> liveness probe (unauthenticated, surfaced by router)
 *
 * Everything else is owned by the embedded WebView so we don't reimplement
 * the WebUI surface.
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
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun base(): String = store.spaceUrl
        ?: throw IllegalStateException("Space URL not configured")

    /**
     * Posts the gateway token to /api/auth/login and captures the
     * hermes_session cookie from the response.
     */
    suspend fun login(spaceUrl: String, token: String): LoginResult = withContext(Dispatchers.IO) {
        val cleanUrl = spaceUrl.trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        // Build the JSON body via the JSON DSL so the password is correctly escaped.
        val payload = buildJsonObject { put("password", token) }.toString()

        val req = Request.Builder()
            .url("$cleanUrl/api/auth/login")
            .post(payload.toRequestBody(jsonMedia))
            .header("User-Agent", "HermesMobile/1.0 (Android)")
            .header("Accept", "application/json")
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                val cookie = resp.headers("Set-Cookie")
                    .firstOrNull { c -> c.startsWith("hermes_session=") }
                    ?.substringBefore(';')
                    ?.removePrefix("hermes_session=")

                when {
                    resp.code == 401 ->
                        LoginResult.Failure("Wrong password. Check your GATEWAY_TOKEN.")
                    resp.code == 429 ->
                        LoginResult.Failure("Too many attempts. Wait a minute and try again.")
                    resp.code == 404 ->
                        LoginResult.Failure(
                            "Login endpoint not found at $cleanUrl. " +
                                "Make sure the URL points to your Hermes WebUI Space."
                        )
                    !resp.isSuccessful ->
                        LoginResult.Failure("Login failed (HTTP ${resp.code}).")
                    cookie.isNullOrBlank() ->
                        LoginResult.Failure(
                            "Login succeeded but no session cookie was returned. " +
                                "Check that HERMES_WEBUI_PASSWORD (or GATEWAY_TOKEN) is " +
                                "set on the Space."
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
                .header("Cookie", "hermes_session=$cookie")
                .header("User-Agent", "HermesMobile/1.0 (Android)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
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
