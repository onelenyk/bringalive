package dev.onelenyk.bringalive.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.onelenyk.bringalive.voice.VoiceState

@Composable
fun MicButton(
    voiceState: VoiceState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = voiceState != VoiceState.IDLE

    val backgroundColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
            VoiceState.LISTENING -> MaterialTheme.colorScheme.primaryContainer
            VoiceState.RECORDING -> MaterialTheme.colorScheme.error
            VoiceState.TRANSCRIBING -> MaterialTheme.colorScheme.primaryContainer
            VoiceState.THINKING -> MaterialTheme.colorScheme.primaryContainer
            VoiceState.SPEAKING -> MaterialTheme.colorScheme.tertiaryContainer
        },
        animationSpec = tween(300),
        label = "micBg",
    )

    val iconTint by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            VoiceState.RECORDING -> MaterialTheme.colorScheme.onError
            VoiceState.SPEAKING -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(300),
        label = "micIcon",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(48.dp)) {
        // Pulsating ring when recording
        if (voiceState == VoiceState.RECORDING) {
            PulseRing(color = MaterialTheme.colorScheme.error)
        }
        if (voiceState == VoiceState.LISTENING) {
            PulseRing(color = MaterialTheme.colorScheme.primary)
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(backgroundColor),
        ) {
            AnimatedContent(
                targetState = voiceState,
                transitionSpec = {
                    scaleIn(tween(150)) togetherWith scaleOut(tween(150))
                },
                label = "micIconAnim",
            ) { state ->
                Icon(
                    imageVector = when (state) {
                        VoiceState.IDLE -> Icons.Default.Mic
                        VoiceState.LISTENING -> Icons.Default.Mic
                        VoiceState.RECORDING -> Icons.Default.MicOff
                        VoiceState.TRANSCRIBING -> Icons.Default.Mic
                        VoiceState.THINKING -> Icons.Default.Mic
                        VoiceState.SPEAKING -> Icons.Default.VolumeUp
                    },
                    contentDescription = when (state) {
                        VoiceState.IDLE -> "Start voice"
                        VoiceState.RECORDING -> "Stop recording"
                        VoiceState.SPEAKING -> "Stop speaking"
                        else -> "Voice active"
                    },
                    tint = iconTint,
                )
            }
        }
    }
}

@Composable
private fun PulseRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )
    Canvas(modifier = Modifier.size(48.dp * scale).alpha(alpha)) {
        drawCircle(color = color, radius = size.minDimension / 2)
    }
}

@Composable
fun VoiceWaveform(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 16,
) {
    val animatedAmps = (0 until barCount).map { i ->
        // Create variation per bar using index offset
        val phase = i.toFloat() / barCount
        val raw = if (isActive) {
            (amplitude * (0.3f + 0.7f * ((phase * 7 + amplitude * 3) % 1f))).coerceIn(0.05f, 1f)
        } else {
            0.05f
        }
        val animated by animateFloatAsState(
            targetValue = raw,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "bar$i",
        )
        animated
    }

    Canvas(
        modifier = modifier.height(28.dp),
    ) {
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * spacing
        var x = (size.width - totalWidth) / 2
        val centerY = size.height / 2
        val maxH = size.height

        for (i in 0 until barCount) {
            val amp = animatedAmps[i]
            val h = (4.dp.toPx() + amp * (maxH - 4.dp.toPx())).coerceAtLeast(4.dp.toPx())

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - h / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
            x += barWidth + spacing
        }
    }
}

@Composable
fun SpeakingBars(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
) {
    val transition = rememberInfiniteTransition(label = "speaking")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { i ->
            val scale by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(350, delayMillis = i * 100, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp * scale.coerceAtLeast(0.2f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}
