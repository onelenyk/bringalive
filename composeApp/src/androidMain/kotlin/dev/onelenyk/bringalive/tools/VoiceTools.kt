package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.voice.VoiceEngine
import kotlinx.serialization.Serializable

@Serializable
data class SpeakArgs(val text: String) : ToolArgs

class SpeakTool(private val voiceEngine: VoiceEngine) : SimpleTool<SpeakArgs>(
    argsType = typeToken<SpeakArgs>(),
    name = "speak",
    description = "Speak text aloud to the user using TTS. Call this to voice a response, confirmation, or summary. Keep the text conversational — no markdown asterisks, no bullet syntax, no URLs. This function suspends until speech is complete or interrupted by the user.",
) {
    override suspend fun execute(args: SpeakArgs): String {
        voiceEngine.speak(args.text)
        return "spoken"
    }
}

fun createVoiceTools(voiceEngine: VoiceEngine): List<Tool<*, *>> = listOf(
    SpeakTool(voiceEngine),
)
