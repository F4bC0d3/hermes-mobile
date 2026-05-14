package com.hermes.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.HermesApi
import com.hermes.mobile.data.SessionEntry
import com.hermes.mobile.data.SessionStore
import com.hermes.mobile.ui.components.AppMenuSheet
import com.hermes.mobile.ui.components.HermesWebView
import com.hermes.mobile.ui.components.SessionsDrawer
import com.hermes.mobile.ui.components.WebViewControls
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatShell(
    sessionStore: SessionStore,
    api: HermesApi,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var sessions by remember { mutableStateOf<List<SessionEntry>>(emptyList()) }
    var sessionsLoading by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("New chat") }

    val controls = remember { WebViewControls() }

    fun refreshSessions() {
        scope.launch {
            sessionsLoading = true
            sessions = api.sessions()
            sessionsLoading = false
        }
    }

    LaunchedEffect(Unit) { refreshSessions() }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = drawerState.isClosed && controls.canGoBack()) {
        controls.goBack()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !controls.busy,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                SessionsDrawer(
                    sessions = sessions,
                    loading = sessionsLoading,
                    onNewChat = {
                        controls.loadUrl("${sessionStore.spaceUrl}/")
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { s ->
                        controls.loadUrl("${sessionStore.spaceUrl}/session/${s.id}")
                        currentTitle = s.title ?: "Session"
                        scope.launch { drawerState.close() }
                    },
                    onRefresh = { refreshSessions() },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        menuOpen = true
                    },
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ─── Top bar (Claude/ChatGPT style) ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Filled.Menu, "Sessions",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                )
                IconButton(onClick = {
                    controls.loadUrl("${sessionStore.spaceUrl}/")
                    currentTitle = "New chat"
                }) {
                    Icon(Icons.Filled.Add, "New chat",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "Menu",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            // ─── Embedded WebUI (the actual chat surface) ────────────
            Box(modifier = Modifier.weight(1f)) {
                HermesWebView(
                    sessionStore = sessionStore,
                    controls = controls,
                    darkTheme = darkTheme,
                    onTitleChanged = { newTitle ->
                        if (!newTitle.isNullOrBlank() && newTitle != "Hermes") {
                            currentTitle = newTitle
                        }
                    },
                )
            }
        }
    }

    if (menuOpen) {
        AppMenuSheet(
            darkTheme = darkTheme,
            onDismiss = { menuOpen = false },
            onToggleTheme = {
                onToggleTheme()
                menuOpen = false
            },
            onOpenSettings = {
                controls.loadUrl("${sessionStore.spaceUrl}/")
                controls.evalJs("document.querySelector('[data-action=\"open-settings\"]')?.click();")
                menuOpen = false
            },
            onOpenDashboard = {
                controls.loadUrl("${sessionStore.spaceUrl}/hm")
                menuOpen = false
            },
            onReload = {
                controls.reload()
                menuOpen = false
            },
            onLogout = {
                menuOpen = false
                onLogout()
            },
        )
    }
}
