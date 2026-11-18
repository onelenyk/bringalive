package dev.onelenyk.bringalive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.onelenyk.bringalive.assistant.AssistantEngine
import dev.onelenyk.bringalive.assistant.AssistantState
import dev.onelenyk.bringalive.data.AgentLogStore
import dev.onelenyk.bringalive.data.LogEntry
import dev.onelenyk.bringalive.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val amplitude: Float = 0f,
    val isActive: Boolean = false,
    val lastCommand: String? = null,
    val commandHistory: List<String> = emptyList(),
    val wakeWord: String = "hey note",
    val kwsThreshold: Float = 0.25f,
    val kwsScore: Float = 1.5f,
    val kwsTrailingBlanks: Int = 1,
    val logs: List<LogEntry> = emptyList(),
)

class AssistantViewModel(
    private val engine: AssistantEngine,
    private val settings: SettingsRepository,
    private val logStore: AgentLogStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState(
        wakeWord = settings.assistantWakeWord,
        kwsThreshold = settings.kwsThreshold,
        kwsScore = settings.kwsScore,
        kwsTrailingBlanks = settings.kwsTrailingBlanks,
    ))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(engine.state, engine.amplitude) { state, amplitude ->
                state to amplitude
            }.collect { (state, amplitude) ->
                _uiState.update {
                    it.copy(
                        assistantState = state,
                        amplitude = amplitude,
                        isActive = state != AssistantState.IDLE,
                    )
                }
            }
        }

        viewModelScope.launch {
            engine.commands.collect { command ->
                _uiState.update {
                    it.copy(
                        lastCommand = command,
                        commandHistory = (listOf(command) + it.commandHistory).take(50),
                    )
                }
            }
        }

        viewModelScope.launch {
            logStore.logs.collect { allLogs ->
                val assistantLogs = allLogs.filter { it.tag == "ASSISTANT" }.takeLast(30)
                _uiState.update { it.copy(logs = assistantLogs) }
            }
        }
    }

    fun toggleAssistant() {
        if (_uiState.value.isActive) {
            engine.stop()
        } else {
            engine.start()
        }
    }

    fun updateWakeWord(word: String) {
        settings.assistantWakeWord = word
        _uiState.update { it.copy(wakeWord = word) }
    }

    fun updateKwsThreshold(value: Float) {
        settings.kwsThreshold = value
        _uiState.update { it.copy(kwsThreshold = value) }
    }

    fun updateKwsScore(value: Float) {
        settings.kwsScore = value
        _uiState.update { it.copy(kwsScore = value) }
    }

    fun updateKwsTrailingBlanks(value: Int) {
        settings.kwsTrailingBlanks = value
        _uiState.update { it.copy(kwsTrailingBlanks = value) }
    }
}
