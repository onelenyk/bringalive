package dev.onelenyk.bringalive.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.onelenyk.bringalive.model.ToolStatus
import dev.onelenyk.bringalive.presentation.AgentAction

@Composable
fun AgentStatusPanel(
    isGenerating: Boolean,
    streamingText: String,
    currentAction: AgentAction?,
    activeTools: List<ToolStatus>,
    modifier: Modifier = Modifier,
) {
    val isVisible = isGenerating || currentAction != null || activeTools.any { it.status != "done" }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // Header row: status indicator + label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsatingDot(
                        color = if (currentAction?.status == "failed") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusLabel(isGenerating, currentAction, activeTools),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Current action detail
                if (currentAction != null) {
                    Spacer(Modifier.height(6.dp))
                    ActionRow(currentAction)
                }

                // Active tools list
                if (activeTools.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    for (tool in activeTools) {
                        ToolRow(tool)
                    }
                }

                // Progress bar when generating
                if (isGenerating && streamingText.isEmpty() && currentAction == null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: AgentAction) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Action badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (action.status) {
                    "done" -> Color(0xFF4CAF50)
                    "failed" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            ) {
                Text(
                    text = action.action,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (action.target.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = action.target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.weight(1f))

            // Status icon
            Text(
                text = when (action.status) {
                    "done" -> "done"
                    "failed" -> "fail"
                    else -> "..."
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (action.status) {
                    "done" -> Color(0xFF4CAF50)
                    "failed" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ToolRow(tool: ToolStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when (tool.status) {
                        "done" -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.primary
                    }
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = tool.name,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = tool.status,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = when (tool.status) {
                "done" -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PulsatingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

private fun statusLabel(
    isGenerating: Boolean,
    currentAction: AgentAction?,
    activeTools: List<ToolStatus>,
): String {
    if (currentAction != null) {
        return when (currentAction.status) {
            "failed" -> "Action failed"
            "done" -> "Action complete"
            else -> "Executing action..."
        }
    }
    val runningTools = activeTools.count { it.status != "done" }
    if (runningTools > 0) return "Running $runningTools tool${if (runningTools > 1) "s" else ""}..."
    if (isGenerating) return "Thinking..."
    return "Active"
}
