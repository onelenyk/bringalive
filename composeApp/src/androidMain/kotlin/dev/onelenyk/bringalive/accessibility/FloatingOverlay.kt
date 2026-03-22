package dev.onelenyk.bringalive.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

data class OverlayStep(
    val index: Int,
    val action: String,
    val target: String,
    val status: String, // "running", "done", "failed"
    val durationMs: Long? = null,
)

data class OverlayState(
    val visible: Boolean = false,
    val title: String = "",
    val steps: List<OverlayStep> = emptyList(),
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
)

class FloatingOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var composeView: ComposeView? = null

    val state = mutableStateOf(OverlayState())

    private val lifecycleOwner = OverlayLifecycleOwner()

    fun show() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 80 // offset from top

        val frame = FrameLayout(context)
        val compose = ComposeView(context).apply {
            setContent {
                OverlayContent(state.value)
            }
        }

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        frame.setViewTreeLifecycleOwner(lifecycleOwner)
        frame.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        frame.addView(compose)
        windowManager.addView(frame, params)
        overlayView = frame
        composeView = compose
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
        }
        overlayView = null
        composeView = null
        state.value = OverlayState()
    }

    fun update(newState: OverlayState) {
        state.value = newState
        if (newState.visible && overlayView == null) show()
        if (!newState.visible && overlayView != null) hide()
        composeView?.setContent {
            OverlayContent(newState)
        }
    }

    fun startAction(title: String) {
        update(OverlayState(visible = true, title = title))
    }

    fun addStep(index: Int, action: String, target: String) {
        val current = state.value
        val step = OverlayStep(index, action, target, "running")
        update(current.copy(steps = current.steps + step))
    }

    fun completeStep(index: Int, durationMs: Long) {
        val current = state.value
        update(current.copy(
            steps = current.steps.map {
                if (it.index == index) it.copy(status = "done", durationMs = durationMs) else it
            }
        ))
    }

    fun failStep(index: Int) {
        val current = state.value
        update(current.copy(
            steps = current.steps.map {
                if (it.index == index) it.copy(status = "failed") else it
            },
            isFailed = true,
        ))
    }

    fun complete() {
        val current = state.value
        update(current.copy(isComplete = true))
    }
}

@Composable
private fun OverlayContent(state: OverlayState) {
    if (!state.visible) return

    val bgColor = when {
        state.isFailed -> Color(0xE6442222)
        state.isComplete -> Color(0xE6224422)
        else -> Color(0xE61A1A2E)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayPulseDot(
                    color = when {
                        state.isFailed -> Color(0xFFF44336)
                        state.isComplete -> Color(0xFF4CAF50)
                        else -> Color(0xFF4FC3F7)
                    },
                    animate = !state.isComplete && !state.isFailed,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.title.ifBlank { "bringalive" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (state.isComplete) {
                    Text("done", color = Color(0xFF4CAF50), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                } else if (state.isFailed) {
                    Text("failed", color = Color(0xFFF44336), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Steps
            if (state.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                for (step in state.steps) {
                    OverlayStepRow(step)
                }
            }
        }
    }
}

@Composable
private fun OverlayStepRow(step: OverlayStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        Text(
            text = when (step.status) {
                "done" -> ">"
                "failed" -> "x"
                else -> ">"
            },
            color = when (step.status) {
                "done" -> Color(0xFF4CAF50)
                "failed" -> Color(0xFFF44336)
                else -> Color(0xFF4FC3F7)
            },
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))

        // Action name
        Text(
            text = step.action,
            color = Color(0xFFBBBBBB),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        if (step.target.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "\"${step.target}\"",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        // Duration
        if (step.durationMs != null) {
            Text(
                text = "${step.durationMs}ms",
                color = Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun OverlayPulseDot(color: Color, animate: Boolean) {
    if (animate) {
        val transition = rememberInfiniteTransition()
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
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
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Minimal LifecycleOwner for overlay ComposeView. */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
