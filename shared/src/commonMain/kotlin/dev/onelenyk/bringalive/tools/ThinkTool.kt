package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

@Serializable
data class ThinkArgs(
    val goal: String,
    val steps: List<String>,
    val reasoning: String = "",
) : ToolArgs

class ThinkTool : SimpleTool<ThinkArgs>(
    argsType = typeToken<ThinkArgs>(),
    name = "think",
    description = """
        Call this tool FIRST before any non-trivial or multi-step task.
        Provide: goal (one sentence describing the objective), steps (ordered list of the exact tool calls you will make), reasoning (optional).
        Skip for simple single-response questions that require no tools.
    """.trimIndent(),
) {
    override suspend fun execute(args: ThinkArgs): String =
        "Plan acknowledged: ${args.goal} (${args.steps.size} steps). Proceed."
}
