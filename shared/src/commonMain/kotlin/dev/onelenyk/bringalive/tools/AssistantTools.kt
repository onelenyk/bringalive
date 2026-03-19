package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@LLMDescription("General assistant tools for time, memory, and web")
class AssistantTools(
    private val memoryStore: MemoryStore,
) : ToolSet {

    @Tool
    @LLMDescription("Get the current date and time in the user's timezone")
    fun getCurrentTime(
        @LLMDescription("IANA timezone ID, e.g. 'Europe/Kyiv'. Defaults to UTC if unknown.")
        timezone: String = "UTC",
    ): String {
        val tz = try {
            TimeZone.of(timezone)
        } catch (_: Exception) {
            TimeZone.UTC
        }
        val now = Clock.System.now()
        val local = now.toLocalDateTime(tz)
        return "Current time: ${local.date} ${local.time} ($timezone)"
    }

    @Tool
    @LLMDescription("Save a fact or preference to persistent memory so you can recall it in future conversations")
    fun saveMemory(
        @LLMDescription("Category: 'preference', 'fact', or 'instruction'")
        category: String,
        @LLMDescription("The content to remember, e.g. 'User's name is Nazar' or 'User prefers dark mode'")
        content: String,
    ): String {
        memoryStore.save(category, content)
        return "Saved to memory: [$category] $content"
    }

    @Tool
    @LLMDescription("Delete a specific entry from persistent memory")
    fun forgetMemory(
        @LLMDescription("The content or keyword to search for and remove from memory")
        query: String,
    ): String {
        val removed = memoryStore.forget(query)
        return if (removed) {
            "Removed from memory: $query"
        } else {
            "No matching memory found for: $query"
        }
    }

    @Tool
    @LLMDescription("List all entries currently stored in persistent memory")
    fun listMemory(): String {
        val entries = memoryStore.getAll()
        if (entries.isEmpty()) return "Memory is empty."
        return entries.joinToString("\n") { "- [${it.category}] ${it.content}" }
    }
}
