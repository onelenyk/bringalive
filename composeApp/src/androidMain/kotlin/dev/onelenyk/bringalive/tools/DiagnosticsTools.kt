package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.shizuku.ShizukuManager
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class DumpsysArgs(val service: String) : ToolArgs

@Serializable
data class ReadLogsArgs(
    val tag: String = "",
    val level: String = "E",
    val lines: Int = 50,
) : ToolArgs

@Serializable
data class GetPropArgs(val key: String = "") : ToolArgs

// --- Tools ---

class DumpsysTool(private val shizukuManager: ShizukuManager) : SimpleTool<DumpsysArgs>(
    argsType = typeToken<DumpsysArgs>(),
    name = "dumpsys",
    description = "Run dumpsys for a system service. Common values: battery, wifi, audio, notification, activity top, window, location, sensorservice, telephony.registry, diskstats, meminfo. Returns raw dumpsys output.",
) {
    override suspend fun execute(args: DumpsysArgs): String {
        val result = shizukuManager.executeCommand("dumpsys ${args.service}")
        return result.toDisplayString()
    }
}

class ReadLogsTool(private val shizukuManager: ShizukuManager) : SimpleTool<ReadLogsArgs>(
    argsType = typeToken<ReadLogsArgs>(),
    name = "readLogs",
    description = "Read device logcat. level: V/D/I/W/E (default E=errors only). tag: filter by tag (optional). lines: max lines to return (default 50).",
) {
    override suspend fun execute(args: ReadLogsArgs): String {
        val command = if (args.tag.isBlank()) {
            "logcat -d -t ${args.lines} *:${args.level}"
        } else {
            "logcat -d -t ${args.lines} -s ${args.tag}"
        }
        val result = shizukuManager.executeCommand(command)
        return result.toDisplayString()
    }
}

class GetPropTool(private val shizukuManager: ShizukuManager) : SimpleTool<GetPropArgs>(
    argsType = typeToken<GetPropArgs>(),
    name = "getprop",
    description = "Read Android system properties. Pass a specific key (e.g. ro.build.version.sdk, ro.product.model, ro.product.manufacturer) or leave blank to list all properties.",
) {
    override suspend fun execute(args: GetPropArgs): String {
        val command = if (args.key.isBlank()) "getprop" else "getprop ${args.key}"
        val result = shizukuManager.executeCommand(command)
        return result.toDisplayString()
    }
}

/** Returns all diagnostics tools as a list for ToolRegistry. */
fun createDiagnosticsTools(shizukuManager: ShizukuManager): List<Tool<*, *>> = listOf(
    DumpsysTool(shizukuManager),
    ReadLogsTool(shizukuManager),
    GetPropTool(shizukuManager),
)
