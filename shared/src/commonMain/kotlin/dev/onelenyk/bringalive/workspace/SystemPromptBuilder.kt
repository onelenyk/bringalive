package dev.onelenyk.bringalive.workspace

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class SystemPromptBuilder(private val workspace: WorkspaceManager) {

    fun build(): String {
        val sections = mutableListOf<String>()

        // 0: Chain-of-thought protocol (hardcoded, always first)
        sections += """
            ## Chain-of-Thought Protocol
            Before executing any task that involves more than one tool call, you MUST call the `think` tool first.
            In `think`, provide:
            - goal: one sentence describing what you are trying to accomplish
            - steps: ordered list of the tool calls you plan to make
            - reasoning: optional notes (leave blank if obvious)
            Do NOT call `think` for simple single-step actions or conversational responses.
        """.trimIndent()

        // 1-5: Bootstrap files (AGENTS → SOUL → USER → TOOLS → MEMORY)
        for (file in workspace.contextFiles) {
            val content = workspace.read(file)
            if (!content.isNullOrBlank()) {
                val trimmed = content.take(MAX_FILE_CHARS)
                sections += "## $file\n\n$trimmed"
            }
        }

        // 6: Daily notes (today + yesterday)
        val todayNote = workspace.read(workspace.todayNotePath())
        if (!todayNote.isNullOrBlank()) {
            sections += "## Today's Notes\n\n${todayNote.take(MAX_FILE_CHARS)}"
        }

        val yesterdayNote = workspace.read(workspace.yesterdayNotePath())
        if (!yesterdayNote.isNullOrBlank()) {
            sections += "## Yesterday's Notes\n\n${yesterdayNote.take(MAX_FILE_CHARS)}"
        }

        // 6b: Voice guidance
        sections += """
            ## Voice
            You have a `speak(text)` tool. Use it to speak aloud when:
            - You want to verbally confirm completing an action (e.g. "Done, alarm set for 7 AM.")
            - The user explicitly asks you to read or say something
            - A short spoken response would enhance UX in a voice session
            In call mode your full response is already auto-spoken — do NOT call speak() additionally.
            Keep spoken text conversational: no markdown, no asterisks, no bullet syntax, no URLs.
        """.trimIndent()

        // 7: Runtime context
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val local = now.toLocalDateTime(tz)
        sections += "## Runtime\n- Date: ${local.date} ${local.time.hour}:${local.time.minute.toString().padStart(2, '0')}\n- Timezone: $tz\n- Platform: Android"

        // Enforce total cap
        val joined = sections.joinToString("\n\n---\n\n")
        return if (joined.length > MAX_TOTAL_CHARS) {
            joined.take(MAX_TOTAL_CHARS) + "\n\n[...truncated]"
        } else {
            joined
        }
    }

    companion object {
        private const val MAX_FILE_CHARS = 10_000
        private const val MAX_TOTAL_CHARS = 40_000
    }
}
