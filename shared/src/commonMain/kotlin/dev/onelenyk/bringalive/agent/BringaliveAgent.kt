package dev.onelenyk.bringalive.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.tools.AssistantTools
import dev.onelenyk.bringalive.tools.MemoryStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BringaliveAgent(
    private val settingsRepository: SettingsRepository,
    private val memoryStore: MemoryStore,
) {
    private val _streamEvents = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    val streamEvents: SharedFlow<AgentEvent> = _streamEvents.asSharedFlow()

    private fun createAgent(): AIAgent<String, String> {
        val apiKey = settingsRepository.apiKey
        require(apiKey.isNotBlank()) { "API key is not configured. Go to Settings to add your key." }

        val provider = settingsRepository.resolvedProvider()
        val model = settingsRepository.resolvedModel()

        val executor = when (provider) {
            LlmProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
            LlmProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            LlmProvider.OPENROUTER -> simpleOpenRouterExecutor(apiKey)
        }

        // ToolRegistryBuilder.tools(Object) accepts a ToolSet and registers its @Tool methods
        val toolRegistry = ToolRegistry.builder()
            .tools(AssistantTools(memoryStore) as Any)
            .build()

        val systemPrompt = buildSystemPrompt()

        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry,
            maxIterations = 20,
        ) {
            handleEvents {
                onLLMStreamingFrameReceived { ctx ->
                    when (val frame = ctx.streamFrame) {
                        is StreamFrame.TextDelta -> {
                            _streamEvents.emit(AgentEvent.TextDelta(frame.text))
                        }
                        is StreamFrame.ToolCallComplete -> {
                            _streamEvents.emit(AgentEvent.ToolCallStarted(frame.name))
                        }
                        is StreamFrame.End -> {
                            _streamEvents.emit(AgentEvent.StreamEnd)
                        }
                        else -> { /* TextComplete, ReasoningDelta, ToolCallDelta, etc. */ }
                    }
                }
                onToolCallStarting { ctx ->
                    _streamEvents.emit(
                        AgentEvent.ToolExecuting(ctx.toolName)
                    )
                }
                onToolCallCompleted { ctx ->
                    _streamEvents.emit(
                        AgentEvent.ToolCompleted(
                            name = ctx.toolName,
                            result = ctx.toolResult.toString().take(200),
                        )
                    )
                }
            }
        }
    }

    suspend fun run(input: String): String {
        _streamEvents.emit(AgentEvent.Started)
        return try {
            val agent = createAgent()
            val result = agent.run(input)
            _streamEvents.emit(AgentEvent.Completed(result))
            result
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            _streamEvents.emit(AgentEvent.Error(errorMsg))
            "Error: $errorMsg"
        }
    }

    private fun buildSystemPrompt(): String {
        val memorySection = memoryStore.asSystemPromptSection()
        return BASE_SYSTEM_PROMPT + memorySection
    }

    private fun SettingsRepository.resolvedProvider(): LlmProvider {
        return when {
            provider.contains("OpenRouter", ignoreCase = true) -> LlmProvider.OPENROUTER
            provider.contains("Anthropic", ignoreCase = true) -> LlmProvider.ANTHROPIC
            provider.contains("OpenAI", ignoreCase = true) -> LlmProvider.OPENAI
            else -> LlmProvider.ANTHROPIC
        }
    }

    private fun SettingsRepository.resolvedModel(): LLModel {
        val providerEnum = resolvedProvider()
        val modelId = model
        return providerEnum.availableModels()
            .firstOrNull { it.model.id == modelId || it.displayName == modelId }
            ?.model
            ?: providerEnum.availableModels().first().model
    }
}

sealed interface AgentEvent {
    data object Started : AgentEvent
    data class TextDelta(val text: String) : AgentEvent
    data class ToolCallStarted(val name: String) : AgentEvent
    data class ToolExecuting(val name: String) : AgentEvent
    data class ToolCompleted(val name: String, val result: String) : AgentEvent
    data object StreamEnd : AgentEvent
    data class Completed(val fullText: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

private val BASE_SYSTEM_PROMPT = """
You are bringalive, a personal AI assistant running on Android.
You are helpful, concise, and friendly. You speak the user's language.

You have tools available:
- getCurrentTime: get the current date and time
- saveMemory: save facts/preferences the user wants you to remember
- forgetMemory: delete a memory entry
- listMemory: show all saved memories

When the user shares personal info or preferences, use saveMemory to persist it.
When asked to recall or check memory, use listMemory.
Keep responses focused and practical.
""".trimIndent()
