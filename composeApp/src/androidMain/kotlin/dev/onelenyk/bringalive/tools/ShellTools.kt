package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.shizuku.ShizukuManager
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class ShellExecuteArgs(val command: String) : ToolArgs

@Serializable
data class ShizukuStatusArgs(val placeholder: String = "") : ToolArgs

// --- Tools ---

class ShellExecuteTool(private val shizukuManager: ShizukuManager) : SimpleTool<ShellExecuteArgs>(
    argsType = typeToken<ShellExecuteArgs>(),
    name = "shellExecute",
    description = "Execute a shell command with ADB-level privileges via Shizuku. Returns stdout, stderr, and exit code. Requires Shizuku to be running.",
) {
    override suspend fun execute(args: ShellExecuteArgs): String {
        val result = shizukuManager.executeCommand(args.command)
        return result.toDisplayString()
    }
}

class ShizukuStatusTool(private val shizukuManager: ShizukuManager) : SimpleTool<ShizukuStatusArgs>(
    argsType = typeToken<ShizukuStatusArgs>(),
    name = "shizukuStatus",
    description = "Check the current status of Shizuku (available, permission granted, version).",
) {
    override suspend fun execute(args: ShizukuStatusArgs): String {
        return shizukuManager.getStatusString()
    }
}

/** Returns all shell tools as a list for ToolRegistry. */
fun createShellTools(shizukuManager: ShizukuManager): List<Tool<*, *>> = listOf(
    ShellExecuteTool(shizukuManager),
    ShizukuStatusTool(shizukuManager),
)
