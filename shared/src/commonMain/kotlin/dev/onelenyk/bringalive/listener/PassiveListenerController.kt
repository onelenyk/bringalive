package dev.onelenyk.bringalive.listener

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PassiveListenerController(
    private val transcriptRepository: TranscriptRepository,
    private val noteAgent: NoteAgent,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    var intervalMinutes: Int = 10

    private val _controllerState = MutableStateFlow<ControllerState>(ControllerState.Idle)
    val controllerState: StateFlow<ControllerState> = _controllerState.asStateFlow()

    private val _lastNote = MutableStateFlow<String?>(null)
    val lastNote: StateFlow<String?> = _lastNote.asStateFlow()

    fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(intervalMinutes * 60_000L)
                processNow()
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _controllerState.value = ControllerState.Idle
    }

    suspend fun processNow() {
        val unprocessed = transcriptRepository.getUnprocessed()
        if (unprocessed.isEmpty()) return

        _controllerState.value = ControllerState.Processing

        val result = noteAgent.process(unprocessed)

        if (result.processed) {
            transcriptRepository.markProcessed(unprocessed.map { it.id })
            _lastNote.value = result.summary
            _controllerState.value = ControllerState.LastRun(Clock.System.now().toEpochMilliseconds())
        } else {
            _controllerState.value = ControllerState.Idle
        }
    }

    fun destroy() {
        scope.cancel()
    }
}

sealed interface ControllerState {
    data object Idle : ControllerState
    data object Processing : ControllerState
    data class LastRun(val timestamp: Long) : ControllerState
}
