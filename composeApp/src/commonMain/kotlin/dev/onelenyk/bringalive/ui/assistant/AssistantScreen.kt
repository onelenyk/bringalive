package dev.onelenyk.bringalive.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.onelenyk.bringalive.assistant.AssistantState
import dev.onelenyk.bringalive.data.LogEntry
import dev.onelenyk.bringalive.data.LogLevel
import dev.onelenyk.bringalive.presentation.AssistantViewModel
import org.koin.compose.viewmodel.koinViewModel

class AssistantScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinViewModel<AssistantViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Assistant") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
            ) {
                // Status row
                StatusRow(
                    state = uiState.assistantState,
                    amplitude = uiState.amplitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                HorizontalDivider()

                // Wake word config (only when idle)
                if (!uiState.isActive) {
                    OutlinedTextField(
                        value = uiState.wakeWord,
                        onValueChange = { viewModel.updateWakeWord(it) },
                        label = { Text("Wake word") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    // KWS tuning sliders
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Threshold: ${"%.2f".format(uiState.kwsThreshold)} (lower = easier trigger)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = uiState.kwsThreshold,
                            onValueChange = { viewModel.updateKwsThreshold(it) },
                            valueRange = 0.05f..0.9f,
                        )

                        Text(
                            text = "Score: ${"%.1f".format(uiState.kwsScore)} (higher = keywords favored more)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = uiState.kwsScore,
                            onValueChange = { viewModel.updateKwsScore(it) },
                            valueRange = 0.5f..5.0f,
                        )

                        Text(
                            text = "Trailing blanks: ${uiState.kwsTrailingBlanks} (higher = more conservative)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = uiState.kwsTrailingBlanks.toFloat(),
                            onValueChange = { viewModel.updateKwsTrailingBlanks(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                    }

                    HorizontalDivider()
                }

                // Last command
                uiState.lastCommand?.let { cmd ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Last command",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cmd,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                // Command history
                if (uiState.commandHistory.size > 1) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(uiState.commandHistory.drop(1)) { cmd ->
                            Text(
                                text = cmd,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    // Empty state message
                    if (!uiState.isActive && uiState.logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Tap Start to begin listening",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Live log feed
                if (uiState.logs.isNotEmpty()) {
                    Text(
                        text = "Log",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    val logListState = rememberLazyListState()

                    LaunchedEffect(uiState.logs.size) {
                        if (uiState.logs.isNotEmpty()) {
                            logListState.animateScrollToItem(uiState.logs.size - 1)
                        }
                    }

                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 4.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        items(uiState.logs) { entry ->
                            LogItem(entry)
                        }
                    }
                }

                HorizontalDivider()

                // Start/Stop button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = { viewModel.toggleAssistant() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (uiState.isActive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Icon(
                            if (uiState.isActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isActive) "Stop" else "Start")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    state: AssistantState,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val dotColor = when (state) {
            AssistantState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            AssistantState.LISTENING -> MaterialTheme.colorScheme.primary
            AssistantState.RECORDING -> MaterialTheme.colorScheme.error
            AssistantState.TRANSCRIBING -> MaterialTheme.colorScheme.tertiary
            AssistantState.ACTIVATED -> MaterialTheme.colorScheme.secondary
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )

        Text(
            text = when (state) {
                AssistantState.IDLE -> "Idle"
                AssistantState.LISTENING -> "Listening for wake word"
                AssistantState.RECORDING -> "Recording"
                AssistantState.TRANSCRIBING -> "Transcribing"
                AssistantState.ACTIVATED -> "Activated — speak now"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (state != AssistantState.IDLE) {
            LinearProgressIndicator(
                progress = { amplitude },
                modifier = Modifier
                    .width(80.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when (state) {
                    AssistantState.RECORDING -> MaterialTheme.colorScheme.error
                    AssistantState.ACTIVATED -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun LogItem(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Text(
        text = "${entry.timestamp} ${entry.message}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        maxLines = 1,
    )
}
