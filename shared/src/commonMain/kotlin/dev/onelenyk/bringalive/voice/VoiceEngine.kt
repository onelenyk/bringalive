package dev.onelenyk.bringalive.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common voice engine interface. Android implementation: AndroidVoiceEngine.
 */
interface VoiceEngine {
    val state: StateFlow<VoiceState>
    val amplitude: StateFlow<Float>
    val transcribedText: SharedFlow<String>
    val error: SharedFlow<String>

    fun startListening()
    fun stopListening()
    fun stopSpeaking()
    fun onAgentTextDelta(delta: String)
    fun onAgentResponseComplete()
    fun setThinking()
    fun destroy()
}
