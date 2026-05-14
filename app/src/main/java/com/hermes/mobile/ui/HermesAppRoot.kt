package com.hermes.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hermes.mobile.data.HermesApi
import com.hermes.mobile.data.SessionStore
import com.hermes.mobile.ui.screens.ChatShell
import com.hermes.mobile.ui.screens.LoginScreen

@Composable
fun HermesAppRoot(
    sessionStore: SessionStore,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val api = remember { HermesApi(sessionStore) }
    var loggedIn by remember { mutableStateOf(sessionStore.isConfigured()) }

    if (!loggedIn) {
        LoginScreen(
            api = api,
            initialUrl = sessionStore.spaceUrl.orEmpty(),
            onSuccess = { loggedIn = true },
        )
    } else {
        ChatShell(
            sessionStore = sessionStore,
            api = api,
            darkTheme = darkTheme,
            onToggleTheme = onToggleTheme,
            onLogout = {
                sessionStore.clear()
                loggedIn = false
            },
        )
    }
}
