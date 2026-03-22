package dev.onelenyk.bringalive.voice

enum class VoiceState {
    IDLE,          // Not active
    LISTENING,     // Mic open, waiting for speech
    RECORDING,     // VAD detected speech, recording audio
    TRANSCRIBING,  // Sending audio to Groq STT
    THINKING,      // Waiting for agent response
    SPEAKING,      // Playing TTS audio
}
