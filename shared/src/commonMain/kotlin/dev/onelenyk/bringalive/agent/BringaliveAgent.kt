package dev.onelenyk.bringalive.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import dev.onelenyk.bringalive.conversation.ConversationManager
import dev.onelenyk.bringalive.conversation.JsonlChatHistoryProvider
import dev.onelenyk.bringalive.conversation.PersistedMessage
import dev.onelenyk.bringalive.data.AgentLogStore
import dev.onelenyk.bringalive.data.LogLevel
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.di.PlatformToolSets
import dev.onelenyk.bringalive.tools.*
import dev.onelenyk.bringalive.workspace.SystemPromptBuilder
import dev.onelenyk.bringalive.workspace.WorkspaceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BringaliveAgent(
    private val settingsRepository: SettingsRepository,
    private val workspace: WorkspaceManager,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val platformToolSets: PlatformToolSets,
    val logStore: AgentLogStore,
    private val conversationManager: ConversationManager,
) {
    private val _streamEvents = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    val streamEvents: SharedFlow<AgentEvent> = _streamEvents.asSharedFlow()

    /** Cached agent — recreated when provider/model changes. */
    private var cachedAgent: AIAgent<String, String>? = null
    private var cachedProviderKey: String? = null

    private fun getOrCreateAgent(): AIAgent<String, String> {
        val currentKey = "${settingsRepository.provider}|${settingsRepository.model}|${settingsRepository.apiKey}"
        if (cachedAgent != null && cachedProviderKey == currentKey) {
            return cachedAgent!!
        }

        val apiKey = settingsRepository.apiKey
        require(apiKey.isNotBlank()) { "API key is not configured. Go to Settings to add your key." }

        val provider = settingsRepository.resolvedProvider()
        val model = settingsRepository.resolvedModel()
        logStore.log("AGENT", "Creating agent: provider=${provider.displayName}, model=${model.id}")

        val executor = when (provider) {
            LlmProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
            LlmProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            LlmProvider.OPENROUTER -> simpleOpenRouterExecutor(apiKey)
        }

        val workspaceTools: List<Tool<*, *>> = listOf(
            GetCurrentTimeTool(),
            SaveMemoryTool(workspace),
            ForgetMemoryTool(workspace),
            ListMemoryTool(workspace),
            UpdateUserTool(workspace),
            AddDailyNoteTool(workspace),
            SearchHistoryTool(workspace),
            ReadWorkspaceFileTool(workspace),
            WriteWorkspaceFileTool(workspace),
        )

        val platformTools: List<Tool<*, *>> = platformToolSets.tools

        val toolRegistry = ToolRegistry {
            tools(workspaceTools)
            tools(platformTools)
        }

        val systemPrompt = systemPromptBuilder.build()

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry,
            maxIterations = 20,
        ) {
            // Install ChatMemory with our JSONL-backed provider
            install(ChatMemory.Feature) {
                chatHistoryProvider(JsonlChatHistoryProvider(conversationManager))
                windowSize(40) // keep last 40 messages in context
            }

            handleEvents {
                onLLMCallStarting {
                    logStore.startTimer("llm")
                    logStore.debug("LLM", "Calling ${model.id}...")
                }
                onLLMCallCompleted {
                    val ms = logStore.stopTimer("llm")
                    logStore.log(LogLevel.INFO, "LLM", "Response received", ms)
                }
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
                        else -> {}
                    }
                }
                onLLMStreamingFailed { ctx ->
                    logStore.error("LLM", "Streaming failed: ${ctx.error}")
                }
                onToolCallStarting { ctx ->
                    logStore.startTimer("tool:${ctx.toolCallId}")
                    logStore.info("TOOL", ">>> ${ctx.toolName}(${ctx.toolArgs})")
                    _streamEvents.emit(AgentEvent.ToolExecuting(ctx.toolName))
                }
                onToolCallCompleted { ctx ->
                    val ms = logStore.stopTimer("tool:${ctx.toolCallId}")
                    val result = ctx.toolResult.toString().take(500)
                    logStore.log(LogLevel.INFO, "TOOL", "<<< ${ctx.toolName} → $result", ms)
                    _streamEvents.emit(
                        AgentEvent.ToolCompleted(
                            name = ctx.toolName,
                            result = result.take(200),
                        )
                    )
                }
                onToolCallFailed { ctx ->
                    logStore.stopTimer("tool:${ctx.toolCallId}")
                    logStore.error("TOOL", "FAILED ${ctx.toolName}: ${ctx.message} | error=${ctx.error}")
                }
                onToolValidationFailed { ctx ->
                    logStore.error("TOOL", "VALIDATION FAILED ${ctx.toolName}: ${ctx.message} | args=${ctx.toolArgs}")
                }
                onAgentExecutionFailed { ctx ->
                    logStore.error("AGENT", "Execution failed: ${ctx.throwable.message ?: ctx.throwable}")
                }
                onNodeExecutionFailed { ctx ->
                    logStore.error("NODE", "Node failed: ${ctx.throwable.message ?: ctx.throwable}")
                }
            }
        }

        cachedAgent = agent
        cachedProviderKey = currentKey
        return agent
    }

    /** Invalidate cached agent (call when switching conversations). */
    fun resetAgent() {
        cachedAgent = null
        cachedProviderKey = null
    }

    suspend fun run(input: String): String {
        logStore.log("USER", input)
        _streamEvents.emit(AgentEvent.Started)

        // Persist user message
        conversationManager.appendToActive(
            PersistedMessage(role = "user", content = input)
        )

        return try {
            val agent = getOrCreateAgent()
            val result = agent.run(input)

            // Persist assistant response
            conversationManager.appendToActive(
                PersistedMessage(role = "assistant", content = result)
            )

            logStore.log("AGENT", "Response: ${result.take(200)}")
            _streamEvents.emit(AgentEvent.Completed(result))
            result
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            logStore.log("ERROR", errorMsg)
            _streamEvents.emit(AgentEvent.Error(errorMsg))
            "Error: $errorMsg"
        }
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
