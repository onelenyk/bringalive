package dev.onelenyk.bringalive.model

import kotlinx.serialization.Serializable

@Serializable
data class ThinkingBlock(
    val goal: String,
    val steps: List<String>,
    val reasoning: String = "",
)
