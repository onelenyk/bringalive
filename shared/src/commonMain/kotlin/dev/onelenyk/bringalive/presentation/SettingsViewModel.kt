package dev.onelenyk.bringalive.presentation

import androidx.lifecycle.ViewModel
import dev.onelenyk.bringalive.agent.LlmProvider
import dev.onelenyk.bringalive.agent.availableModels
import dev.onelenyk.bringalive.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val provider: String = "Anthropic (Claude)",
    val apiKey: String = "",
    val model: String = "",
    val availableModels: List<String> = emptyList(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onProviderChanged(provider: String) {
        settingsRepository.provider = provider
        val models = modelsForProvider(provider)
        val firstModel = models.firstOrNull().orEmpty()
        settingsRepository.model = firstModel
        _state.update {
            it.copy(
                provider = provider,
                availableModels = models,
                model = firstModel,
            )
        }
    }

    fun onApiKeyChanged(apiKey: String) {
        settingsRepository.apiKey = apiKey
        _state.update { it.copy(apiKey = apiKey) }
    }

    fun onModelChanged(model: String) {
        settingsRepository.model = model
        _state.update { it.copy(model = model) }
    }

    private fun buildState(): SettingsUiState {
        val provider = settingsRepository.provider
        val models = modelsForProvider(provider)
        val currentModel = settingsRepository.model.ifBlank { models.firstOrNull().orEmpty() }
        return SettingsUiState(
            provider = provider,
            apiKey = settingsRepository.apiKey,
            model = currentModel,
            availableModels = models,
        )
    }

    private fun modelsForProvider(provider: String): List<String> {
        val llmProvider = when {
            provider.contains("OpenRouter", ignoreCase = true) -> LlmProvider.OPENROUTER
            provider.contains("Anthropic", ignoreCase = true) -> LlmProvider.ANTHROPIC
            provider.contains("OpenAI", ignoreCase = true) -> LlmProvider.OPENAI
            else -> LlmProvider.ANTHROPIC
        }
        return llmProvider.availableModels().map { it.displayName }
    }
}
