package dev.onelenyk.bringalive.listener

import kotlinx.coroutines.flow.StateFlow

interface PassiveListenerEngine {
    val state: StateFlow<ListenerState>
    val amplitude: StateFlow<Float>
    fun startListening()
    fun stopListening()
    var onPermissionNeeded: (() -> Unit)?
    var onServiceStart: (() -> Unit)?
    var onServiceStop: (() -> Unit)?
}

data class TranscriptEntry(
    val id: String,
    val text: String,
    val timestamp: Long,
    val processed: Boolean = false,
)

enum class ListenerState {
    IDLE,
    LISTENING,
    RECORDING,
    TRANSCRIBING,
}
