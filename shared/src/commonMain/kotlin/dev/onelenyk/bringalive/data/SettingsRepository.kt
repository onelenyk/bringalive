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

    companion object {
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL = "llm_model"
    }
}
