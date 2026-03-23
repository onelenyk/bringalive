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
    val callMode: StateFlow<Boolean>
    val ttsEnabled: StateFlow<Boolean>

    fun startListening()
    fun stopListening()
    fun stopSpeaking()
    fun onAgentTextDelta(delta: String)
    fun onAgentResponseComplete()
    fun setThinking()
    fun toggleCallMode()  // enter/exit call mode
    fun toggleTts()       // enable/disable TTS for all responses
    fun testTts()         // speak a test sentence to verify TTS works
    fun destroy()
}
