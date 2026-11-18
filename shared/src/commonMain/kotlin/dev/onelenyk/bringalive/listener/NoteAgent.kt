package dev.onelenyk.bringalive.listener

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import dev.onelenyk.bringalive.agent.LlmProvider
import dev.onelenyk.bringalive.agent.availableModels
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.tools.AddDailyNoteTool
import dev.onelenyk.bringalive.tools.SaveMemoryTool
import dev.onelenyk.bringalive.workspace.WorkspaceManager
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private val NOTE_AGENT_SYSTEM_PROMPT = """
You are a passive meeting and conversation note-taker.
You receive raw speech transcripts with timestamps from ambient audio recordings.

Your tasks:
1. Write a brief summary of what was discussed
2. List action items (who will do what, if determinable)
3. Note key decisions made
4. Capture interesting ideas mentioned
5. Save all of this to the daily note using the addDailyNote tool
6. Save action items and decisions as memories using the saveMemory tool (category: "decision" or "project")

Rules:
- Only include information explicitly present in the transcript
- Be concise and use bullet points
- If the transcript is too short or unclear, still save a brief note about it
- Format the daily note entry with a timestamp header
""".trimIndent()

class NoteAgent(
    private val settings: SettingsRepository,
    private val workspace: WorkspaceManager,
) {
    suspend fun process(entries: List<TranscriptEntry>): NoteAgentResult {
        if (entries.isEmpty()) return NoteAgentResult(summary = "", processed = false)

        val apiKey = settings.apiKey
        if (apiKey.isBlank()) {
            return NoteAgentResult(summary = "Error: API key not configured", processed = false)
        }

        val transcript = formatTranscript(entries)

        val provider = resolvedProvider()
        val model = resolvedModel()

        val executor = when (provider) {
            LlmProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
            LlmProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            LlmProvider.OPENROUTER -> simpleOpenRouterExecutor(apiKey)
        }

        val toolRegistry = ToolRegistry {
            tools(listOf(
                AddDailyNoteTool(workspace),
                SaveMemoryTool(workspace),
            ))
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = NOTE_AGENT_SYSTEM_PROMPT,
            toolRegistry = toolRegistry,
            maxIterations = 10,
        )

        return try {
            val result = agent.run(transcript)
            NoteAgentResult(summary = result, processed = true)
        } catch (e: Exception) {
            NoteAgentResult(summary = "Error: ${e.message}", processed = false)
        }
    }

    private fun formatTranscript(entries: List<TranscriptEntry>): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val header = "## Transcript — ${now.date} ${now.hour}:${now.minute.toString().padStart(2, '0')}\n\n"
        val lines = entries.joinToString("\n") { entry ->
            val time = Instant.fromEpochMilliseconds(entry.timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val timeStr = "${time.hour}:${time.minute.toString().padStart(2, '0')}:${time.second.toString().padStart(2, '0')}"
            "[$timeStr] ${entry.text}"
        }
        return "Please analyze this transcript and save structured notes:\n\n$header$lines"
    }

    private fun resolvedProvider(): LlmProvider {
        return when {
            settings.provider.contains("OpenRouter", ignoreCase = true) -> LlmProvider.OPENROUTER
            settings.provider.contains("Anthropic", ignoreCase = true) -> LlmProvider.ANTHROPIC
            settings.provider.contains("OpenAI", ignoreCase = true) -> LlmProvider.OPENAI
            else -> LlmProvider.ANTHROPIC
        }
    }

    private fun resolvedModel(): ai.koog.prompt.llm.LLModel {
        val provider = resolvedProvider()
        val modelId = settings.model
        return provider.availableModels()
            .firstOrNull { it.model.id == modelId || it.displayName == modelId }
            ?.model
            ?: provider.availableModels().first().model
    }
}

data class NoteAgentResult(
    val summary: String,
    val processed: Boolean,
)
