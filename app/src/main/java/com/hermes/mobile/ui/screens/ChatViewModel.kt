package com.hermes.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.Attachment
import com.hermes.mobile.data.ChatMessage
import com.hermes.mobile.data.HermesApi
import com.hermes.mobile.data.HermesEvent
import com.hermes.mobile.data.HermesSseClient
import com.hermes.mobile.data.SessionEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns chat state for a single session.
 *
 * Holds the message list (history + the live assistant turn being streamed),
 * a reasoning trace (Claude/o3 thinking), in-flight tool calls, and any
 * pending approval card. Calling [send] starts a new turn; [cancel] aborts.
 */
class ChatViewModel(
    private val api: HermesApi,
    private val sse: HermesSseClient,
    private val sessionStore: com.hermes.mobile.data.SessionStore? = null,
) : ViewModel() {

    var sessionId by mutableStateOf<String?>(null)
        private set

    val messages = mutableStateListOf<ChatBubble>()

    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()

    var sessionTitle by mutableStateOf("New chat")
        private set

    private var streamJob: Job? = null
    private var streamId: String? = null

    /** Switch to a different session and load its history. */
    fun loadSession(id: String, title: String? = null) {
        if (sessionId == id) return
        cancelStream()
        sessionId = id
        sessionTitle = title?.takeIf { it.isNotBlank() } ?: "Loading…"
        messages.clear()
        _state.value = ChatState(loading = true)
        viewModelScope.launch {
            val detail = api.getSession(id, withMessages = true, msgLimit = 200)
            messages.clear()
            detail?.messages?.forEach { messages.add(ChatBubble.fromMessage(it)) }
            sessionTitle = detail?.title?.takeIf { it.isNotBlank() } ?: "Session"
            _state.value = ChatState(loading = false)
        }
    }

    /** Start a brand-new session and switch to it. */
    fun newSession() {
        cancelStream()
        viewModelScope.launch {
            _state.value = ChatState(loading = true)
            val id = api.createSession()
            if (id != null) {
                sessionId = id
                sessionTitle = "New chat"
                messages.clear()
            }
            _state.value = ChatState(loading = false)
        }
    }

    fun send(message: String, attachments: List<Attachment> = emptyList()) {
        val sid = sessionId ?: run {
            // Auto-create on first send if no session is active
            viewModelScope.launch {
                val id = api.createSession() ?: return@launch
                sessionId = id
                send(message, attachments)
            }
            return
        }
        if (message.isBlank() && attachments.isEmpty()) return

        // Echo the user message immediately for snappy UX
        messages.add(
            ChatBubble(
                role = "user",
                content = message,
                attachments = attachments,
                streaming = false,
            )
        )

        // Placeholder assistant bubble we'll mutate as tokens arrive
        val assistantIdx = messages.size
        messages.add(ChatBubble(role = "assistant", content = "", streaming = true))

        _state.value = _state.value.copy(streaming = true, error = null)

        streamJob = viewModelScope.launch {
            val newStreamId = api.chatStart(
                sessionId = sid,
                message = message,
                attachments = attachments,
                model = sessionStore?.defaultModel,
            )
            if (newStreamId.isNullOrBlank()) {
                mutateAssistant(assistantIdx) { it.copy(streaming = false, content = "_failed to start stream_", error = true) }
                _state.value = _state.value.copy(streaming = false, error = "Could not start stream")
                return@launch
            }
            streamId = newStreamId
            consumeStream(newStreamId, assistantIdx)
        }
    }

    private suspend fun consumeStream(streamId: String, assistantIdx: Int) {
        val url = api.chatStreamUrl(streamId)
        val sb = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = mutableListOf<ToolCallView>()
        var pendingApproval: HermesEvent.ApprovalRequest? = null

        try {
            sse.subscribe(url).collect { ev ->
                when (ev) {
                    is HermesEvent.Token -> {
                        sb.append(ev.text)
                        mutateAssistant(assistantIdx) { it.copy(content = sb.toString(), streaming = true) }
                    }
                    is HermesEvent.Reasoning -> {
                        reasoning.append(ev.text)
                        mutateAssistant(assistantIdx) { it.copy(reasoning = reasoning.toString()) }
                    }
                    is HermesEvent.ToolCall -> {
                        toolCalls.add(ToolCallView(name = ev.name, args = ev.args, result = null, id = ev.id))
                        mutateAssistant(assistantIdx) { it.copy(toolCalls = toolCalls.toList()) }
                    }
                    is HermesEvent.ToolResult -> {
                        val idx = toolCalls.indexOfLast { it.id == ev.id }
                        if (idx >= 0) {
                            toolCalls[idx] = toolCalls[idx].copy(result = ev.result)
                        } else {
                            toolCalls.add(ToolCallView(name = ev.name, args = "", result = ev.result, id = ev.id))
                        }
                        mutateAssistant(assistantIdx) { it.copy(toolCalls = toolCalls.toList()) }
                    }
                    is HermesEvent.ApprovalRequest -> {
                        pendingApproval = ev
                        _state.value = _state.value.copy(approval = ev)
                    }
                    is HermesEvent.TitleChanged -> {
                        sessionTitle = ev.title
                    }
                    is HermesEvent.Error -> {
                        mutateAssistant(assistantIdx) { it.copy(error = true, streaming = false) }
                        _state.value = _state.value.copy(streaming = false, error = ev.message)
                    }
                    HermesEvent.Done, HermesEvent.Closed -> {
                        mutateAssistant(assistantIdx) { it.copy(streaming = false) }
                        _state.value = _state.value.copy(streaming = false)
                    }
                    HermesEvent.Open -> { /* connection opened */ }
                    is HermesEvent.Other -> { /* ignored */ }
                }
            }
        } catch (e: Exception) {
            mutateAssistant(assistantIdx) {
                it.copy(streaming = false, error = true,
                    content = if (sb.isNotEmpty()) sb.toString() else "_${e.message ?: "stream error"}_")
            }
            _state.value = _state.value.copy(streaming = false, error = e.message)
        } finally {
            this.streamId = null
        }
    }

    fun cancel() {
        cancelStream()
        viewModelScope.launch {
            streamId?.let { api.chatCancel(it) }
        }
    }

    private fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
    }

    private fun mutateAssistant(idx: Int, block: (ChatBubble) -> ChatBubble) {
        if (idx in messages.indices) {
            messages[idx] = block(messages[idx])
        }
    }

    override fun onCleared() {
        cancelStream()
        super.onCleared()
    }
}

data class ChatState(
    val loading: Boolean = false,
    val streaming: Boolean = false,
    val error: String? = null,
    val approval: HermesEvent.ApprovalRequest? = null,
)

/** UI-side message model. Decoupled from the network model so we can mutate live. */
data class ChatBubble(
    val role: String,
    val content: String,
    val streaming: Boolean = false,
    val error: Boolean = false,
    val reasoning: String = "",
    val toolCalls: List<ToolCallView> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis() / 1000,
) {
    val isUser get() = role == "user"
    val isAssistant get() = role == "assistant"

    companion object {
        fun fromMessage(m: ChatMessage): ChatBubble = ChatBubble(
            role = m.role,
            content = m.content,
            streaming = false,
            error = m.error,
            attachments = m.attachments,
            timestamp = m.timestamp ?: 0L,
        )
    }
}

data class ToolCallView(
    val name: String,
    val args: String,
    val result: String?,
    val id: String?,
)
