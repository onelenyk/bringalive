package dev.onelenyk.bringalive.voice

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Groq STT — Whisper transcription.
 * Ported from aisist voice-engine.html.
 */
class GroqSttClient(private val httpClient: HttpClient) {

    suspend fun transcribe(
        audioBytes: ByteArray,
        apiKey: String,
        model: String = "whisper-large-v3-turbo",
        language: String = "", // "" = auto-detect, "uk" = Ukrainian, "en" = English
        filename: String = "audio.m4a",
        mimeType: String = "audio/mp4",
    ): SttResult {
        val response = httpClient.submitFormWithBinaryData(
            url = "https://api.groq.com/openai/v1/audio/transcriptions",
            formData = formData {
                append("file", audioBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
                append("model", model)
                append("response_format", "json")
                if (language.isNotBlank()) {
                    append("language", language)
                }
            }
        ) {
            header("Authorization", "Bearer $apiKey")
        }

        val body = response.bodyAsText()

        if (response.status.value !in 200..299) {
            return SttResult(text = "", error = "HTTP ${response.status.value}: $body")
        }

        val json = Json.parseToJsonElement(body) as? JsonObject
            ?: return SttResult(text = "", error = "Invalid JSON response")

        val text = json["text"]?.jsonPrimitive?.content?.trim() ?: ""
        return SttResult(text = text, error = null)
    }
}

data class SttResult(
    val text: String,
    val error: String?,
)

/**
 * Groq TTS — PlayAI text-to-speech.
 * Ported from aisist voice-engine.html.
 */
class GroqTtsClient(private val httpClient: HttpClient) {

    suspend fun synthesize(
        text: String,
        apiKey: String,
        model: String,
        voice: String = "troy",
        responseFormat: String = "wav",
    ): TtsResult {
        val payload = buildJsonObject {
            put("model", model)
            put("input", text.take(MAX_TTS_CHARS))
            put("voice", voice)
            put("response_format", responseFormat)
        }

        val response = httpClient.post("https://api.groq.com/openai/v1/audio/speech") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            return TtsResult(audioBytes = null, error = "HTTP ${response.status.value}: $body")
        }

        val bytes = response.bodyAsBytes()
        return TtsResult(audioBytes = bytes, error = null)
    }

    companion object {
        const val MAX_TTS_CHARS = 1000
    }
}

data class TtsResult(
    val audioBytes: ByteArray?,
    val error: String?,
)

/** Available Groq TTS models. */
enum class GroqTtsModel(val id: String, val displayName: String) {
    ORPHEUS_ENGLISH("canopylabs/orpheus-v1-english", "Orpheus English"),
    ORPHEUS_ARABIC("canopylabs/orpheus-arabic-saudi", "Orpheus Arabic (Saudi)"),
}

data class GroqTtsVoice(val id: String, val displayName: String, val model: GroqTtsModel)

/** Registry of all TTS voices grouped by model. */
object GroqTtsVoices {
    private val allVoices = listOf(
        GroqTtsVoice("troy", "Troy (male)", GroqTtsModel.ORPHEUS_ENGLISH),
        GroqTtsVoice("austin", "Austin (male)", GroqTtsModel.ORPHEUS_ENGLISH),
        GroqTtsVoice("daniel", "Daniel (male)", GroqTtsModel.ORPHEUS_ENGLISH),
        GroqTtsVoice("autumn", "Autumn (female)", GroqTtsModel.ORPHEUS_ENGLISH),
        GroqTtsVoice("diana", "Diana (female)", GroqTtsModel.ORPHEUS_ENGLISH),
        GroqTtsVoice("hannah", "Hannah (female)", GroqTtsModel.ORPHEUS_ENGLISH),
        GroqTtsVoice("fahad", "Fahad (male)", GroqTtsModel.ORPHEUS_ARABIC),
        GroqTtsVoice("sultan", "Sultan (male)", GroqTtsModel.ORPHEUS_ARABIC),
        GroqTtsVoice("lulwa", "Lulwa (female)", GroqTtsModel.ORPHEUS_ARABIC),
        GroqTtsVoice("noura", "Noura (female)", GroqTtsModel.ORPHEUS_ARABIC),
    )

    fun voicesForModel(model: GroqTtsModel): List<GroqTtsVoice> =
        allVoices.filter { it.model == model }

    fun defaultVoiceForModel(model: GroqTtsModel): GroqTtsVoice =
        voicesForModel(model).first()

    fun findVoice(voiceId: String): GroqTtsVoice? =
        allVoices.find { it.id == voiceId }

    fun modelById(modelId: String): GroqTtsModel? =
        GroqTtsModel.entries.find { it.id == modelId }
}

/** Available Groq STT models. */
enum class GroqSttModel(val id: String, val displayName: String) {
    WHISPER_V3_TURBO("whisper-large-v3-turbo", "Whisper v3 Turbo (fast)"),
    WHISPER_V3("whisper-large-v3", "Whisper v3 (accurate)"),
    DISTIL_WHISPER_EN("distil-whisper-large-v3-en", "Distil-Whisper EN (English only)"),
}
