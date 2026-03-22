package dev.onelenyk.bringalive.conversation

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo

/**
 * Koog ChatHistoryProvider that loads persisted messages from ConversationManager.
 * Messages are persisted incrementally by BringaliveAgent, so store() is a no-op.
 */
class JsonlChatHistoryProvider(
    private val conversationManager: ConversationManager,
) : ChatHistoryProvider {

    override suspend fun store(sessionId: String, messages: List<Message>) {
        // No-op — we persist messages incrementally in BringaliveAgent.run()
    }

    override suspend fun load(sessionId: String): List<Message> {
        return conversationManager.activeMessages().mapNotNull { it.toKoogMessage() }
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
        else -> null
    }
}
