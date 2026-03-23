package dev.onelenyk.bringalive.presentation

import androidx.lifecycle.ViewModel
import dev.onelenyk.bringalive.agent.LlmProvider
import dev.onelenyk.bringalive.agent.availableModels
import dev.onelenyk.bringalive.data.SettingsRepository
import dev.onelenyk.bringalive.voice.GroqTtsModel
import dev.onelenyk.bringalive.voice.GroqTtsVoices
import dev.onelenyk.bringalive.voice.VoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val provider: String = "Anthropic (Claude)",
    val apiKey: String = "",
    val model: String = "",
    val availableModels: List<String> = emptyList(),
    // Voice
    val groqApiKey: String = "",
    val ttsModel: String = "canopylabs/orpheus-v1-english",
    val ttsVoice: String = "troy",
    val voiceLanguage: String = "",
    val availableTtsModels: List<Pair<String, String>> = emptyList(),
    val availableVoices: List<Pair<String, String>> = emptyList(),
    val inputLanguages: List<Pair<String, String>> = emptyList(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val voiceEngine: VoiceEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onProviderChanged(provider: String) {
        settingsRepository.provider = provider
        val models = modelsForProvider(provider)
        val firstModel = models.firstOrNull().orEmpty()
        settingsRepository.model = firstModel
        _state.update {
            it.copy(provider = provider, availableModels = models, model = firstModel)
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

    fun onGroqApiKeyChanged(key: String) {
        settingsRepository.groqApiKey = key
        _state.update { it.copy(groqApiKey = key) }
    }

    fun onTtsModelChanged(modelId: String) {
        settingsRepository.ttsModel = modelId
        val model = GroqTtsModel.entries.find { it.id == modelId } ?: GroqTtsModel.ORPHEUS_ENGLISH
        val voices = GroqTtsVoices.voicesForModel(model)
        val defaultVoice = voices.first().id
        settingsRepository.ttsVoice = defaultVoice
        _state.update {
            it.copy(
                ttsModel = modelId,
                ttsVoice = defaultVoice,
                availableVoices = voices.map { v -> v.id to v.displayName },
            )
        }
    }

    fun onTtsVoiceChanged(voice: String) {
        settingsRepository.ttsVoice = voice
        _state.update { it.copy(ttsVoice = voice) }
    }

    fun onVoiceLanguageChanged(lang: String) {
        settingsRepository.voiceLanguage = lang
        _state.update { it.copy(voiceLanguage = lang) }
    }

    fun onTestTts() {
        voiceEngine.testTts()
    }

    private fun buildState(): SettingsUiState {
        val provider = settingsRepository.provider
        val models = modelsForProvider(provider)
        val currentModel = settingsRepository.model.ifBlank { models.firstOrNull().orEmpty() }
        val ttsModelId = settingsRepository.ttsModel
        val ttsModel = GroqTtsModel.entries.find { it.id == ttsModelId } ?: GroqTtsModel.ORPHEUS_ENGLISH
        val voices = GroqTtsVoices.voicesForModel(ttsModel)
        return SettingsUiState(
            provider = provider,
            apiKey = settingsRepository.apiKey,
            model = currentModel,
            availableModels = models,
            groqApiKey = settingsRepository.groqApiKey,
            ttsModel = ttsModelId,
            ttsVoice = settingsRepository.ttsVoice,
            voiceLanguage = settingsRepository.voiceLanguage,
            availableTtsModels = OUTPUT_LANGUAGES,
            availableVoices = voices.map { it.id to it.displayName },
            inputLanguages = INPUT_LANGUAGES,
        )
    }

    companion object {
        /** Whisper-supported input languages */
        val INPUT_LANGUAGES = listOf(
            "" to "Auto-detect",
            "en" to "English",
            "uk" to "Ukrainian",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "pl" to "Polish",
            "ja" to "Japanese",
            "ko" to "Korean",
            "zh" to "Chinese",
            "ar" to "Arabic",
            "hi" to "Hindi",
            "tr" to "Turkish",
            "nl" to "Dutch",
            "ru" to "Russian",
            "sv" to "Swedish",
        )

        /** Output languages mapped to TTS model IDs */
        val OUTPUT_LANGUAGES = listOf(
            GroqTtsModel.ORPHEUS_ENGLISH.id to "English",
            GroqTtsModel.ORPHEUS_ARABIC.id to "Arabic (Saudi)",
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
