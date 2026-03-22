package dev.onelenyk.bringalive.conversation

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo

/**
 * Koog ChatHistoryProvider that loads persisted messages from ConversationManager.
 * Includes tool call/result context so the LLM remembers what tools returned.
 * Messages are persisted incrementally by BringaliveAgent, so store() is a no-op.
 */
class JsonlChatHistoryProvider(
    private val conversationManager: ConversationManager,
) : ChatHistoryProvider {

    override suspend fun store(sessionId: String, messages: List<Message>) {
        // No-op — we persist messages incrementally in BringaliveAgent.run()
    }

    override suspend fun load(sessionId: String): List<Message> {
        val persisted = conversationManager.activeMessages()
        // Merge consecutive tool_call + tool_result into the assistant/user flow
        // so the LLM sees the full context of what happened
        return persisted.mapNotNull { it.toKoogMessage() }
    }
}

private fun PersistedMessage.toKoogMessage(): Message? {
    return when (role) {
        "user" -> Message.User(
            parts = listOf(ContentPart.Text(content)),
            metaInfo = RequestMetaInfo.Empty,
        )
        "assistant" -> Message.Assistant(
            parts = listOf(ContentPart.Text(content)),
            metaInfo = ResponseMetaInfo.Empty,
        )
        // Inject tool results as user messages so LLM sees the context
        // Format: "[Tool: toolName] result"
        "tool_result" -> Message.User(
            parts = listOf(ContentPart.Text("[Previous tool result from $toolName]: $content")),
            metaInfo = RequestMetaInfo.Empty,
        )
        // Skip tool_call entries — the LLM already knows what it called
        "tool_call" -> null
        else -> null
    }
}
