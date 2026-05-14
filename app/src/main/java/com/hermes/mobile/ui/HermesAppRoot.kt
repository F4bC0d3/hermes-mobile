package com.hermes.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hermes.mobile.data.HermesApi
import com.hermes.mobile.data.HermesSseClient
import com.hermes.mobile.data.SessionStore
import com.hermes.mobile.ui.screens.ChatShell
import com.hermes.mobile.ui.screens.LoginScreen
import com.hermes.mobile.ui.screens.MemoryScreen
import com.hermes.mobile.ui.screens.ModelsScreen
import com.hermes.mobile.ui.screens.ProfilesScreen
import com.hermes.mobile.ui.screens.SettingsScreen
import com.hermes.mobile.ui.screens.WorkspaceScreen
import com.hermes.mobile.ui.theme.HermesTheme

private sealed interface Route {
    data object Chat : Route
    data object Memory : Route
    data object Profiles : Route
    data object Models : Route
    data object Workspace : Route
    data object Settings : Route
}

@Composable
fun HermesAppRoot(
    sessionStore: SessionStore,
    currentTheme: HermesTheme,
    onThemeChange: (HermesTheme) -> Unit,
) {
    val api = remember { HermesApi(sessionStore) }
    val sse = remember { HermesSseClient(sessionStore) }
    var loggedIn by remember { mutableStateOf(sessionStore.isConfigured()) }
    var route by remember { mutableStateOf<Route>(Route.Chat) }
    val activeSessionId = remember { mutableStateOf<String?>(null) }

    if (!loggedIn) {
        LoginScreen(
            api = api,
            initialUrl = sessionStore.spaceUrl.orEmpty(),
            onSuccess = {
                loggedIn = true
                route = Route.Chat
            },
        )
        return
    }

    when (route) {
        Route.Chat -> ChatShell(
            sessionStore = sessionStore,
            api = api,
            sse = sse,
            currentTheme = currentTheme,
            onLogout = {
                sessionStore.clear()
                loggedIn = false
            },
            onOpenMemory = { route = Route.Memory },
            onOpenWorkspace = { route = Route.Workspace },
            onOpenProfiles = { route = Route.Profiles },
            onOpenModels = { route = Route.Models },
            onOpenSettings = { route = Route.Settings },
            onActiveSessionChanged = { activeSessionId.value = it },
        )
        Route.Memory -> MemoryScreen(api = api, onBack = { route = Route.Chat })
        Route.Profiles -> ProfilesScreen(api = api, onBack = { route = Route.Chat })
        Route.Models -> ModelsScreen(
            api = api,
            sessionStore = sessionStore,
            onBack = { route = Route.Chat },
        )
        Route.Workspace -> WorkspaceScreen(
            api = api,
            sessionId = activeSessionId.value,
            onBack = { route = Route.Chat },
        )
        Route.Settings -> SettingsScreen(
            sessionStore = sessionStore,
            currentTheme = currentTheme,
            onThemeChange = onThemeChange,
            onBack = { route = Route.Chat },
            onLogout = {
                sessionStore.clear()
                loggedIn = false
                route = Route.Chat
            },
        )
    }
}
