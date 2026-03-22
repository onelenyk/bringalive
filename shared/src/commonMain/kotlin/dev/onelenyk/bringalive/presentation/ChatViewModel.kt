package dev.onelenyk.bringalive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.onelenyk.bringalive.agent.AgentEvent
import dev.onelenyk.bringalive.agent.BringaliveAgent
import dev.onelenyk.bringalive.conversation.ConversationManager
import dev.onelenyk.bringalive.conversation.ConversationMeta
import dev.onelenyk.bringalive.model.ChatMessage
import dev.onelenyk.bringalive.model.MessageRole
import dev.onelenyk.bringalive.model.ToolStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val activeTools: List<ToolStatus> = emptyList(),
    val error: String? = null,
    val conversations: List<ConversationMeta> = emptyList(),
    val activeConversationId: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class ChatViewModel(
    private val agent: BringaliveAgent,
    private val conversationManager: ConversationManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var currentJob: Job? = null

    init {
        observeAgentEvents()
        observeConversations()
        loadActiveConversation()
    }

    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun onSendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isGenerating) return

        val userMessage = ChatMessage(
            id = Uuid.random().toString(),
            role = MessageRole.USER,
            content = text,
        )

        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isGenerating = true,
                streamingText = "",
                activeTools = emptyList(),
                error = null,
            )
        }

        // Auto-title: use first message as title
        val activeId = conversationManager.activeConversationId.value
        if (activeId != null) {
            val meta = _state.value.conversations.find { it.id == activeId }
            if (meta != null && meta.messageCount == 0) {
                conversationManager.updateTitle(activeId, text.take(40))
            }
        }

        currentJob = viewModelScope.launch {
            agent.run(text)
        }
    }

    fun onNewConversation() {
        agent.resetAgent()
        conversationManager.newConversation()
        loadActiveConversation()
    }

    fun onSwitchConversation(conversationId: String) {
        agent.resetAgent()
        conversationManager.switchTo(conversationId)
        loadActiveConversation()
    }

    fun onDeleteConversation(conversationId: String) {
        conversationManager.deleteConversation(conversationId)
        loadActiveConversation()
    }

    fun onDismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun loadActiveConversation() {
        val messages = conversationManager.activeMessages().map { msg ->
            ChatMessage(
                id = Uuid.random().toString(),
                role = when (msg.role) {
                    "user" -> MessageRole.USER
                    "assistant" -> MessageRole.ASSISTANT
                    else -> MessageRole.TOOL
                },
                content = msg.content,
                toolName = msg.toolName,
            )
        }
        _state.update {
            it.copy(
                messages = messages,
                streamingText = "",
                activeTools = emptyList(),
                isGenerating = false,
                error = null,
                activeConversationId = conversationManager.activeConversationId.value,
            )
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            conversationManager.conversations.collect { convos ->
                _state.update { it.copy(conversations = convos) }
            }
        }
        viewModelScope.launch {
            conversationManager.activeConversationId.collect { id ->
                _state.update { it.copy(activeConversationId = id) }
            }
        }
    }

    private fun observeAgentEvents() {
        viewModelScope.launch {
            agent.streamEvents.collect { event ->
                when (event) {
                    is AgentEvent.Started -> {}

                    is AgentEvent.TextDelta -> {
                        _state.update {
                            it.copy(streamingText = it.streamingText + event.text)
                        }
                    }

                    is AgentEvent.ToolCallStarted -> {
                        _state.update {
                            it.copy(
                                activeTools = it.activeTools + ToolStatus(
                                    name = event.name,
                                    status = "calling...",
                                )
                            )
                        }
                    }

                    is AgentEvent.ToolExecuting -> {
                        _state.update {
                            it.copy(
                                activeTools = it.activeTools.map { tool ->
                                    if (tool.name == event.name) tool.copy(status = "running...")
                                    else tool
                                }
                            )
                        }
                    }

                    is AgentEvent.ToolCompleted -> {
                        _state.update {
                            it.copy(
                                activeTools = it.activeTools.map { tool ->
                                    if (tool.name == event.name) tool.copy(
                                        status = "done",
                                        result = event.result,
                                    )
                                    else tool
                                }
                            )
                        }
                    }

                    is AgentEvent.StreamEnd -> {}

                    is AgentEvent.Completed -> {
                        val responseText = _state.value.streamingText.ifBlank { event.fullText }

                        val toolMessages = _state.value.activeTools.map { tool ->
                            ChatMessage(
                                id = Uuid.random().toString(),
                                role = MessageRole.TOOL,
                                content = tool.result ?: tool.status,
                                toolName = tool.name,
                            )
                        }

                        val assistantMessage = ChatMessage(
                            id = Uuid.random().toString(),
                            role = MessageRole.ASSISTANT,
                            content = responseText,
                        )

                        _state.update {
                            it.copy(
                                messages = it.messages + toolMessages + assistantMessage,
                                isGenerating = false,
                                streamingText = "",
                                activeTools = emptyList(),
                            )
                        }
                    }

                    is AgentEvent.Error -> {
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                streamingText = "",
                                activeTools = emptyList(),
                                error = event.message,
                            )
                        }
                    }
                }
            }
        }
    }
}
