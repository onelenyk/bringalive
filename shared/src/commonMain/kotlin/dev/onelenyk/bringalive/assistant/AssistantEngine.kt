package dev.onelenyk.bringalive.assistant

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AssistantEngine {
    val state: StateFlow<AssistantState>
    val amplitude: StateFlow<Float>
    val commands: SharedFlow<String>

    fun start()
    fun stop()
    fun destroy()

    var onPermissionNeeded: (() -> Unit)?
    var onServiceStart: (() -> Unit)?
    var onServiceStop: (() -> Unit)?
}

enum class AssistantState {
    IDLE,
    LISTENING,
    RECORDING,
    TRANSCRIBING,
    ACTIVATED,
}
