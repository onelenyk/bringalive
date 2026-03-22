package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class GetCurrentTimeArgs(val timezone: String = "") : ToolArgs

class GetCurrentTimeTool : SimpleTool<GetCurrentTimeArgs>(
    argsType = typeToken<GetCurrentTimeArgs>(),
    name = "getCurrentTime",
    description = "Get the current date and time. Optionally specify an IANA timezone (e.g. 'Europe/Kyiv'). Defaults to device timezone.",
) {
    override suspend fun execute(args: GetCurrentTimeArgs): String {
        val tz = if (args.timezone.isBlank()) {
            TimeZone.currentSystemDefault()
        } else {
            try {
                TimeZone.of(args.timezone)
            } catch (_: Exception) {
                TimeZone.currentSystemDefault()
            }
        }
        val now = Clock.System.now()
        val local = now.toLocalDateTime(tz)
        return "Current time: ${local.date} ${local.time} ($tz)"
    }
}
