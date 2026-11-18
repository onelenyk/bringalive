package dev.onelenyk.bringalive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.onelenyk.bringalive.listener.ControllerState
import dev.onelenyk.bringalive.listener.ListenerState
import dev.onelenyk.bringalive.listener.PassiveListenerController
import dev.onelenyk.bringalive.listener.PassiveListenerEngine
import dev.onelenyk.bringalive.listener.TranscriptEntry
import dev.onelenyk.bringalive.listener.TranscriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PassiveListenerUiState(
    val listenerState: ListenerState = ListenerState.IDLE,
    val amplitude: Float = 0f,
    val transcripts: List<TranscriptEntry> = emptyList(),
    val controllerState: ControllerState = ControllerState.Idle,
    val lastNote: String? = null,
    val isActive: Boolean = false,
)

class PassiveListenerViewModel(
    val engine: PassiveListenerEngine,
    private val controller: PassiveListenerController,
    private val repository: TranscriptRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassiveListenerUiState())
    val uiState: StateFlow<PassiveListenerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                engine.state,
                engine.amplitude,
                repository.transcripts,
                controller.controllerState,
                controller.lastNote,
            ) { state, amplitude, transcripts, controllerState, lastNote ->
                PassiveListenerUiState(
                    listenerState = state,
                    amplitude = amplitude,
                    transcripts = transcripts,
                    controllerState = controllerState,
                    lastNote = lastNote,
                    isActive = state != ListenerState.IDLE,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleListening() {
        val state = _uiState.value
        if (state.isActive) {
            engine.stopListening()
            controller.stopTimer()
        } else {
            engine.startListening()
            controller.startTimer()
        }
    }

    fun processNow() {
        viewModelScope.launch {
            controller.processNow()
        }
    }

    fun updateInterval(minutes: Int) {
        controller.intervalMinutes = minutes
    }
}
