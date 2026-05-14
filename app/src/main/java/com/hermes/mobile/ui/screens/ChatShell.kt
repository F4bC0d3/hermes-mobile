package com.hermes.mobile.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.HermesApi
import com.hermes.mobile.data.HermesSseClient
import com.hermes.mobile.data.SessionEntry
import com.hermes.mobile.data.SessionStore
import com.hermes.mobile.ui.components.AppMenuSheet
import com.hermes.mobile.ui.components.ChatRow
import com.hermes.mobile.ui.components.Composer
import com.hermes.mobile.ui.components.SessionsDrawer
import com.hermes.mobile.ui.components.SlashCommandSheet
import com.hermes.mobile.ui.components.UiAttachment
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatShell(
    sessionStore: SessionStore,
    api: HermesApi,
    sse: HermesSseClient,
    currentTheme: com.hermes.mobile.ui.theme.HermesTheme,
    onLogout: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onActiveSessionChanged: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }

    val viewModel = remember(api, sse) { ChatViewModel(api, sse, sessionStore) }
    val state by viewModel.state.collectAsState()

    var sessions by remember { mutableStateOf<List<SessionEntry>>(emptyList()) }
    var sessionsLoading by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val pendingAttachments = remember { mutableStateListOf<UiAttachment>() }

    val listState = rememberLazyListState()

    fun refreshSessions() {
        scope.launch {
            sessionsLoading = true
            sessions = api.listSessions()
            sessionsLoading = false
        }
    }

    LaunchedEffect(viewModel.sessionId) {
        onActiveSessionChanged(viewModel.sessionId)
    }

    LaunchedEffect(Unit) {
        refreshSessions()
        val first = api.listSessions().firstOrNull()
        if (first != null) viewModel.loadSession(first.id, first.title)
        else viewModel.newSession()
    }

    // Auto-scroll on new messages and during streaming
    LaunchedEffect(viewModel.messages.size, state.streaming) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.lastIndex)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = readUriBytes(context.contentResolver, uri)
                val name = queryDisplayName(context.contentResolver, uri) ?: "attachment"
                val mime = context.contentResolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', ""))
                    ?: "application/octet-stream"
                val sid = viewModel.sessionId
                if (sid != null && bytes != null) {
                    val uploaded = api.uploadAttachment(sid, name, mime, bytes)
                    if (uploaded != null) {
                        pendingAttachments.add(
                            UiAttachment(
                                id = UUID.randomUUID().toString(),
                                name = uploaded.name,
                                mime = uploaded.mime,
                                size = uploaded.size ?: bytes.size.toLong(),
                                uploadedPath = uploaded.path,
                            )
                        )
                    } else {
                        snackbar.showSnackbar("Upload failed")
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !state.streaming,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                SessionsDrawer(
                    sessions = sessions,
                    loading = sessionsLoading,
                    onNewChat = {
                        viewModel.newSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { s ->
                        viewModel.loadSession(s.id, s.title)
                        scope.launch { drawerState.close() }
                    },
                    onRefresh = { refreshSessions() },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        menuOpen = true
                    },
                )
            }
        },
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            // Top bar
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
                    text = viewModel.sessionTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.newSession() }) {
                    Icon(Icons.Filled.Add, "New chat",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "Menu",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Message list
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
                if (viewModel.messages.isEmpty() && !state.loading) {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(viewModel.messages.size) { idx ->
                            ChatRow(viewModel.messages[idx])
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                )
            }

            // Slash command suggestions (only when input starts with /)
            if (inputText.startsWith("/") && !inputText.contains(' ')) {
                SlashCommandSheet(
                    query = inputText,
                    onSelect = { cmd -> inputText = "$cmd " },
                )
            }

            // Composer
            Composer(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    val msg = inputText.trim()
                    val attachments = pendingAttachments
                        .filter { it.uploadedPath != null }
                        .map {
                            com.hermes.mobile.data.Attachment(
                                name = it.name,
                                path = it.uploadedPath!!,
                                mime = it.mime,
                                size = it.size,
                                isImage = it.mime.startsWith("image/"),
                            )
                        }
                    if (msg.isNotEmpty() || attachments.isNotEmpty()) {
                        viewModel.send(msg, attachments)
                        inputText = ""
                        pendingAttachments.clear()
                    }
                },
                onStop = { viewModel.cancel() },
                streaming = state.streaming,
                attachments = pendingAttachments.toList(),
                onAddAttachment = {
                    filePickerLauncher.launch("*/*")
                },
                onRemoveAttachment = { att -> pendingAttachments.remove(att) },
                onVoice = { spoken ->
                    inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                },
            )
        }
    }

    if (menuOpen) {
        AppMenuSheet(
            currentTheme = currentTheme,
            onDismiss = { menuOpen = false },
            onOpenModels = {
                menuOpen = false
                onOpenModels()
            },
            onOpenProfiles = {
                menuOpen = false
                onOpenProfiles()
            },
            onOpenMemory = {
                menuOpen = false
                onOpenMemory()
            },
            onOpenWorkspace = {
                menuOpen = false
                onOpenWorkspace()
            },
            onOpenSettings = {
                menuOpen = false
                onOpenSettings()
            },
            onReload = {
                viewModel.sessionId?.let { id -> viewModel.loadSession(id) }
                menuOpen = false
            },
            onLogout = {
                menuOpen = false
                onLogout()
            },
        )
    }

    state.error?.let { err ->
        LaunchedEffect(err) { snackbar.showSnackbar(err) }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "🪽",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask Hermes anything",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Type a message below or use a slash command like /help",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun readUriBytes(resolver: ContentResolver, uri: Uri): ByteArray? =
    runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}.getOrNull()
