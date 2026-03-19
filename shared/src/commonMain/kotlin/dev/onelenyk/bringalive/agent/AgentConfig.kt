package dev.onelenyk.bringalive.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel

enum class LlmProvider(val displayName: String) {
    ANTHROPIC("Anthropic (Claude)"),
    OPENAI("OpenAI"),
    OPENROUTER("OpenRouter"),
}

fun LlmProvider.availableModels(): List<ModelOption> = when (this) {
    LlmProvider.ANTHROPIC -> listOf(
        ModelOption("Claude Sonnet 4.5", AnthropicModels.Sonnet_4_5),
        ModelOption("Claude Sonnet 4", AnthropicModels.Sonnet_4),
        ModelOption("Claude Opus 4.1", AnthropicModels.Opus_4_1),
        ModelOption("Claude Haiku 4.5", AnthropicModels.Haiku_4_5),
    )
    LlmProvider.OPENAI -> listOf(
        ModelOption("GPT-4o", OpenAIModels.Chat.GPT4o),
        ModelOption("GPT-4o Mini", OpenAIModels.Chat.GPT4oMini),
        ModelOption("GPT-4.1", OpenAIModels.Chat.GPT4_1),
        ModelOption("O3 Mini", OpenAIModels.Chat.O3Mini),
    )
    LlmProvider.OPENROUTER -> listOf(
        ModelOption("Claude 4.5 Sonnet", OpenRouterModels.Claude4_5Sonnet),
        ModelOption("Claude 4.5 Opus", OpenRouterModels.Claude4_5Opus),
        ModelOption("Claude 4.1 Opus", OpenRouterModels.Claude4_1Opus),
        ModelOption("Claude 4 Sonnet", OpenRouterModels.Claude4Sonnet),
        ModelOption("GPT-5", OpenRouterModels.GPT5),
        ModelOption("GPT-5 Mini", OpenRouterModels.GPT5Mini),
        ModelOption("GPT-4o", OpenRouterModels.GPT4o),
        ModelOption("Gemini 2.5 Pro", OpenRouterModels.Gemini2_5Pro),
        ModelOption("Gemini 2.5 Flash", OpenRouterModels.Gemini2_5Flash),
        ModelOption("DeepSeek V3", OpenRouterModels.DeepSeekV30324),
        ModelOption("Llama 3", OpenRouterModels.Llama3Instruct),
        ModelOption("Qwen 2.5", OpenRouterModels.Qwen2_5),
        ModelOption("Mistral 7B", OpenRouterModels.Mistral7B),
    )
}

data class ModelOption(
    val displayName: String,
    val model: LLModel,
)
