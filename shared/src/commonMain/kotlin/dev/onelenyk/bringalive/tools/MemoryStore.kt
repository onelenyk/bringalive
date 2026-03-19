package dev.onelenyk.bringalive.tools

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MemoryEntry(
    val category: String,
    val content: String,
)

class MemoryStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    fun save(category: String, content: String) {
        val entries = loadEntries().toMutableList()
        // Update if same content exists, otherwise add
        val existing = entries.indexOfFirst {
            it.content.equals(content, ignoreCase = true)
        }
        if (existing >= 0) {
            entries[existing] = MemoryEntry(category, content)
        } else {
            entries.add(MemoryEntry(category, content))
        }
        saveEntries(entries)
    }

    fun forget(query: String): Boolean {
        val entries = loadEntries().toMutableList()
        val removed = entries.removeAll {
            it.content.contains(query, ignoreCase = true) ||
                it.category.equals(query, ignoreCase = true)
        }
        if (removed) saveEntries(entries)
        return removed
    }

    fun getAll(): List<MemoryEntry> = loadEntries()

    fun asSystemPromptSection(): String {
        val entries = loadEntries()
        if (entries.isEmpty()) return ""
        val formatted = entries.joinToString("\n") { "- [${it.category}] ${it.content}" }
        return "\n\n## Your Memory\n$formatted"
    }

    private fun loadEntries(): List<MemoryEntry> {
        val raw = settings.getStringOrNull(KEY_MEMORY) ?: return emptyList()
        return try {
            json.decodeFromString<List<MemoryEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveEntries(entries: List<MemoryEntry>) {
        settings.putString(KEY_MEMORY, json.encodeToString(entries))
    }

    companion object {
        private const val KEY_MEMORY = "bringalive_memory"
    }
}
