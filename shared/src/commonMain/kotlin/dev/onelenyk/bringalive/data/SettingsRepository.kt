package dev.onelenyk.bringalive.data

import com.russhwolf.settings.Settings

class SettingsRepository(private val settings: Settings) {

    var provider: String
        get() = settings.getString(KEY_PROVIDER, "Anthropic (Claude)")
        set(value) = settings.putString(KEY_PROVIDER, value)

    var apiKey: String
        get() = settings.getString(KEY_API_KEY, "")
        set(value) = settings.putString(KEY_API_KEY, value)

    var model: String
        get() = settings.getString(KEY_MODEL, "claude-sonnet-4-20250514")
        set(value) = settings.putString(KEY_MODEL, value)

    // --- Voice settings ---

    var groqApiKey: String
        get() = settings.getString(KEY_GROQ_API_KEY, "")
        set(value) = settings.putString(KEY_GROQ_API_KEY, value)

    var voiceLanguage: String
        get() = settings.getString(KEY_VOICE_LANG, "")
        set(value) = settings.putString(KEY_VOICE_LANG, value)

    var ttsVoice: String
        get() = settings.getString(KEY_TTS_VOICE, "troy")
        set(value) = settings.putString(KEY_TTS_VOICE, value)

    var ttsModel: String
        get() = settings.getString(KEY_TTS_MODEL, "canopylabs/orpheus-v1-english")
        set(value) = settings.putString(KEY_TTS_MODEL, value)

    var sttModel: String
        get() = settings.getString(KEY_STT_MODEL, "whisper-large-v3-turbo")
        set(value) = settings.putString(KEY_STT_MODEL, value)

    var autoSpeak: Boolean
        get() = settings.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) = settings.putBoolean(KEY_AUTO_SPEAK, value)

    // --- Listener settings ---

    var listenerIntervalMinutes: Int
        get() = settings.getInt(KEY_LISTENER_INTERVAL, 10)
        set(value) = settings.putInt(KEY_LISTENER_INTERVAL, value)

    // --- Assistant settings ---

    var assistantWakeWord: String
        get() = settings.getString(KEY_ASSISTANT_WAKE_WORD, "hey note")
        set(value) = settings.putString(KEY_ASSISTANT_WAKE_WORD, value)

    var kwsThreshold: Float
        get() = settings.getFloat(KEY_KWS_THRESHOLD, 0.25f)
        set(value) = settings.putFloat(KEY_KWS_THRESHOLD, value)

    var kwsScore: Float
        get() = settings.getFloat(KEY_KWS_SCORE, 1.5f)
        set(value) = settings.putFloat(KEY_KWS_SCORE, value)

    var kwsTrailingBlanks: Int
        get() = settings.getInt(KEY_KWS_TRAILING_BLANKS, 1)
        set(value) = settings.putInt(KEY_KWS_TRAILING_BLANKS, value)

    companion object {
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_VOICE_LANG = "voice_language"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_STT_MODEL = "stt_model"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_LISTENER_INTERVAL = "listener_interval_minutes"
        private const val KEY_ASSISTANT_WAKE_WORD = "assistant_wake_word"
        private const val KEY_KWS_THRESHOLD = "kws_threshold"
        private const val KEY_KWS_SCORE = "kws_score"
        private const val KEY_KWS_TRAILING_BLANKS = "kws_trailing_blanks"
    }
}
