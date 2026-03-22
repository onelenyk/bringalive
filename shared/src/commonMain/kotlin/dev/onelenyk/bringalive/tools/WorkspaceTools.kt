package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.workspace.WorkspaceManager
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class SaveMemoryArgs(val category: String, val content: String) : ToolArgs

@Serializable
data class ForgetMemoryArgs(val query: String) : ToolArgs

@Serializable
data class UpdateUserArgs(val section: String, val content: String) : ToolArgs

@Serializable
data class AddDailyNoteArgs(val note: String) : ToolArgs

@Serializable
data class SearchHistoryArgs(val query: String) : ToolArgs

@Serializable
data class ReadFileArgs(val filename: String) : ToolArgs

@Serializable
data class WriteFileArgs(val filename: String, val content: String) : ToolArgs

@Serializable
data class EmptyArgs(val placeholder: String = "") : ToolArgs

// --- Tools ---

class SaveMemoryTool(private val workspace: WorkspaceManager) : SimpleTool<SaveMemoryArgs>(
    argsType = typeToken<SaveMemoryArgs>(),
    name = "saveMemory",
    description = "Save a fact, preference, or decision to MEMORY.md for long-term recall. Categories: fact, preference, instruction, decision, project.",
) {
    override suspend fun execute(args: SaveMemoryArgs): String {
        val memory = workspace.read("MEMORY.md") ?: "# Memory\n"
        val sectionHeader = "## ${args.category.replaceFirstChar { it.uppercase() }}s"
        val updated = if (memory.contains(sectionHeader)) {
            memory.replace(sectionHeader, "$sectionHeader\n- ${args.content}")
        } else {
            "$memory\n$sectionHeader\n- ${args.content}"
        }
        workspace.write("MEMORY.md", updated)
        return "Saved to memory: [${args.category}] ${args.content}"
    }
}

class ForgetMemoryTool(private val workspace: WorkspaceManager) : SimpleTool<ForgetMemoryArgs>(
    argsType = typeToken<ForgetMemoryArgs>(),
    name = "forgetMemory",
    description = "Remove a specific entry from MEMORY.md by matching text.",
) {
    override suspend fun execute(args: ForgetMemoryArgs): String {
        val memory = workspace.read("MEMORY.md") ?: return "Memory is empty."
        val lines = memory.lines().toMutableList()
        val removed = lines.removeAll { it.startsWith("- ") && it.contains(args.query, ignoreCase = true) }
        if (removed) {
            workspace.write("MEMORY.md", lines.joinToString("\n"))
            return "Removed from memory: ${args.query}"
        }
        return "No matching memory found for: ${args.query}"
    }
}

@Serializable
data class ListMemoryArgs(val filter: String = "") : ToolArgs

class ListMemoryTool(private val workspace: WorkspaceManager) : SimpleTool<ListMemoryArgs>(
    argsType = typeToken<ListMemoryArgs>(),
    name = "listMemory",
    description = "List all entries in MEMORY.md. Optionally filter by keyword.",
) {
    override suspend fun execute(args: ListMemoryArgs): String {
        val memory = workspace.read("MEMORY.md")
        if (memory.isNullOrBlank()) return "Memory is empty."
        if (args.filter.isBlank()) return memory
        return memory.lines().filter { it.contains(args.filter, ignoreCase = true) }.joinToString("\n")
    }
}

class UpdateUserTool(private val workspace: WorkspaceManager) : SimpleTool<UpdateUserArgs>(
    argsType = typeToken<UpdateUserArgs>(),
    name = "updateUser",
    description = "Update a section in USER.md with new information about the user. Sections: Profile, Preferences, Current Projects.",
) {
    override suspend fun execute(args: UpdateUserArgs): String {
        val user = workspace.read("USER.md") ?: "# User\n"
        val sectionHeader = "## ${args.section}"
        val updated = if (user.contains(sectionHeader)) {
            user.replace(sectionHeader, "$sectionHeader\n- ${args.content}")
        } else {
            "$user\n$sectionHeader\n- ${args.content}"
        }
        workspace.write("USER.md", updated)
        return "Updated USER.md [${args.section}]: ${args.content}"
    }
}

class AddDailyNoteTool(private val workspace: WorkspaceManager) : SimpleTool<AddDailyNoteArgs>(
    argsType = typeToken<AddDailyNoteArgs>(),
    name = "addDailyNote",
    description = "Add a note to today's daily log (memory/YYYY-MM-DD.md).",
) {
    override suspend fun execute(args: AddDailyNoteArgs): String {
        workspace.appendDailyNote(args.note)
        return "Added to today's notes."
    }
}

class SearchHistoryTool(private val workspace: WorkspaceManager) : SimpleTool<SearchHistoryArgs>(
    argsType = typeToken<SearchHistoryArgs>(),
    name = "searchHistory",
    description = "Search HISTORY.md for past events matching a query.",
) {
    override suspend fun execute(args: SearchHistoryArgs): String {
        val results = workspace.searchHistory(args.query)
        if (results.isEmpty()) return "No history entries found for: ${args.query}"
        return results.joinToString("\n\n").take(2000)
    }
}

class ReadWorkspaceFileTool(private val workspace: WorkspaceManager) : SimpleTool<ReadFileArgs>(
    argsType = typeToken<ReadFileArgs>(),
    name = "readWorkspaceFile",
    description = "Read any workspace file by name (e.g. SOUL.md, AGENTS.md, USER.md, HISTORY.md).",
) {
    override suspend fun execute(args: ReadFileArgs): String {
        return workspace.read(args.filename) ?: "File not found: ${args.filename}"
    }
}

class WriteWorkspaceFileTool(private val workspace: WorkspaceManager) : SimpleTool<WriteFileArgs>(
    argsType = typeToken<WriteFileArgs>(),
    name = "writeWorkspaceFile",
    description = "Write/replace content of a workspace file. Only SOUL.md, USER.md, AGENTS.md, TOOLS.md, MEMORY.md and memory/ files are writable.",
) {
    override suspend fun execute(args: WriteFileArgs): String {
        val allowed = listOf("SOUL.md", "USER.md", "AGENTS.md", "TOOLS.md", "MEMORY.md")
        if (args.filename !in allowed && !args.filename.startsWith("memory/")) {
            return "Cannot write to ${args.filename} — only workspace files are writable."
        }
        workspace.write(args.filename, args.content)
        return "Updated ${args.filename}"
    }
}
