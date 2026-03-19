package dev.onelenyk.bringalive.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
    val isStreaming: Boolean = false,
)

data class ToolStatus(
    val name: String,
    val status: String,
    val result: String? = null,
)
