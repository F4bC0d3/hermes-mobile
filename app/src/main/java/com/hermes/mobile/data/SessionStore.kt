package com.hermes.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted credential storage for the Space URL, GATEWAY_TOKEN, and the
 * webui_session cookie value.
 *
 * Uses Android Keystore via androidx.security.crypto so values stay
 * encrypted at rest. Tokens never travel through plain SharedPreferences.
 */
class SessionStore(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hermes_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var spaceUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value?.trimEnd('/')).apply()

    var gatewayToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var sessionCookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "DARK") ?: "DARK"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var defaultModel: String?
        get() = prefs.getString(KEY_MODEL, null)
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    fun isConfigured(): Boolean = !spaceUrl.isNullOrBlank() && !gatewayToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_URL = "space_url"
        private const val KEY_TOKEN = "gateway_token"
        private const val KEY_COOKIE = "webui_cookie"
        private const val KEY_DARK = "dark_theme"
        private const val KEY_THEME = "theme_name"
        private const val KEY_MODEL = "default_model"
    }
}
