package dev.onelenyk.bringalive.conversation

import dev.onelenyk.bringalive.workspace.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class ConversationMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
)

@Serializable
data class PersistedMessage(
    val role: String,    // "user", "assistant", "tool"
    val content: String,
    val toolName: String? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
)

@OptIn(ExperimentalUuidApi::class)
class ConversationManager(
    private val workspace: WorkspaceManager,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _conversations = MutableStateFlow<List<ConversationMeta>>(emptyList())
    val conversations: StateFlow<List<ConversationMeta>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    fun init() {
        loadConversationList()
        // Resume last active or create new
        val active = workspace.read("sessions/active.txt")?.trim()
        if (active != null && _conversations.value.any { it.id == active }) {
            _activeConversationId.value = active
        } else if (_conversations.value.isNotEmpty()) {
            _activeConversationId.value = _conversations.value.first().id
        } else {
            newConversation()
        }
    }

    fun newConversation(): String {
        val id = Uuid.random().toString().take(12)
        val now = Clock.System.now().toEpochMilliseconds()
        val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val title = "Chat ${local.monthNumber}/${local.dayOfMonth} ${local.hour}:${local.minute.toString().padStart(2, '0')}"

        val meta = ConversationMeta(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
        )

        // Create empty session file
        workspace.write("sessions/$id.jsonl", "")

        // Update list
        _conversations.update { listOf(meta) + it }
        _activeConversationId.value = id
        workspace.write("sessions/active.txt", id)
        saveConversationList()

        return id
    }

    fun switchTo(conversationId: String) {
        _activeConversationId.value = conversationId
        workspace.write("sessions/active.txt", conversationId)
    }

    fun deleteConversation(conversationId: String) {
        workspace.write("sessions/$conversationId.jsonl", "")
        _conversations.update { list -> list.filter { it.id != conversationId } }
        saveConversationList()

        if (_activeConversationId.value == conversationId) {
            val remaining = _conversations.value
            if (remaining.isNotEmpty()) {
                switchTo(remaining.first().id)
            } else {
                newConversation()
            }
        }
    }

    fun updateTitle(conversationId: String, title: String) {
        _conversations.update { list ->
            list.map { if (it.id == conversationId) it.copy(title = title) else it }
        }
        saveConversationList()
    }

    // --- Message persistence ---

    fun loadMessages(conversationId: String): List<PersistedMessage> {
        val raw = workspace.read("sessions/$conversationId.jsonl") ?: return emptyList()
        return raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    json.decodeFromString<PersistedMessage>(line)
                } catch (_: Exception) {
                    null
                }
            }
    }

    fun appendMessage(conversationId: String, message: PersistedMessage) {
        val line = json.encodeToString(message)
        workspace.append("sessions/$conversationId.jsonl", line + "\n")

        // Update meta
        _conversations.update { list ->
            list.map {
                if (it.id == conversationId) it.copy(
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    messageCount = it.messageCount + 1,
                )
                else it
            }
        }
        saveConversationList()
    }

    fun activeMessages(): List<PersistedMessage> {
        val id = _activeConversationId.value ?: return emptyList()
        return loadMessages(id)
    }

    fun appendToActive(message: PersistedMessage) {
        val id = _activeConversationId.value ?: return
        appendMessage(id, message)
    }

    // --- Internal ---

    private fun loadConversationList() {
        val raw = workspace.read("sessions/index.json") ?: return
        try {
            val list = json.decodeFromString<List<ConversationMeta>>(raw)
            _conversations.value = list.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {}
    }

    private fun saveConversationList() {
        val data = json.encodeToString(_conversations.value)
        workspace.write("sessions/index.json", data)
    }
}
