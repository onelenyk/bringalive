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

    var sttModel: String
        get() = settings.getString(KEY_STT_MODEL, "whisper-large-v3-turbo")
        set(value) = settings.putString(KEY_STT_MODEL, value)

    var autoSpeak: Boolean
        get() = settings.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) = settings.putBoolean(KEY_AUTO_SPEAK, value)

    companion object {
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_VOICE_LANG = "voice_language"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_STT_MODEL = "stt_model"
        private const val KEY_AUTO_SPEAK = "auto_speak"
    }
}
