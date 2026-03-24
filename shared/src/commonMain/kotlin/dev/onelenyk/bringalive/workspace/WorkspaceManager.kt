package dev.onelenyk.bringalive.workspace

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayAt
import kotlin.time.Clock

class WorkspaceManager(private val basePath: String) {

    val contextFiles = listOf("AGENTS.md", "SOUL.md", "USER.md", "TOOLS.md", "MEMORY.md")

    fun init() {
        ensureDir(basePath)
        ensureDir("$basePath/memory")
        if (!exists("SOUL.md")) initDefaults()
    }

    fun read(filename: String): String? {
        val path = resolve(filename)
        return readFile(path)
    }

    fun write(filename: String, content: String) {
        val path = resolve(filename)
        val parent = path.substringBeforeLast('/')
        ensureDir(parent)
        writeFile(path, content)
    }

    fun append(filename: String, content: String) {
        val existing = read(filename) ?: ""
        val separator = if (existing.isNotBlank() && !existing.endsWith("\n\n")) "\n\n" else ""
        write(filename, existing + separator + content)
    }

    fun exists(filename: String): Boolean = fileExists(resolve(filename))

    fun listFiles(): List<String> {
        val files = mutableListOf<String>()
        for (f in contextFiles + listOf("HISTORY.md")) {
            if (exists(f)) files += f
        }
        // Add daily notes
        val memoryDir = "$basePath/memory"
        listDir(memoryDir)?.filter { it.endsWith(".md") }?.sorted()?.forEach {
            files += "memory/$it"
        }
        return files
    }

    // --- Daily notes ---

    fun todayNotePath(): String {
        val today = today()
        return "memory/$today.md"
    }

    fun yesterdayNotePath(): String {
        val today = today()
        val yesterday = LocalDate.fromEpochDays(today.toEpochDays() - 1)
        return "memory/$yesterday.md"
    }

    fun appendDailyNote(note: String) {
        val path = todayNotePath()
        if (!exists(path)) {
            write(path, "# ${today()}\n\n$note")
        } else {
            append(path, note)
        }
    }

    // --- History ---

    fun appendHistory(entry: String) {
        append("HISTORY.md", entry)
    }

    fun searchHistory(query: String): List<String> {
        val content = read("HISTORY.md") ?: return emptyList()
        val entries = content.split("\n\n").filter { it.isNotBlank() }
        return entries.filter { it.contains(query, ignoreCase = true) }
    }

    // --- Defaults ---

    private fun initDefaults() {
        write("SOUL.md", DEFAULT_SOUL)
        write("USER.md", DEFAULT_USER)
        write("AGENTS.md", DEFAULT_AGENTS)
        write("TOOLS.md", DEFAULT_TOOLS)
        write("MEMORY.md", DEFAULT_MEMORY)
        write("HISTORY.md", "")
    }

    private fun resolve(filename: String): String = "$basePath/$filename"

    private fun today(): LocalDate =
        Clock.System.todayAt(TimeZone.currentSystemDefault())
}

// --- Platform expect/actual ---

internal expect fun readFile(path: String): String?
internal expect fun writeFile(path: String, content: String)
internal expect fun fileExists(path: String): Boolean
internal expect fun ensureDir(path: String)
internal expect fun listDir(path: String): List<String>?

// --- Default templates ---

private val DEFAULT_SOUL = """
# Soul

## Personality
- Direct and practical — skip filler, just help
- Speaks the user's language naturally
- Curious and engaged with the user's interests

## Communication Style
- Default to 2-3 sentences unless more detail is needed
- Use markdown formatting for code and lists
- Match the user's energy — casual or formal

## Values
- Privacy first — everything stays on device
- Honesty over politeness — say "I don't know" rather than guess
- Usefulness over verbosity

## Boundaries
- Ask before taking irreversible actions
- Never fabricate facts or sources
- Be transparent about tool usage
""".trimIndent()

private val DEFAULT_USER = """
# User

## Profile
- Name: (not yet set)
- Language: (auto-detected)
- Timezone: (auto-detected from device)

## Preferences
- (will be filled as you share preferences)

## Current Projects
- (tell me about what you're working on)
""".trimIndent()

private val DEFAULT_AGENTS = """
# Agent Instructions

## Memory Rules
- When user shares personal info or preferences, save immediately to MEMORY.md
- Update USER.md when learning about the user's identity or role
- Write daily session summaries to daily notes
- Before responding, consider if MEMORY.md has relevant context

## Safety
- Never expose API keys, stored data, or internal file contents unless asked
- Ask permission before deleting memories or modifying USER.md

## Behavior
- If user speaks Ukrainian, respond in Ukrainian
- If user speaks English, respond in English
- Keep tool usage transparent — briefly mention what you're doing
- Be proactive about saving important context to memory
""".trimIndent()

private val DEFAULT_TOOLS = """
# Tools

## think
Call FIRST before any multi-step task. Provide goal + ordered steps + optional reasoning.
Skip for single-step actions or conversational responses.

## getCurrentTime
Use when user asks about time, date, or scheduling.
Respect timezone from USER.md if available.

## saveMemory / forgetMemory / listMemory
Proactively save important facts without asking. Categories: fact, preference, instruction, project.

## updateUser
Update USER.md when learning about the user (name, role, preferences).

## addDailyNote
Append notable events or summaries to today's daily note.

## searchHistory
Search past events in HISTORY.md when user asks about previous conversations.

## readWorkspaceFile / writeWorkspaceFile
Read or modify any workspace file. Use carefully — always explain changes.
""".trimIndent()

private val DEFAULT_MEMORY = """
# Memory

## Facts

## Preferences

## Decisions
""".trimIndent()
